# ML-Based Cache Eviction Service

This service provides machine learning-based predictions for cache eviction decisions in the Raft-based distributed cache.

## Overview

The ML service uses a Random Forest classifier to predict which cache keys are most likely to be accessed in the near future. Keys with the lowest probability of being accessed are recommended for eviction.

## Features

- **Access Pattern Analysis**: Analyzes historical access patterns including:
  - Hours since last access
  - Access count in last hour
  - Access count in last day
  - Key hash (for key-specific patterns)
  - Hour of day (temporal patterns)
  - Day of week (weekly patterns)

- **ML Model**: Random Forest classifier (100 trees, max depth 10)
- **REST API**: Flask-based API for predictions
- **Thread-safe**: Designed for concurrent requests

## Setup

### 1. Create Virtual Environment

```bash
cd ml-service
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
```

### 2. Install Dependencies

```bash
pip install -r requirements.txt
```

### 3. Train the Model

```bash
python train_model.py
```

This will:
- Generate 10,000 synthetic access patterns
- Train a Random Forest classifier
- Print accuracy metrics and feature importance
- Save the model to `cache_eviction_model.pkl`

Expected output:
```
Cache Eviction Model Training
============================================================

Generating synthetic training data...
Generated 10000 samples
Training RandomForest Classifier...
Training samples: 8000
Test samples: 2000

Model Evaluation
============================================================
Accuracy: 0.85+

Feature Importance:
  hours_since_last_access       : 0.35-0.40
  access_count_hour            : 0.25-0.30
  access_count_day             : 0.15-0.20
  ...
```

### 4. Start the ML Service

```bash
python app.py
```

The service will start on `http://localhost:5000`.

## API Endpoints

### GET /health

Health check endpoint.

**Response:**
```json
{
  "status": "healthy",
  "model": "cache_eviction_model.pkl"
}
```

### POST /predict

Predict which key should be evicted based on access patterns.

**Request:**
```json
{
  "keys": ["user:123", "session:456", "cache:789"],
  "accessHistory": {
    "user:123": [1700000000000, 1700000300000, 1700000600000],
    "session:456": [],
    "cache:789": [1699900000000]
  },
  "currentTime": 1700001000000
}
```

**Response:**
```json
{
  "evictKey": "session:456",
  "confidence": 0.92,
  "predictions": [
    {
      "key": "session:456",
      "probability": 0.08,
      "willBeAccessed": false
    },
    {
      "key": "cache:789",
      "probability": 0.35,
      "willBeAccessed": false
    },
    {
      "key": "user:123",
      "probability": 0.87,
      "willBeAccessed": true
    }
  ]
}
```

## Testing

### Test Health Endpoint

```bash
curl http://localhost:5000/health
```

### Test Prediction Endpoint

```bash
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

### Integration with Java Cache

The Java cache can query this service via HTTP:

```java
// Example integration (pseudo-code)
String mlServiceUrl = "http://localhost:5000/predict";
List<String> keys = cache.getAllKeys();
Map<String, List<Long>> accessHistory = accessTracker.getAccessHistory();

// Build request
JsonObject request = new JsonObject();
request.add("keys", keys);
request.add("accessHistory", accessHistory);
request.addProperty("currentTime", System.currentTimeMillis());

// Get prediction
HttpResponse response = httpClient.post(mlServiceUrl, request);
JsonObject prediction = response.getBody();
String evictKey = prediction.get("evictKey").getAsString();

// Evict the recommended key
cache.evict(evictKey);
```

## Model Details

### Features

1. **key_hash** (0.0-1.0): Hash of the key for key-specific patterns
2. **hours_since_last_access** (0-168): Hours since the key was last accessed
3. **access_count_hour** (0-100): Number of accesses in the last hour
4. **access_count_day** (0-500): Number of accesses in the last 24 hours
5. **hour_of_day** (0-23): Current hour for temporal patterns
6. **day_of_week** (0-6): Current day of week for weekly patterns

### Training Data

The model is trained on synthetic data with three access profiles:

- **Hot Keys** (20%): Recently accessed, high frequency, 90% chance of re-access
- **Warm Keys** (30%): Moderately accessed, 50% chance of re-access
- **Cold Keys** (50%): Rarely accessed, stale, 10% chance of re-access

### Performance

- Accuracy: ~85-90% on test data
- Most important features: hours_since_last_access, access_count_hour
- Precision/Recall balanced for both classes

## Troubleshooting

### Model not found

```
ERROR: Model file not found at cache_eviction_model.pkl
Please run train_model.py first to generate the model.
```

**Solution:** Run `python train_model.py` to generate the model.

### Port already in use

**Solution:** Change the port in `app.py` or kill the process using port 5000:
```bash
lsof -ti:5000 | xargs kill -9
```

### Import errors

**Solution:** Make sure you're in the virtual environment and have installed dependencies:
```bash
source venv/bin/activate
pip install -r requirements.txt
```

## Production Considerations

For production deployment:

1. **Model Retraining**: Periodically retrain the model with real access patterns
2. **Caching**: Cache predictions for frequently queried key sets
3. **Load Balancing**: Run multiple instances behind a load balancer
4. **Monitoring**: Add metrics for prediction latency and model accuracy
5. **A/B Testing**: Compare ML-based eviction with LRU/LFU baselines
6. **Feature Store**: Store computed features to reduce computation time
