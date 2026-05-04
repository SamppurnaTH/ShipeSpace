#!/bin/bash
set -e
cd web
echo "=== Installing dependencies ==="
npm install --legacy-peer-deps --silent

echo "=== Building frontend for production ==="
npm run build

echo "=== Serving production build ==="
exec npm run preview -- --host 0.0.0.0 --port 5000
