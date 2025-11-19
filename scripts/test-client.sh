#!/bin/bash
# ---------------------------------------------------------
# Simple client wrapper for interacting with cluster
# ---------------------------------------------------------

JAR_FILE="target/raft-cache-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
  echo "❌ Build not found! Run: mvn clean package -DskipTests"
  exit 1
fi

java -cp $JAR_FILE com.distributed.cache.raft.client.CacheClient "$@"
