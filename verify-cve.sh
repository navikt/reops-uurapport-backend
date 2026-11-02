#!/usr/bin/env bash
set -euo pipefail
IMAGE="reops-uurapport-backend"

echo "== mvn package =="
mvn clean package -DskipTests --batch-mode --settings ./.mvn/settings.xml

echo "== docker build =="
docker build --pull -t "$IMAGE" .

echo "== grype scan =="
grype "$IMAGE" -o table
