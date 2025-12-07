import React, { useState } from 'react'
import { Brain, AlertTriangle, CheckCircle } from 'lucide-react'
import axios from 'axios'

const MLStats = ({ prediction, setPrediction, nodes }) => {
  const [loading, setLoading] = useState(false)

  const getPrediction = async () => {
    setLoading(true)
    try {
      // 1. Get stats from Leader (since traffic hits leader)
      const leader = nodes.find(n => n.state === 'LEADER' && n.active) || nodes[0]
      const statsRes = await axios.get(`http://localhost:${leader.port}/cache/access-stats`)
      const stats = statsRes.data.stats

      if (!stats || stats.length === 0) {
        setPrediction(null)
        setLoading(false)
        console.warn("No access stats available for prediction")
        return
      }

      // 2. Format for ML Service
      // We need to construct the payload expected by app.py
      // stats is an Array of objects from the backend
      const keys = stats.map(item => ({
        key: item.key,
        access_count: item.totalAccessCount,
        last_access_ms: item.lastAccessTime,
        access_count_hour: item.accessCountHour,
        access_count_day: item.accessCountDay,
        avg_interval_ms: 0 // Simplified for now
      }))

      const payload = {
          keys: keys,
          currentTime: Date.now()
      }

      // 3. Call ML Service
      const mlRes = await axios.post('http://localhost:5001/predict', payload)
      
      // Find the key most likely to NOT be accessed (lowest probability)
      const predictions = mlRes.data.predictions
      const evictionCandidate = predictions.sort((a, b) => a.probability - b.probability)[0]
      
      if (evictionCandidate && evictionCandidate.key && evictionCandidate.key !== "0") {
          setPrediction(evictionCandidate)
      } else {
          setPrediction(null)
          console.warn("Received invalid prediction:", evictionCandidate)
      }

    } catch (e) {
      console.error("ML Prediction failed", e)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div>
      <div style={{ marginBottom: '1rem' }}>
        <button className="btn" onClick={getPrediction} disabled={loading} style={{ width: '100%', justifyContent: 'center', background: 'var(--accent-purple)', border: 'none' }}>
          <Brain size={16} /> {loading ? 'Analyzing...' : 'Analyze Eviction Candidates'}
        </button>
      </div>

      {prediction ? (
        <div style={{
          background: 'rgba(255, 255, 255, 0.03)',
          borderRadius: '8px',
          padding: '1rem',
          border: '1px solid var(--border-color)',
          textAlign: 'center'
        }}>
          <div style={{ color: 'var(--text-secondary)', marginBottom: '0.5rem' }}>Recommended Eviction:</div>
          <div style={{ fontWeight: 'bold', color: 'var(--warning)', fontSize: '1.5rem' }}>{prediction.key}</div>
          <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginTop: '0.75rem' }}>
            This key has the lowest predicted re-access likelihood based on ML analysis.
          </div>
        </div>
      ) : (
        <div style={{ textAlign: 'center', color: 'var(--text-secondary)', padding: '2rem 0', fontSize: '0.9rem' }}>
          <p style={{ marginBottom: '0.5rem' }}>Click analyze to let the ML model find the best key to evict.</p>
          <span style={{ fontSize: '0.8rem', opacity: 0.7 }}>The model analyzes access patterns to identify "cold" data.</span>
        </div>
      )}
    </div>
  )
}

export default MLStats
