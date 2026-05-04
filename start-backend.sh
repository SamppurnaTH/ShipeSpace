#!/bin/bash
set -e

echo "=== Starting MongoDB ==="
mkdir -p /tmp/mongodb-data /tmp/mongodb-log
if ! pgrep -x mongod > /dev/null; then
  mongod --dbpath /tmp/mongodb-data \
         --logpath /tmp/mongodb-log/mongod.log \
         --port 27017 \
         --bind_ip 127.0.0.1 \
         --replSet rs0 \
         --fork
  echo "MongoDB started"
  # Initialise replica set if not already done
  sleep 2
  mongosh --quiet --eval "
    try { rs.status().ok } catch(e) {
      rs.initiate({_id:'rs0',members:[{_id:0,host:'127.0.0.1:27017'}]});
    }
  " || true
else
  echo "MongoDB already running"
fi

echo "=== Starting Redis ==="
if ! pgrep -x redis-server > /dev/null; then
  redis-server --daemonize yes \
               --bind 127.0.0.1 \
               --port 6379 \
               --loglevel warning
  echo "Redis started"
else
  echo "Redis already running"
fi

echo "=== Waiting for services ==="
sleep 2

echo "=== Building production JAR ==="
mvn -B package -DskipTests -q

echo "=== Starting ShipSpace (production) ==="
exec java \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom \
  -jar target/shipspace-mvp-1.0.0.jar
