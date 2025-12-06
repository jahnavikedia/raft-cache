#!/usr/bin/env python3
"""
ML Service for Cache Eviction Predictions

This Flask service provides ML-based predictions for which cache keys
are likely to be accessed again, helping optimize eviction decisions.

Uses a RandomForest classifier trained on access pattern features.
"""

from flask import Flask, request, jsonify
import numpy as np
from sklearn.ensemble import RandomForestClassifier
import logging

from flask_cors import CORS

app = Flask(__name__)
CORS(app)
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Train a simple RandomForest model on startup
# In production, this would be loaded from a saved model file
model = RandomForestClassifier(n_estimators=10, max_depth=5, random_state=42)

# Generate synthetic training data
# Features: [access_count, hours_since_last_access, access_count_hour, access_count_day, avg_interval_hours]
# Label: will_be_accessed (1 = likely to be accessed, 0 = unlikely)
np.random.seed(42)
X_train = []
y_train = []

# Generate positive examples (keys likely to be accessed)
for _ in range(100):
    access_count = np.random.randint(10, 100)
    hours_since_last = np.random.uniform(0, 2)  # Recently accessed
    access_hour = np.random.randint(5, 50)
    access_day = np.random.randint(20, 200)
    avg_interval = np.random.uniform(0.1, 2)  # Frequent accesses
    X_train.append([access_count, hours_since_last, access_hour, access_day, avg_interval])
    y_train.append(1)

# Generate negative examples (keys unlikely to be accessed)
for _ in range(100):
    access_count = np.random.randint(1, 20)
    hours_since_last = np.random.uniform(5, 48)  # Not accessed recently
    access_hour = np.random.randint(0, 5)
    access_day = np.random.randint(1, 30)
    avg_interval = np.random.uniform(5, 24)  # Infrequent accesses
    X_train.append([access_count, hours_since_last, access_hour, access_day, avg_interval])
    y_train.append(0)

# Train the model
model.fit(X_train, y_train)
logger.info("ML model trained with {} samples".format(len(X_train)))


@app.route('/health', methods=['GET'])
def health():
    """Health check endpoint"""
    return jsonify({"status": "healthy"}), 200


@app.route('/predict', methods=['POST'])
def predict():
    """
    Predict which keys are likely to be accessed again.
    """
    try:
        data = request.get_json()
        keys = data.get('keys', [])

        if not keys:
            return jsonify({"error": "No keys provided"}), 400

        predictions = []
        current_time_ms = max([k.get('last_access_ms', 0) for k in keys]) if keys else 0

        for key_data in keys:
            key = key_data.get('key')
            access_count = key_data.get('access_count', 0)
            last_access_ms = key_data.get('last_access_ms', 0)
            access_count_hour = key_data.get('access_count_hour', 0)
            access_count_day = key_data.get('access_count_day', 0)
            avg_interval_ms = key_data.get('avg_interval_ms', 0)

            # Calculate hours since last access
            hours_since_last = (current_time_ms - last_access_ms) / (1000 * 3600) if last_access_ms > 0 else 999

            # Convert average interval to hours
            avg_interval_hours = avg_interval_ms / (1000 * 3600) if avg_interval_ms > 0 else 999

            # Prepare features for prediction
            features = np.array([[
                access_count,
                hours_since_last,
                access_count_hour,
                access_count_day,
                avg_interval_hours
            ]])

            # Get prediction probability
            proba = model.predict_proba(features)[0]
            probability = float(proba[1])  # Probability of being accessed (class 1)
            will_be_accessed = probability >= 0.5

            predictions.append({
                "key": key,
                "probability": round(probability, 4),
                "willBeAccessed": will_be_accessed
            })

        logger.info("Generated {} predictions".format(len(predictions)))
        return jsonify({"predictions": predictions}), 200

    except Exception as e:
        logger.error("Prediction error: {}".format(str(e)))
        return jsonify({"error": str(e)}), 500


if __name__ == '__main__':
    logger.info("Starting ML Service on port 5001...")
    app.run(host='0.0.0.0', port=5001, debug=False)
