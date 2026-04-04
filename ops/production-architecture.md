# Production Deployment Architecture

## Network Flow

```
Internet
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  Cloudflare (DDoS protection, CDN, WAF)                     │
│  - Rate limiting at edge (1000 req/min per IP)              │
│  - Bot detection                                            │
│  - TLS termination                                          │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  Load Balancer (AWS ALB / GCP Load Balancer)                │
│  - Health check: GET /api/actuator/health/readiness         │
│  - Sticky sessions: OFF (stateless JWT)                     │
│  - Connection draining: 30s                                 │
└─────────────────────────────────────────────────────────────┘
    │           │
    ▼           ▼
┌────────┐  ┌────────┐   ← 2 instances minimum, 4 at peak
│Backend │  │Backend │     Each: 2 vCPU, 4GB RAM
│  :8080 │  │  :8080 │     JVM: -Xmx3g -XX:MaxRAMPercentage=75
└────────┘  └────────┘
    │           │
    ├───────────┤
    │           │
    ▼           ▼
┌─────────────────────────────────────────────────────────────┐
│  MongoDB Atlas M30 (or self-hosted 3-node PSS replica set)  │
│                                                             │
│  Primary ──── Secondary ──── Secondary                      │
│  (writes)     (reads)        (reads + hidden for backups)   │
│                                                             │
│  Connection string:                                         │
│  mongodb+srv://...?replicaSet=rs0&readPreference=           │
│  secondaryPreferred&maxPoolSize=50&minPoolSize=10           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  Redis Sentinel (3 nodes) or Redis Cluster                  │
│                                                             │
│  Master ──── Replica ──── Replica                           │
│  (writes)    (reads)      (reads)                           │
│                                                             │
│  Sentinel monitors master, auto-promotes replica on failure │
│  Failover time: ~10-30s                                     │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  Observability Stack (separate namespace/VPC)               │
│                                                             │
│  Prometheus ──── Grafana (dashboards)                       │
│  Zipkin/Jaeger (distributed traces)                         │
│  ELK / CloudWatch (log aggregation)                         │
└─────────────────────────────────────────────────────────────┘
```

## Instance Sizing

| Component | Min | Peak | Spec |
|-----------|-----|------|------|
| Backend | 2 | 4 | 2 vCPU, 4GB RAM |
| MongoDB Primary | 1 | 1 | 4 vCPU, 16GB RAM, 100GB SSD |
| MongoDB Secondary | 2 | 2 | 4 vCPU, 16GB RAM, 100GB SSD |
| Redis Master | 1 | 1 | 2 vCPU, 4GB RAM |
| Redis Replica | 2 | 2 | 2 vCPU, 4GB RAM |

## Scaling Strategy

**Horizontal scaling (backend):**
- Scale out at: CPU > 70% for 3 minutes OR p95 latency > 500ms
- Scale in at: CPU < 30% for 10 minutes
- Max instances: 8 (MongoDB pool limit: 8 × 50 = 400 connections, within M30 limit of 500)
- Use: Kubernetes HPA or ECS auto-scaling

**MongoDB scaling:**
- M30 handles ~50K DAU comfortably
- Upgrade to M50 at ~100K DAU (more RAM = larger working set in memory)
- Add read replicas before upgrading tier

**Redis scaling:**
- Single master handles ~100K ops/sec — sufficient for 200K DAU
- Move to Redis Cluster when memory > 80% sustained

## Kubernetes Deployment (abbreviated)

```yaml
# k8s/production/deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: shipspace-backend
  namespace: production
spec:
  replicas: 2
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0  # Zero-downtime rolling update
  selector:
    matchLabels:
      app: shipspace-backend
  template:
    spec:
      containers:
        - name: backend
          image: ghcr.io/your-org/shipspace:sha-abc1234
          ports:
            - containerPort: 8080
          resources:
            requests:
              cpu: "1"
              memory: "2Gi"
            limits:
              cpu: "2"
              memory: "4Gi"
          readinessProbe:
            httpGet:
              path: /api/actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /api/actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 30
            failureThreshold: 3
          env:
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: shipspace-secrets
                  key: jwt-secret
            # ... other env vars from Kubernetes secrets
          lifecycle:
            preStop:
              exec:
                # Give in-flight requests 30s to complete before shutdown
                command: ["/bin/sh", "-c", "sleep 10"]
      terminationGracePeriodSeconds: 60
```
