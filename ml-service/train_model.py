#!/usr/bin/env python3
"""
Train ML Model for Cache Eviction Prediction

This script generates synthetic access pattern data and trains a RandomForest
classifier to predict which cache keys are likely to be accessed in the next hour.

Features:
- hours_since_last_access
- access_count_hour
- access_count_day
- key_hash (normalized)
- hour_of_day
- day_of_week

Label: will_be_accessed (1 if accessed in next hour, 0 otherwise)
"""

import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, accuracy_score, confusion_matrix
import joblib
import datetime
import hashlib


def hash_key(key):
    """Hash a key to a numeric value between 0 and 1."""
    return int(hashlib.md5(key.encode()).hexdigest(), 16) % 10000 / 10000.0


def generate_synthetic_data(n_samples=10000):
    """
    Generate synthetic cache access patterns.

    Returns DataFrame with features and labels.
    """
    np.random.seed(42)

    data = []

    for i in range(n_samples):
        # Generate a synthetic key
        key_id = f"key_{i % 1000}"  # 1000 unique keys
        key_hash_val = hash_key(key_id)

        # Simulate current time (random time in the last week)
        current_time = datetime.datetime.now() - datetime.timedelta(
            days=np.random.randint(0, 7),
            hours=np.random.randint(0, 24),
            minutes=np.random.randint(0, 60)
        )

        hour_of_day = current_time.hour
        day_of_week = current_time.weekday()

        # Generate access patterns based on different profiles
        profile = np.random.choice(['hot', 'warm', 'cold'], p=[0.2, 0.3, 0.5])

        if profile == 'hot':
            # Hot keys: recently accessed, high frequency
            hours_since_last_access = np.random.exponential(0.5)  # Very recent
            access_count_hour = np.random.poisson(20)  # High frequency
            access_count_day = np.random.poisson(100)
            will_be_accessed = 1 if np.random.random() < 0.9 else 0  # 90% chance

        elif profile == 'warm':
            # Warm keys: moderately accessed
            hours_since_last_access = np.random.exponential(3)  # Somewhat recent
            access_count_hour = np.random.poisson(5)  # Medium frequency
            access_count_day = np.random.poisson(30)
            will_be_accessed = 1 if np.random.random() < 0.5 else 0  # 50% chance

        else:  # cold
            # Cold keys: rarely accessed, stale
            hours_since_last_access = np.random.uniform(10, 72)  # Long time ago
            access_count_hour = np.random.poisson(0.5)  # Very low frequency
            access_count_day = np.random.poisson(2)
            will_be_accessed = 1 if np.random.random() < 0.1 else 0  # 10% chance

        # Add some time-based patterns (e.g., business hours)
        if 9 <= hour_of_day <= 17 and day_of_week < 5:  # Business hours
            access_count_hour = int(access_count_hour * 1.5)
            access_count_day = int(access_count_day * 1.3)

        # Cap values to reasonable ranges
        hours_since_last_access = min(hours_since_last_access, 168)  # Max 1 week
        access_count_hour = min(access_count_hour, 100)
        access_count_day = min(access_count_day, 500)

        data.append({
            'key_hash': key_hash_val,
            'hours_since_last_access': hours_since_last_access,
            'access_count_hour': access_count_hour,
            'access_count_day': access_count_day,
            'hour_of_day': hour_of_day,
            'day_of_week': day_of_week,
            'will_be_accessed': will_be_accessed
        })

    return pd.DataFrame(data)


def train_model(df):
    """
    Train RandomForest classifier on the data.

    Returns trained model.
    """
    # Separate features and labels
    feature_columns = [
        'key_hash',
        'hours_since_last_access',
        'access_count_hour',
        'access_count_day',
        'hour_of_day',
        'day_of_week'
    ]

    X = df[feature_columns]
    y = df['will_be_accessed']

    # Split into train and test sets
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    print("Training RandomForest Classifier...")
    print(f"Training samples: {len(X_train)}")
    print(f"Test samples: {len(X_test)}")
    print(f"Features: {feature_columns}")
    print()

    # Train model
    model = RandomForestClassifier(
        n_estimators=100,
        max_depth=10,
        random_state=42,
        n_jobs=-1
    )

    model.fit(X_train, y_train)

    # Evaluate on test set
    y_pred = model.predict(X_test)
    y_pred_proba = model.predict_proba(X_test)

    print("=" * 60)
    print("Model Evaluation")
    print("=" * 60)
    print(f"Accuracy: {accuracy_score(y_test, y_pred):.4f}")
    print()

    print("Classification Report:")
    print(classification_report(y_test, y_pred, target_names=['Not Accessed', 'Will Be Accessed']))
    print()

    print("Confusion Matrix:")
    print(confusion_matrix(y_test, y_pred))
    print()

    # Feature importance
    print("Feature Importance:")
    for feature, importance in zip(feature_columns, model.feature_importances_):
        print(f"  {feature:30s}: {importance:.4f}")
    print()

    return model


def main():
    """Main training pipeline."""
    print("=" * 60)
    print("Cache Eviction Model Training")
    print("=" * 60)
    print()

    # Generate synthetic data
    print("Generating synthetic training data...")
    df = generate_synthetic_data(n_samples=10000)

    print(f"Generated {len(df)} samples")
    print(f"Label distribution:")
    print(df['will_be_accessed'].value_counts())
    print()

    # Train model
    model = train_model(df)

    # Save model
    model_path = 'cache_eviction_model.pkl'
    joblib.dump(model, model_path)
    print(f"Model saved to: {model_path}")
    print()

    print("=" * 60)
    print("Training Complete!")
    print("=" * 60)
    print()
    print("Next steps:")
    print("  1. Start the Flask API: python app.py")
    print("  2. Test predictions with the /predict endpoint")
    print()


if __name__ == '__main__':
    main()
