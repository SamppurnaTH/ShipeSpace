#!/bin/bash
set -e

echo "=== Starting MongoDB ==="
mkdir -p /tmp/mongodb-data /tmp/mongodb-log
if ! pgrep -x mongod > /dev/null; then
  mongod --dbpath /tmp/mongodb-data \
         --logpath /tmp/mongodb-log/mongod.log \
         --port 27017 \
         --bind_ip 127.0.0.1 \
         --fork
  echo "MongoDB started"
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

echo "=== Building and starting Spring Boot ==="
exec mvn spring-boot:run -Dmaven.test.skip=true -Dspring-boot.run.jvmArguments="-Xms256m -Xmx512m"
