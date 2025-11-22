# Quick Start Guide: ML-Based Cache Eviction

This guide walks you through setting up and testing the ML-based cache eviction system.

## Part 1: Access Tracking (Java)

### What Was Added

1. **AccessStats.java** - Tracks per-key statistics:
   - Last 100 access timestamps
   - Access count in last hour/day
   - Thread-safe with atomic counters

2. **AccessTracker.java** - Manages all keys' access patterns:
   - ConcurrentHashMap for thread-safety
   - Automatic decay every 5 minutes
   - Provides data for ML predictions

3. **KeyValueStore.java** - Modified to track accesses:
   - Records access on every `get()` call
   - Exposes `AccessTracker` via `getAccessTracker()`

4. **CacheRESTServer.java** - New endpoint:
   - `GET /cache/access-stats` - Returns access statistics

### Testing Access Tracking

1. **Build the project:**
   ```bash
   mvn clean package
   ```

2. **Start a Raft node:**
   ```bash
   # Start node1 on port 8081
   java -jar target/raft-cache-1.0-SNAPSHOT.jar node1 8081
   ```

3. **Run the access tracking test:**
   ```bash
   ./scripts/test_access_tracking.sh
   ```

   This script will:
   - Insert 10 keys
   - Access keys 1-5 ten times each
   - Access keys 6-10 once each
   - Fetch and display access statistics

4. **Expected output:**
   ```json
   {
     "nodeId": "node1",
     "trackedKeys": 10,
     "stats": [
       {
         "key": "key1",
         "lastAccessTime": 1700000000000,
         "accessCountHour": 10,
         "accessCountDay": 10,
         "totalAccessCount": 10,
         "recentTimestamps": [...]
       },
       {
         "key": "key6",
         "accessCountHour": 1,
         "accessCountDay": 1,
         "totalAccessCount": 1
       }
     ]
   }
   ```

## Part 2: ML Service (Python)

### Setup

1. **Navigate to ML service directory:**
   ```bash
   cd ml-service
   ```

2. **Create and activate virtual environment:**
   ```bash
   python3 -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   ```

3. **Install dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

### Train the Model

```bash
python train_model.py
```

**Expected output:**
```
============================================================
Cache Eviction Model Training
============================================================

Generating synthetic training data...
Generated 10000 samples
Label distribution:
0    5000
1    5000

Training RandomForest Classifier...
Training samples: 8000
Test samples: 2000

============================================================
Model Evaluation
============================================================
Accuracy: 0.8500

Classification Report:
              precision    recall  f1-score   support

Not Accessed       0.84      0.87      0.86      1000
Will Be Accessed   0.86      0.83      0.85      1000

Feature Importance:
  key_hash                      : 0.0892
  hours_since_last_access       : 0.3856
  access_count_hour            : 0.2734
  access_count_day             : 0.1623
  hour_of_day                  : 0.0512
  day_of_week                  : 0.0383

Model saved to: cache_eviction_model.pkl
```

### Start the ML Service

```bash
python app.py
```

The service will start on `http://localhost:5000`.

### Test the ML Service

**In a new terminal:**

```bash
# Test health endpoint
curl http://localhost:5000/health

# Test prediction endpoint
curl -X POST http://localhost:5000/predict \
  -H 'Content-Type: application/json' \
  -d '{
    "keys": ["user:123", "session:456", "cache:789"],
    "accessHistory": {
      "user:123": [1700000000000, 1700000300000],
      "session:456": [],
      "cache:789": [1699900000000]
    },
    "currentTime": 1700001000000
  }'
```

**Or use the automated test script:**

```bash
./test_ml_service.sh
```

## Part 3: Integration (Full System)

### Architecture

```
┌─────────────────┐
│  Raft Cache     │
│  (Java)         │
│                 │
│  ┌───────────┐  │      HTTP       ┌─────────────────┐
│  │ KVStore   │  │   /predict      │  ML Service     │
│  │           │◄─┼─────────────────┤  (Python/Flask) │
│  │ Access    │  │                 │                 │
│  │ Tracker   │  │   JSON Request  │  Random Forest  │
│  └───────────┘  │   ────────────► │  Classifier     │
│                 │                 │                 │
│  GET /cache/    │   JSON Response │                 │
│  access-stats   │   ◄──────────── │                 │
└─────────────────┘                 └─────────────────┘
```

