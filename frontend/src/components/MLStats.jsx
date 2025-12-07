import React, { useState } from 'react'
import { Brain, Zap, TrendingDown, TrendingUp, Loader } from 'lucide-react'
import axios from 'axios'

const MLStats = ({ prediction, setPrediction, nodes }) => {
  const [loading, setLoading] = useState(false)
  const [generatingTraffic, setGeneratingTraffic] = useState(false)
  const [allPredictions, setAllPredictions] = useState([])
  const [trafficGenerated, setTrafficGenerated] = useState(false)

  // Generate demo traffic with hot and cold keys
  const generateDemoTraffic = async () => {
    setGeneratingTraffic(true)
    try {
      const leader = nodes.find(n => n.state === 'LEADER' && n.active) || nodes[0]
      const baseUrl = `http://localhost:${leader.port}`

      // Step 1: Create the COLD key FIRST (so it has older access time)
      await axios.post(`${baseUrl}/cache/temp-upload`, {
        value: 'temporary-file-data',
        clientId: 'demo',
        sequenceNumber: Date.now()
      })

      // Small delay to ensure time difference
      await new Promise(r => setTimeout(r, 500))

      // Step 2: Create hot keys
      const hotKeys = [
        { key: 'user-session', value: 'active-user-12345' },
        { key: 'api-token', value: 'tok_secret_abc' },
        { key: 'config', value: 'production-settings' }
      ]

      for (const { key, value } of hotKeys) {
        await axios.post(`${baseUrl}/cache/${key}`, {
          value,
          clientId: 'demo',
          sequenceNumber: Date.now()
        })
      }

      // Step 3: Generate traffic - access hot keys many times
      // Access hot keys 20 times each to create clear difference
      for (let i = 0; i < 20; i++) {
        for (const { key } of hotKeys) {
          await axios.get(`${baseUrl}/cache/${key}`)
        }
      }

      // temp-upload stays at 1 access (from creation) while hot keys have 20+

      setTrafficGenerated(true)
    } catch (e) {
      console.error("Failed to generate demo traffic", e)
    } finally {
      setGeneratingTraffic(false)
    }
  }

  const getPrediction = async () => {
    setLoading(true)
    try {
      // 1. Get stats from Leader (since traffic hits leader)
      const leader = nodes.find(n => n.state === 'LEADER' && n.active) || nodes[0]
      const statsRes = await axios.get(`http://localhost:${leader.port}/cache/access-stats`)
      const stats = statsRes.data.stats

      if (!stats || stats.length === 0) {
        setPrediction(null)
        setAllPredictions([])
        setLoading(false)
        console.warn("No access stats available for prediction")
        return
      }

      // 2. Format for ML Service
      const keys = stats.map(item => ({
        key: item.key,
        access_count: item.totalAccessCount,
        last_access_ms: item.lastAccessTime,
        access_count_hour: item.accessCountHour,
        access_count_day: item.accessCountDay,
        avg_interval_ms: 0
      }))

      const payload = {
          keys: keys,
          currentTime: Date.now()
      }

      // 3. Call ML Service
      const mlRes = await axios.post('http://localhost:5001/predict', payload)

      // Sort by probability (lowest first = best eviction candidates)
      const predictions = mlRes.data.predictions.sort((a, b) => a.probability - b.probability)

      // Store all predictions for display
      setAllPredictions(predictions)

      // Set the top eviction candidate
      const evictionCandidate = predictions[0]

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

  // Get color based on probability
  const getProbabilityColor = (prob) => {
    if (prob < 0.3) return 'var(--error)'      // Low probability = eviction candidate (red)
    if (prob < 0.6) return 'var(--warning)'    // Medium probability (yellow)
    return 'var(--accent-green)'               // High probability = keep (green)
  }

  return (
    <div>
      {/* Generate Demo Traffic Button */}
      <div style={{ marginBottom: '1rem' }}>
        <button
          className="btn"
          onClick={generateDemoTraffic}
          disabled={generatingTraffic}
          style={{
            width: '100%',
            justifyContent: 'center',
            background: trafficGenerated ? 'rgba(0, 255, 157, 0.2)' : 'rgba(0, 240, 255, 0.2)',
            border: `1px solid ${trafficGenerated ? 'var(--accent-green)' : 'var(--accent-blue)'}`,
            marginBottom: '0.5rem'
          }}
        >
          {generatingTraffic ? (
            <><Loader size={16} className="spinning" /> Generating Traffic...</>
          ) : trafficGenerated ? (
            <><Zap size={16} /> Traffic Generated - Ready to Analyze</>
          ) : (
            <><Zap size={16} /> Generate Demo Traffic</>
          )}
        </button>
        <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', textAlign: 'center', margin: 0 }}>
          Creates hot keys (frequently accessed) and cold keys (rarely accessed)
        </p>
      </div>

      {/* Analyze Button */}
      <div style={{ marginBottom: '1rem' }}>
        <button
          className="btn"
          onClick={getPrediction}
          disabled={loading}
          style={{
            width: '100%',
            justifyContent: 'center',
            background: 'var(--accent-purple)',
            border: 'none'
          }}
        >
          <Brain size={16} /> {loading ? 'Analyzing...' : 'Analyze Eviction Candidates'}
        </button>
      </div>

      {/* Results */}
      {allPredictions.length > 0 ? (
        <div>
          {/* Top Eviction Candidate - only show if there's a truly cold key */}
          {prediction && prediction.probability < 0.5 && (
            <div style={{
              background: 'rgba(255, 77, 77, 0.1)',
              borderRadius: '8px',
              padding: '1rem',
              border: '1px solid var(--error)',
              marginBottom: '1rem'
            }}>
              <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: '0.5rem'
              }}>
                <span style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
                  <TrendingDown size={14} style={{ marginRight: '0.25rem', verticalAlign: 'middle' }} />
                  Recommended Eviction
                </span>
                <span style={{
                  color: 'var(--error)',
                  fontWeight: 'bold',
                  fontSize: '0.9rem'
                }}>
                  Only {Math.round(prediction.probability * 100)}% likely to be accessed
                </span>
              </div>
              <div style={{ fontWeight: 'bold', color: 'var(--error)', fontSize: '1.3rem' }}>
                {prediction.key}
              </div>
            </div>
          )}

          {/* Show message when all keys are hot */}
          {prediction && prediction.probability >= 0.5 && (
            <div style={{
              background: 'rgba(0, 255, 157, 0.1)',
              borderRadius: '8px',
              padding: '1rem',
              border: '1px solid var(--accent-green)',
              marginBottom: '1rem',
              textAlign: 'center'
            }}>
              <span style={{ color: 'var(--accent-green)', fontWeight: 'bold' }}>
                All keys are actively used - no eviction recommended
              </span>
            </div>
          )}

          {/* All Keys with Probability Bars */}
          <div style={{
            background: 'rgba(255, 255, 255, 0.03)',
            borderRadius: '8px',
            padding: '1rem',
            border: '1px solid var(--border-color)'
          }}>
            <div style={{
              fontSize: '0.85rem',
              color: 'var(--text-secondary)',
              marginBottom: '0.75rem',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem'
            }}>
              <Brain size={14} />
              All Keys - Access Probability
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {allPredictions.map((pred, idx) => {
                const probPercent = Math.round(pred.probability * 100)
                const color = getProbabilityColor(pred.probability)

                // Determine badge based on probability thresholds (mutually exclusive)
                const isCold = pred.probability < 0.3
                const isWarm = pred.probability >= 0.3 && pred.probability < 0.7
                const isHot = pred.probability >= 0.7
                const isLowest = idx === 0

                return (
                  <div key={pred.key}>
                    <div style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      marginBottom: '0.25rem'
                    }}>
                      <span style={{
                        fontWeight: isCold || isLowest ? 'bold' : 'normal',
                        color: isCold ? 'var(--error)' : 'var(--text-primary)',
                        fontSize: '0.9rem',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '0.5rem'
                      }}>
                        {pred.key}
                        {isCold && (
                          <span style={{
                            fontSize: '0.65rem',
                            background: 'var(--error)',
                            color: '#000',
                            padding: '0.1rem 0.4rem',
                            borderRadius: '4px',
                            fontWeight: 'bold'
                          }}>
                            COLD
                          </span>
                        )}
                        {isWarm && (
                          <span style={{
                            fontSize: '0.65rem',
                            background: 'var(--warning)',
                            color: '#000',
                            padding: '0.1rem 0.4rem',
                            borderRadius: '4px',
                            fontWeight: 'bold'
                          }}>
                            WARM
                          </span>
                        )}
                        {isHot && (
                          <span style={{
                            fontSize: '0.65rem',
                            background: 'var(--accent-green)',
                            color: '#000',
                            padding: '0.1rem 0.4rem',
                            borderRadius: '4px',
                            fontWeight: 'bold'
                          }}>
                            HOT
                          </span>
                        )}
                      </span>
                      <span style={{
                        color: color,
                        fontWeight: 'bold',
                        fontSize: '0.85rem',
                        fontFamily: 'monospace'
                      }}>
                        {probPercent}%
                      </span>
                    </div>

                    {/* Probability Bar */}
                    <div style={{
                      height: '8px',
                      background: 'var(--border-color)',
                      borderRadius: '4px',
                      overflow: 'hidden'
                    }}>
                      <div style={{
                        height: '100%',
                        width: `${probPercent}%`,
                        background: color,
                        borderRadius: '4px',
                        transition: 'width 0.3s ease'
                      }} />
                    </div>

                    {/* Debug info */}
                    {pred.debug && (
                      <div style={{
                        fontSize: '0.7rem',
                        color: 'var(--text-secondary)',
                        marginTop: '0.25rem',
                        display: 'flex',
                        gap: '0.75rem'
                      }}>
                        <span>Accesses: {pred.debug.accessCount}</span>
                        <span>Recency: {pred.debug.recency.toFixed(0)}</span>
                        <span>Frequency: {pred.debug.frequency.toFixed(0)}</span>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          </div>

          {/* Legend */}
          <div style={{
            marginTop: '0.75rem',
            fontSize: '0.75rem',
            color: 'var(--text-secondary)',
            display: 'flex',
            justifyContent: 'center',
            gap: '1rem'
          }}>
            <span><span style={{ color: 'var(--error)' }}>●</span> Cold (Evict)</span>
            <span><span style={{ color: 'var(--warning)' }}>●</span> Warm</span>
            <span><span style={{ color: 'var(--accent-green)' }}>●</span> Hot (Keep)</span>
          </div>
        </div>
      ) : (
        <div style={{ textAlign: 'center', color: 'var(--text-secondary)', padding: '1.5rem 0', fontSize: '0.9rem' }}>
          <p style={{ marginBottom: '0.5rem' }}>
            {trafficGenerated
              ? 'Traffic generated! Click "Analyze" to see ML predictions.'
              : 'Generate demo traffic first, then analyze.'}
          </p>
          <span style={{ fontSize: '0.8rem', opacity: 0.7 }}>
            The ML model identifies "cold" data based on access patterns.
          </span>
        </div>
      )}
    </div>
  )
}

export default MLStats
