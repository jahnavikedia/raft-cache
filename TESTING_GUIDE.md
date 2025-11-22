# Step-by-Step Testing Guide

This guide will walk you through testing the ML-based cache eviction system.

## Option 1: Automated Test (Recommended)

The easiest way is to run the automated demo script:

```bash
./test_complete_demo.sh
```

This will automatically test all components in ~30 seconds.

---

## Option 2: Manual Testing

### Step 1: Start ML Service

**Terminal 1:**
```bash
cd ml-service
source venv/bin/activate
python app.py
```

Wait for: `Starting Flask server on http://localhost:5001`

---

### Step 2: Start Raft Node  

**Terminal 2:**
```bash
java -jar target/raft-cache-1.0-SNAPSHOT.jar --config config/node1.yaml
```

Wait ~15 seconds for leader election.

---

### Step 3: Test the System

**Terminal 3:**

```bash
# 1. Insert test keys
CLIENT_ID="test-$(date +%s)"
for i in {1..5}; do
  curl -X POST http://localhost:8081/cache/key$i \
    -H "Content-Type: application/json" \
    -d "{\"clientId\": \"$CLIENT_ID\", \"value\": \"value$i\", \"sequenceNumber\": $i}"
done

# 2. Create access patterns (hot keys)
for i in {1..10}; do
  curl -s http://localhost:8081/cache/key1 > /dev/null
  curl -s http://localhost:8081/cache/key2 > /dev/null
done

# 3. Create warm and cold keys
curl -s http://localhost:8081/cache/key3 > /dev/null
curl -s http://localhost:8081/cache/key4 > /dev/null
curl -s http://localhost:8081/cache/key5 > /dev/null

# 4. View access statistics
curl http://localhost:8081/cache/access-stats | python3 -m json.tool

# 5. Get ML prediction
curl -X POST http://localhost:5001/predict \
  -H 'Content-Type: application/json' \
  -d '{
    "keys": ["key1", "key2", "key3", "key4", "key5"],
    "accessHistory": {
      "key1": ['$(date +%s)'000],
      "key2": ['$(date +%s)'000],
      "key3": ['$(date +%s)'000],
      "key4": ['$(date +%s)'000],
      "key5": ['$(date +%s)'000]
    },
    "currentTime": '$(date +%s)'000
  }' | python3 -m json.tool
```

---

## What to Expect

✅ **Access Statistics** should show:
- key1, key2: ~10 accesses (hot)
- key3, key4, key5: ~1 access (cold)

✅ **ML Prediction** should recommend evicting:
- key5, key4, or key3 (least accessed)
- With confidence > 0.7

---

## Cleanup

```bash
pkill -f "python app.py"
pkill -f "java -jar.*raft-cache"
```

---

## Quick Reference

| What | URL |
|------|-----|
| ML Health | http://localhost:5001/health |
| ML Predict | http://localhost:5001/predict |
| Raft Status | http://localhost:8081/status |
| Access Stats | http://localhost:8081/cache/access-stats |
| Cache GET | http://localhost:8081/cache/{key} |

For full details, see [QUICKSTART_ML.md](QUICKSTART_ML.md)
