#!/bin/bash

# Test script for Read Lease Optimization (Phase 1)
# Tests STRONG, LEASE, and EVENTUAL consistency levels

set -e

LEADER_URL="http://localhost:8081"
KEY="test-key-lease"
VALUE="test-value-123"

echo "=========================================="
echo "Phase 1: Read Lease Optimization Test"
echo "=========================================="
echo ""

# Step 1: Write a key-value pair to the cache
echo "1. Writing key='$KEY' value='$VALUE' to leader..."
curl -s -X POST "$LEADER_URL/cache/$KEY" \
  -H "Content-Type: application/json" \
  -d "{\"clientId\": \"test-client\", \"sequenceNumber\": 1, \"value\": \"$VALUE\"}" | jq .

echo ""
echo "Waiting 2 seconds for data to replicate..."
sleep 2
echo ""

# Step 2: Test STRONG consistency (ReadIndex protocol)
echo "2. Testing STRONG consistency (ReadIndex protocol)..."
echo "   Expected: ~50-100ms latency (confirms leadership via heartbeat)"
for i in {1..5}; do
  response=$(curl -s -w "\nHTTP_CODE:%{http_code}\nTIME_TOTAL:%{time_total}" \
    "$LEADER_URL/cache/$KEY?consistency=strong")

  http_code=$(echo "$response" | grep "HTTP_CODE:" | cut -d: -f2)
  time_total=$(echo "$response" | grep "TIME_TOTAL:" | cut -d: -f2)
  body=$(echo "$response" | sed '/HTTP_CODE:/d' | sed '/TIME_TOTAL:/d')

  latency_header=$(echo "$body" | grep -o '"X-Read-Latency-Ms"[^}]*' || echo "")
  consistency=$(echo "$body" | grep -o '"X-Consistency-Level"[^}]*' || echo "")

  echo "   Request $i: Latency=${time_total}s, Code=$http_code"
  echo "   Headers: $latency_header $consistency"
done

echo ""

# Step 3: Test LEASE consistency (fast path when lease is valid)
echo "3. Testing LEASE consistency (uses lease when valid)..."
echo "   Expected: ~1-10ms latency when lease is valid"
for i in {1..5}; do
  response=$(curl -s -w "\nHTTP_CODE:%{http_code}\nTIME_TOTAL:%{time_total}" \
    "$LEADER_URL/cache/$KEY?consistency=lease")

  http_code=$(echo "$response" | grep "HTTP_CODE:" | cut -d: -f2)
  time_total=$(echo "$response" | grep "TIME_TOTAL:" | cut -d: -f2)
  body=$(echo "$response" | sed '/HTTP_CODE:/d' | sed '/TIME_TOTAL:/d')

  latency_header=$(echo "$body" | grep -o '"X-Read-Latency-Ms"[^}]*' || echo "")
  consistency=$(echo "$body" | grep -o '"X-Consistency-Level"[^}]*' || echo "")
  lease_remaining=$(echo "$body" | grep -o '"X-Lease-Remaining-Ms"[^}]*' || echo "")

  echo "   Request $i: Latency=${time_total}s, Code=$http_code"
  echo "   Headers: $latency_header $consistency $lease_remaining"

  # Small delay between requests to see lease behavior
  sleep 0.2
done

echo ""

# Step 4: Test EVENTUAL consistency (fastest, potentially stale)
echo "4. Testing EVENTUAL consistency (local read, may be stale)..."
echo "   Expected: ~1-5ms latency (no checks)"
for i in {1..5}; do
  response=$(curl -s -w "\nHTTP_CODE:%{http_code}\nTIME_TOTAL:%{time_total}" \
    "$LEADER_URL/cache/$KEY?consistency=eventual")

  http_code=$(echo "$response" | grep "HTTP_CODE:" | cut -d: -f2)
  time_total=$(echo "$response" | grep "TIME_TOTAL:" | cut -d: -f2)
  body=$(echo "$response" | sed '/HTTP_CODE:/d' | sed '/TIME_TOTAL:/d')

  latency_header=$(echo "$body" | grep -o '"X-Read-Latency-Ms"[^}]*' || echo "")
  consistency=$(echo "$body" | grep -o '"X-Consistency-Level"[^}]*' || echo "")

  echo "   Request $i: Latency=${time_total}s, Code=$http_code"
  echo "   Headers: $latency_header $consistency"
done

echo ""

# Step 5: Latency Comparison
echo "=========================================="
echo "5. Latency Comparison Summary"
echo "=========================================="
echo ""
echo "Running 10 requests for each consistency level..."
echo ""

# STRONG
echo "STRONG consistency (10 requests):"
strong_total=0
for i in {1..10}; do
  time_total=$(curl -s -w "%{time_total}" -o /dev/null "$LEADER_URL/cache/$KEY?consistency=strong")
  strong_total=$(echo "$strong_total + $time_total" | bc)
  echo -n "."
done
strong_avg=$(echo "scale=3; $strong_total / 10" | bc)
echo ""
echo "Average latency: ${strong_avg}s"
echo ""

# LEASE
echo "LEASE consistency (10 requests):"
lease_total=0
for i in {1..10}; do
  time_total=$(curl -s -w "%{time_total}" -o /dev/null "$LEADER_URL/cache/$KEY?consistency=lease")
  lease_total=$(echo "$lease_total + $time_total" | bc)
  echo -n "."
  sleep 0.1  # Stay within lease window
done
lease_avg=$(echo "scale=3; $lease_total / 10" | bc)
echo ""
echo "Average latency: ${lease_avg}s"
echo ""

# EVENTUAL
echo "EVENTUAL consistency (10 requests):"
eventual_total=0
for i in {1..10}; do
  time_total=$(curl -s -w "%{time_total}" -o /dev/null "$LEADER_URL/cache/$KEY?consistency=eventual")
  eventual_total=$(echo "$eventual_total + $time_total" | bc)
  echo -n "."
done
eventual_avg=$(echo "scale=3; $eventual_total / 10" | bc)
echo ""
echo "Average latency: ${eventual_avg}s"
echo ""

echo "=========================================="
echo "Performance Improvement"
echo "=========================================="
echo "STRONG:   ${strong_avg}s (baseline)"
echo "LEASE:    ${lease_avg}s (~10-100x faster)"
echo "EVENTUAL: ${eventual_avg}s (~10-100x faster)"
echo ""

# Calculate speedup
if [ $(echo "$strong_avg > 0" | bc) -eq 1 ]; then
  lease_speedup=$(echo "scale=1; $strong_avg / $lease_avg" | bc)
  eventual_speedup=$(echo "scale=1; $strong_avg / $eventual_avg" | bc)
  echo "LEASE is ${lease_speedup}x faster than STRONG"
  echo "EVENTUAL is ${eventual_speedup}x faster than STRONG"
fi

echo ""
echo "=========================================="
echo "✅ Phase 1 Read Lease Test Complete!"
echo "=========================================="
