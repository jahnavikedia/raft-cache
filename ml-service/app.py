#!/usr/bin/env python3
"""
Flask API for ML-based Cache Eviction Predictions

This service provides a REST API for predicting which cache keys should be evicted
based on their access patterns.

Endpoints:
- GET /health - Health check
- POST /predict - Predict which key to evict
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
import joblib
import numpy as np
import hashlib
import datetime
import os

app = Flask(__name__)
CORS(app)

# Load the trained model
MODEL_PATH = 'cache_eviction_model.pkl'
model = None


def load_model():
    """Load the ML model from disk."""
    global model
    if not os.path.exists(MODEL_PATH):
        print(f"ERROR: Model file not found at {MODEL_PATH}")
        print("Please run train_model.py first to generate the model.")
        return False

    try:
        model = joblib.load(MODEL_PATH)
        print(f"Model loaded successfully from {MODEL_PATH}")
        return True
    except Exception as e:
        print(f"ERROR: Failed to load model: {e}")
        return False


def hash_key(key):
    """Hash a key to a numeric value between 0 and 1."""
    return int(hashlib.md5(key.encode()).hexdigest(), 16) % 10000 / 10000.0


def extract_features(key, access_history, current_time):
    """
    Extract features from access history for a single key.

    Args:
        key: The cache key
        access_history: List of access timestamps (in milliseconds)
        current_time: Current timestamp (in milliseconds)

    Returns:
        Dictionary of features
    """
    current_time_ms = current_time
    current_datetime = datetime.datetime.fromtimestamp(current_time_ms / 1000.0)

    # Key hash
    key_hash_val = hash_key(key)

    # Calculate hours since last access
    if access_history and len(access_history) > 0:
        last_access_ms = max(access_history)
        hours_since_last_access = (current_time_ms - last_access_ms) / (1000.0 * 3600.0)
    else:
        hours_since_last_access = 168  # Default to 1 week if never accessed

    # Count accesses in last hour
    one_hour_ago = current_time_ms - (60 * 60 * 1000)
    access_count_hour = sum(1 for ts in access_history if ts >= one_hour_ago)

    # Count accesses in last day
    one_day_ago = current_time_ms - (24 * 60 * 60 * 1000)
    access_count_day = sum(1 for ts in access_history if ts >= one_day_ago)

    # Time features
    hour_of_day = current_datetime.hour
    day_of_week = current_datetime.weekday()

    return {
        'key_hash': key_hash_val,
        'hours_since_last_access': min(hours_since_last_access, 168),
        'access_count_hour': access_count_hour,
        'access_count_day': access_count_day,
        'hour_of_day': hour_of_day,
        'day_of_week': day_of_week
    }


@app.route('/health', methods=['GET'])
def health():
    """Health check endpoint."""
    if model is None:
        return jsonify({'status': 'unhealthy', 'error': 'Model not loaded'}), 503

    return jsonify({'status': 'healthy', 'model': MODEL_PATH})


@app.route('/predict', methods=['POST'])
def predict():
    """
    Predict which key should be evicted.

    Request body:
    {
        "keys": ["key1", "key2", "key3"],
        "accessHistory": {
            "key1": [timestamp1, timestamp2, ...],
            "key2": [...],
            "key3": [...]
        },
        "currentTime": timestamp_ms
    }

    Response:
    {
        "evictKey": "key_to_evict",
        "confidence": 0.92,
        "predictions": [
            {"key": "key1", "probability": 0.85, "willBeAccessed": true},
            {"key": "key2", "probability": 0.12, "willBeAccessed": false},
            ...
        ]
    }
    """
    if model is None:
        return jsonify({'error': 'Model not loaded'}), 503

    try:
        data = request.get_json()

        # Validate request
        if not data or 'keys' not in data or 'accessHistory' not in data:
            return jsonify({'error': 'Invalid request format'}), 400

        keys = data['keys']
        access_history = data.get('accessHistory', {})
        current_time = data.get('currentTime', int(datetime.datetime.now().timestamp() * 1000))

        if not keys or len(keys) == 0:
            return jsonify({'error': 'No keys provided'}), 400

        # Extract features for each key
        predictions = []
        feature_columns = [
            'key_hash',
            'hours_since_last_access',
            'access_count_hour',
            'access_count_day',
            'hour_of_day',
            'day_of_week'
        ]

        for key in keys:
            key_history = access_history.get(key, [])
            features = extract_features(key, key_history, current_time)

            # Convert to numpy array in correct order
            X = np.array([[features[col] for col in feature_columns]])

            # Predict probability of being accessed
            proba = model.predict_proba(X)[0]
            will_be_accessed_prob = proba[1]  # Probability of class 1 (will be accessed)

            predictions.append({
                'key': key,
                'probability': float(will_be_accessed_prob),
                'willBeAccessed': bool(will_be_accessed_prob >= 0.5)
            })

        # Sort by probability (ascending) - lowest probability should be evicted
        predictions.sort(key=lambda x: x['probability'])

        # The key with lowest probability of being accessed should be evicted
        evict_key = predictions[0]['key']
        evict_probability = predictions[0]['probability']
        confidence = 1.0 - evict_probability  # Confidence = probability it won't be accessed

        return jsonify({
            'evictKey': evict_key,
            'confidence': float(confidence),
            'predictions': predictions
        })

    except Exception as e:
        return jsonify({'error': str(e)}), 500


def main():
    """Main entry point."""
    print("=" * 60)
    print("Cache Eviction ML Service")
    print("=" * 60)
    print()

    # Load model
    if not load_model():
        print("Failed to load model. Exiting.")
        print("Please run: python train_model.py")
        return

    print()
    print("Starting Flask server on http://localhost:5000")
    print()
    print("Endpoints:")
    print("  GET  /health  - Health check")
    print("  POST /predict - Predict which key to evict")
    print()
    print("=" * 60)
    print()

    # Run Flask app
    app.run(host='0.0.0.0', port=5001, debug=True)


if __name__ == '__main__':
    main()
