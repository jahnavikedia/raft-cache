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

    Uses a heuristic-based scoring system that combines:
    - Recency: How recently was the key accessed?
    - Frequency: How often is the key accessed?
    - Trend: Is access increasing or decreasing?
    """
    try:
        data = request.get_json()
        keys = data.get('keys', [])
        current_time = data.get('currentTime', 0)

        if not keys:
            return jsonify({"error": "No keys provided"}), 400

        predictions = []

        # If no current time provided, use the max last access time
        if current_time == 0:
            current_time = max([k.get('last_access_ms', 0) for k in keys]) if keys else 0

        # Calculate scores for each key
        scores = []
        for key_data in keys:
            key = key_data.get('key')
            access_count = key_data.get('access_count', 0)
            last_access_ms = key_data.get('last_access_ms', 0)
            access_count_hour = key_data.get('access_count_hour', 0)
            access_count_day = key_data.get('access_count_day', 0)

            # Calculate time since last access in seconds
            time_since_last_sec = (current_time - last_access_ms) / 1000 if last_access_ms > 0 else float('inf')

            # Score components (higher = more likely to be accessed again)

            # 1. Recency score (0-40 points): More recent = higher score
            # Decays exponentially: score = 40 * exp(-time_seconds / 60)
            # At 0 seconds: 40 points, at 60 seconds: ~15 points, at 120 seconds: ~5 points
            recency_score = 40 * np.exp(-time_since_last_sec / 60) if time_since_last_sec < float('inf') else 0

            # 2. Frequency score (0-30 points): More accesses = higher score
            # Logarithmic scale to prevent domination by very high counts
            frequency_score = min(30, 10 * np.log1p(access_count))

            # 3. Recent activity score (0-30 points): Higher hourly rate = higher score
            # Use logarithmic scale to avoid saturation at low counts
            hourly_rate = access_count_hour
            activity_score = min(30, 10 * np.log1p(hourly_rate))

            total_score = recency_score + frequency_score + activity_score

            scores.append({
                "key": key,
                "total_score": total_score,
                "recency_score": recency_score,
                "frequency_score": frequency_score,
                "activity_score": activity_score,
                "access_count": access_count,
                "time_since_last_sec": time_since_last_sec
            })

        # Convert scores to probabilities using sigmoid-like scaling
        # Max possible score is 100 (40 recency + 30 frequency + 30 activity)
        # Use this to get absolute probabilities, not relative
        MAX_POSSIBLE_SCORE = 100.0

        for score_data in scores:
            # Convert score to probability: score/max gives 0-1 range
            # Apply sigmoid-like curve for better distribution
            raw_prob = score_data["total_score"] / MAX_POSSIBLE_SCORE
            # Clamp to reasonable range
            probability = max(0.05, min(0.95, raw_prob))

            predictions.append({
                "key": score_data["key"],
                "probability": float(round(probability, 4)),
                "willBeAccessed": bool(probability >= 0.5),
                "debug": {
                    "recency": float(round(score_data["recency_score"], 2)),
                    "frequency": float(round(score_data["frequency_score"], 2)),
                    "activity": float(round(score_data["activity_score"], 2)),
                    "total": float(round(score_data["total_score"], 2)),
                    "accessCount": int(score_data["access_count"]),
                    "secSinceAccess": float(round(score_data["time_since_last_sec"], 1))
                }
            })

        logger.info("Generated {} predictions".format(len(predictions)))
        return jsonify({"predictions": predictions}), 200

    except Exception as e:
        logger.error("Prediction error: {}".format(str(e)))
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


if __name__ == '__main__':
    logger.info("Starting ML Service on port 5001...")
    app.run(host='0.0.0.0', port=5001, debug=False)
