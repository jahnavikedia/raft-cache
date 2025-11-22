# 🚀 Quick Test Guide

## Easiest Way (1 command, 30 seconds)

```bash
./test_complete_demo.sh
```

This automated script tests everything and shows you the results.

---

## Manual Way (3 terminals)

### Terminal 1: Start ML Service
```bash
cd ml-service
source venv/bin/activate
python app.py
```
Wait for: "Starting Flask server on http://localhost:5001"

### Terminal 2: Start Raft Node
```bash
java -jar target/raft-cache-1.0-SNAPSHOT.jar --config config/node1.yaml
```
Wait 15 seconds for leader election.

### Terminal 3: Test It
```bash
# Insert keys
CLIENT_ID="test-$(date +%s)"
for i in {1..5}; do
  curl -X POST http://localhost:8081/cache/key$i \
    -H "Content-Type: application/json" \
    -d "{\"clientId\": \"$CLIENT_ID\", \"value\": \"value$i\", \"sequenceNumber\": $i}"
done

# Access key1 and key2 frequently (hot keys)
for i in {1..10}; do
  curl -s http://localhost:8081/cache/key1 > /dev/null
  curl -s http://localhost:8081/cache/key2 > /dev/null
done

# Access key3-5 once (cold keys)
curl -s http://localhost:8081/cache/key3 > /dev/null
curl -s http://localhost:8081/cache/key4 > /dev/null
curl -s http://localhost:8081/cache/key5 > /dev/null

# View access statistics
echo "=== Access Statistics ==="
curl -s http://localhost:8081/cache/access-stats | python3 -m json.tool

# Get ML recommendation
echo ""
echo "=== ML Recommendation ==="
curl -s -X POST http://localhost:5001/predict \
  -H 'Content-Type: application/json' \
  -d "{
    \"keys\": [\"key1\", \"key2\", \"key3\", \"key4\", \"key5\"],
    \"accessHistory\": {
      \"key1\": [$(date +%s)000],
      \"key2\": [$(date +%s)000],
      \"key3\": [$(date +%s)000],
      \"key4\": [$(date +%s)000],
      \"key5\": [$(date +%s)000]
    },
    \"currentTime\": $(date +%s)000
  }" | python3 -m json.tool
```

---

## What You Should See

✅ **Access Stats:** key1 & key2 have ~10 accesses, others have ~1

✅ **ML Prediction:** Recommends evicting key3, key4, or key5 (least accessed)

✅ **Confidence:** > 70%

---

## Cleanup

```bash
pkill -f "python app.py" && pkill -f "java -jar.*raft-cache"
```

---

## Endpoints

- ML Health: http://localhost:5001/health
- ML Predict: http://localhost:5001/predict
- Raft Status: http://localhost:8081/status
- Access Stats: http://localhost:8081/cache/access-stats

**Full guide:** See [TESTING_GUIDE.md](TESTING_GUIDE.md)
