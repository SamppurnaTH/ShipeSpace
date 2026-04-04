# Backup & Recovery Strategy

## MongoDB

### Backup Schedule
| Type | Frequency | Retention | Tool |
|------|-----------|-----------|------|
| Continuous oplog | Real-time | 24h | Atlas / mongodump |
| Daily snapshot | 02:30 UTC (after decay job) | 30 days | Atlas snapshots / mongodump |
| Weekly snapshot | Sunday 03:00 UTC | 90 days | Atlas snapshots |
| Monthly snapshot | 1st of month | 1 year | Atlas snapshots |

### Atlas (Recommended)
Atlas handles backups automatically. Enable:
- Continuous Cloud Backup: ON
- Point-in-time restore: 24h window
- Snapshot schedule: daily + weekly

### Self-Hosted Backup Script
```bash
#!/bin/bash
# Run via cron: 30 2 * * * /opt/shipspace/backup-mongo.sh
set -euo pipefail

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backups/mongodb/${TIMESTAMP}"
S3_BUCKET="s3://shipspace-backups/mongodb"

# Dump from secondary to avoid impacting primary
mongodump \
  --uri="${MONGODB_URI}" \
  --readPreference=secondary \
  --gzip \
  --out="${BACKUP_DIR}"

# Upload to S3 with server-side encryption
aws s3 sync "${BACKUP_DIR}" "${S3_BUCKET}/${TIMESTAMP}/" \
  --sse aws:kms \
  --storage-class STANDARD_IA

# Verify upload
aws s3 ls "${S3_BUCKET}/${TIMESTAMP}/" | grep -q "shipspace" || exit 1

# Clean up local backup (S3 is the source of truth)
rm -rf "${BACKUP_DIR}"

echo "Backup completed: ${S3_BUCKET}/${TIMESTAMP}/"
```

### Restore Process
```bash
# 1. Identify the backup to restore
aws s3 ls s3://shipspace-backups/mongodb/ --recursive | sort | tail -20

# 2. Download backup
aws s3 sync s3://shipspace-backups/mongodb/20260403_023000/ /tmp/restore/

# 3. Stop application (prevent writes during restore)
# kubectl scale deployment shipspace-backend --replicas=0

# 4. Restore
mongorestore \
  --uri="${MONGODB_URI}" \
  --gzip \
  --drop \
  /tmp/restore/

# 5. Verify data integrity
mongosh "${MONGODB_URI}" --eval "
  db.users.countDocuments(),
  db.ships.countDocuments(),
  db.projects.countDocuments()
"

# 6. Restart application
# kubectl scale deployment shipspace-backend --replicas=2
```

### Data Consistency Checks (run after restore)
```javascript
// Run in mongosh after restore
// Check for orphaned ships (no parent project)
db.ships.aggregate([
  { $lookup: { from: "projects", localField: "projectId", foreignField: "_id", as: "project" } },
  { $match: { project: { $size: 0 } } },
  { $count: "orphaned_ships" }
])

// Check for negative counters
db.ships.find({ $or: [{ likeCount: { $lt: 0 } }, { commentCount: { $lt: 0 } }] }).count()

// Check for users with invalid levels
db.users.find({ $or: [{ level: { $lt: 1 } }, { level: { $gt: 30 } }] }).count()

// Check score event log for duplicate entries (should be 0 — unique index)
db.score_event_logs.aggregate([
  { $group: { _id: { userId: "$userId", eventType: "$eventType", entityId: "$entityId" }, count: { $sum: 1 } } },
  { $match: { count: { $gt: 1 } } },
  { $count: "duplicates" }
])
```

---

## Redis

### What to back up
Redis is a cache + ephemeral state store. Most data is reconstructable:
- Rate limit counters: expire naturally, no backup needed
- Token blacklist: 24h TTL max, lose on restart = minor security window
- Distributed locks: expire via TTL, no backup needed
- Cache entries: reconstructed from MongoDB on miss

### RDB Snapshot (already configured in docker-compose)
```
redis-server --save 60 1  # Save every 60s if ≥1 key changed
```

### For production Redis Sentinel/Cluster
- Enable AOF (Append-Only File) for durability: `appendonly yes`
- AOF fsync policy: `appendfsync everysec` (1s data loss max)
- Backup RDB to S3 daily via cron

### Redis Recovery
If Redis is lost entirely:
1. Start fresh Redis instance — all caches will be cold
2. Rate limits reset to zero — acceptable (brief window of no limiting)
3. Token blacklist lost — revoked tokens valid until natural expiry (max 24h for access tokens)
4. Distributed locks reset — scheduled jobs will run on next cron trigger

**Mitigation for token blacklist loss:** Reduce access token TTL to 15 minutes
(`JWT_EXPIRATION=900000`). Maximum replay window after Redis loss = 15 minutes.

---

## Backup Verification (Monthly)
```bash
# Restore to a test environment and run smoke tests
# This must be done monthly — untested backups are not backups

# 1. Spin up test MongoDB
docker run -d --name mongo-restore-test -p 27018:27017 mongo:7

# 2. Restore latest backup
mongorestore --uri="mongodb://localhost:27018" --gzip /tmp/latest-backup/

# 3. Run consistency checks (see above)

# 4. Start application against test DB and run integration tests
MONGODB_URI=mongodb://localhost:27018/shipspace mvn test -Dtest="*IntegrationTest"

# 5. Tear down
docker rm -f mongo-restore-test
```