### Integration Flow

1. **Client requests data:**
   ```
   GET /cache/user:123
   ```

2. **KeyValueStore tracks access:**
   ```java
   accessTracker.recordAccess("user:123");
   ```

3. **When cache is full, query ML service:**
   ```java
   List<String> keys = cache.getAllKeys();
   Map<String, List<Long>> history = accessTracker.getAccessHistory();

   // POST to http://localhost:5000/predict
   MLPrediction prediction = mlClient.predict(keys, history);
   String evictKey = prediction.getEvictKey();

   cache.evict(evictKey);
   ```

4. **ML service returns prediction:**
   ```json
   {
     "evictKey": "session:456",
     "confidence": 0.92
   }
   ```

## Example Scenario

### Scenario: Hot, Warm, and Cold Keys

```bash
# Terminal 1: Start Raft node
java -jar target/raft-cache-1.0-SNAPSHOT.jar node1 8081

# Terminal 2: Start ML service
cd ml-service
source venv/bin/activate
python app.py

# Terminal 3: Insert test data
CLIENT_ID="test-$(date +%s)"

# Insert hot key (will be accessed frequently)
curl -X POST http://localhost:8081/cache/hot:user123 \
  -H 'Content-Type: application/json' \
  -d "{\"clientId\": \"$CLIENT_ID\", \"value\": \"active-user\", \"sequenceNumber\": 1}"

# Insert warm key (accessed occasionally)
curl -X POST http://localhost:8081/cache/warm:session456 \
  -H 'Content-Type: application/json' \
  -d "{\"clientId\": \"$CLIENT_ID\", \"value\": \"session-data\", \"sequenceNumber\": 2}"

# Insert cold key (rarely accessed)
curl -X POST http://localhost:8081/cache/cold:temp789 \
  -H 'Content-Type: application/json' \
  -d "{\"clientId\": \"$CLIENT_ID\", \"value\": \"temp-data\", \"sequenceNumber\": 3}"

# Access hot key 10 times
for i in {1..10}; do
  curl -s http://localhost:8081/cache/hot:user123 > /dev/null
  sleep 0.5
done

# Access warm key 3 times
for i in {1..3}; do
  curl -s http://localhost:8081/cache/warm:session456 > /dev/null
  sleep 1
done

# Get access stats
curl http://localhost:8081/cache/access-stats | python3 -m json.tool

# Get ML prediction for which key to evict
STATS=$(curl -s http://localhost:8081/cache/access-stats)
# (Parse stats and call ML service - see integration code)
```

## Next Steps

1. **Implement MLClient.java** - Java client to call ML service
2. **Add eviction logic** - Integrate ML predictions into cache eviction
3. **Implement cache size limit** - Enforce max cache size (1000 entries)
4. **Add metrics** - Track eviction accuracy and cache hit rate
5. **Retrain with real data** - Use actual access patterns from production

## Troubleshooting

### Access tracking not working

- **Check logs:** Look for "Recorded access for key" messages
- **Verify endpoint:** `curl http://localhost:8081/cache/access-stats`
- **Check tracker started:** Look for "AccessTracker started" in logs

### ML service not responding

- **Check if running:** `curl http://localhost:5000/health`
- **Check model exists:** `ls ml-service/cache_eviction_model.pkl`
- **Check Python version:** Requires Python 3.7+
- **Check virtual env:** `which python` should point to venv

### Model accuracy is low

- **Train with more data:** Increase n_samples in train_model.py
- **Use real access patterns:** Replace synthetic data with actual logs
- **Tune hyperparameters:** Adjust n_estimators, max_depth

## Performance Considerations

- **Access tracking overhead:** ~1-2% performance impact on reads
- **Decay frequency:** Runs every 5 minutes (configurable)
- **ML prediction latency:** ~10-50ms for 100-1000 keys
- **Memory usage:** ~1KB per tracked key

## Architecture Benefits

1. **Separation of concerns:** ML service separate from cache
2. **Polyglot:** Java for cache, Python for ML
3. **Scalable:** ML service can be deployed independently
4. **Testable:** Each component can be tested in isolation
5. **Flexible:** Easy to swap ML models or algorithms
