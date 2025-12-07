import React, { useState } from 'react'
import { Scale, Brain, Clock, CheckCircle, XCircle, Play, RotateCcw } from 'lucide-react'
import axios from 'axios'

const EvictionComparison = ({ nodes }) => {
  const [running, setRunning] = useState(false)
  const [results, setResults] = useState(null)
  const [step, setStep] = useState(0)
  const [stepLog, setStepLog] = useState([])

  const addLog = (message, type = 'info') => {
    setStepLog(prev => [...prev, { message, type, time: new Date().toLocaleTimeString() }])
  }

  const runComparison = async () => {
    setRunning(true)
    setResults(null)
    setStepLog([])
    setStep(1)

    try {
      const leader = nodes.find(n => n.state === 'LEADER' && n.active) || nodes[0]
      const baseUrl = `http://localhost:${leader.port}`

      // Step 1: Clear and setup
      addLog('Setting up comparison scenario...', 'info')

      // Create keys with specific access patterns
      const keys = ['config-data', 'user-session', 'temp-file']

      for (const key of keys) {
        await axios.post(`${baseUrl}/cache/${key}`, {
          value: `value-for-${key}`,
          clientId: 'comparison-demo',
          sequenceNumber: Date.now()
        })
      }
      addLog('Created 3 cache keys: config-data, user-session, temp-file', 'success')

      // Step 2: Generate access patterns
      setStep(2)
      addLog('Generating access patterns...', 'info')

      // Access config-data many times (frequently used)
      for (let i = 0; i < 50; i++) {
        await axios.get(`${baseUrl}/cache/config-data`)
      }
      addLog('config-data: Accessed 50 times (frequently used)', 'success')

      // Access user-session moderately
      for (let i = 0; i < 20; i++) {
        await axios.get(`${baseUrl}/cache/user-session`)
      }
      addLog('user-session: Accessed 20 times (moderately used)', 'success')

      // Access temp-file just once (rarely used)
      await axios.get(`${baseUrl}/cache/temp-file`)
      addLog('temp-file: Accessed 1 time (rarely used)', 'success')

      // Step 3: Wait a moment so config-data becomes "older"
      setStep(3)
      addLog('Waiting 2 seconds to age the access times...', 'info')
      await new Promise(r => setTimeout(r, 2000))

      // Access temp-file once more to make it "most recent" for LRU
      await axios.get(`${baseUrl}/cache/temp-file`)
      addLog('Accessed temp-file again - now it has the most recent timestamp', 'warning')

      // Step 4: Get stats and predictions
      setStep(4)
      addLog('Analyzing eviction strategies...', 'info')

      const statsRes = await axios.get(`${baseUrl}/cache/access-stats`)
      const stats = statsRes.data.stats

      // Calculate what LRU would evict (oldest lastAccessTime)
      const sortedByLRU = [...stats].sort((a, b) => a.lastAccessTime - b.lastAccessTime)
      const lruEviction = sortedByLRU[0]

      // Get ML prediction
      const mlPayload = {
        keys: stats.map(item => ({
          key: item.key,
          access_count: item.totalAccessCount,
          last_access_ms: item.lastAccessTime,
          access_count_hour: item.accessCountHour,
          access_count_day: item.accessCountDay,
          avg_interval_ms: 0
        })),
        currentTime: Date.now()
      }

      const mlRes = await axios.post('http://localhost:5001/predict', mlPayload)
      const predictions = mlRes.data.predictions.sort((a, b) => a.probability - b.probability)
      const mlEviction = predictions[0]

      // Step 5: Show results
      setStep(5)

      const finalResults = {
        stats: stats.map(s => ({
          key: s.key,
          accessCount: s.totalAccessCount,
          lastAccess: s.lastAccessTime,
          mlProbability: predictions.find(p => p.key === s.key)?.probability || 0
        })),
        lru: {
          wouldEvict: lruEviction.key,
          reason: 'Oldest access timestamp',
          correct: lruEviction.key === 'temp-file'
        },
        ml: {
          wouldEvict: mlEviction.key,
          probability: mlEviction.probability,
          reason: 'Lowest access probability (considers frequency)',
          correct: mlEviction.key === 'temp-file'
        }
      }

      setResults(finalResults)

      if (finalResults.lru.wouldEvict !== finalResults.ml.wouldEvict) {
        addLog(`LRU would evict "${finalResults.lru.wouldEvict}" - potentially wrong!`, 'error')
        addLog(`ML would evict "${finalResults.ml.wouldEvict}" - considers frequency!`, 'success')
      } else {
        addLog(`Both strategies would evict "${finalResults.ml.wouldEvict}"`, 'success')
      }

    } catch (e) {
      addLog(`Error: ${e.message}`, 'error')
      console.error(e)
    } finally {
      setRunning(false)
    }
  }

  const reset = () => {
    setResults(null)
    setStep(0)
    setStepLog([])
  }

  return (
    <div className="card">
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.5rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '1rem' }}>
        <Scale size={20} color="var(--accent-blue)" />
        <h2 style={{ margin: 0, fontSize: '1.25rem' }}>LRU vs ML Eviction Comparison</h2>
      </div>

      {/* Explanation */}
      <div style={{
        background: 'rgba(0, 240, 255, 0.05)',
        borderRadius: '8px',
        padding: '1rem',
        marginBottom: '1rem',
        border: '1px solid rgba(0, 240, 255, 0.2)'
      }}>
        <p style={{ margin: 0, fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
          <strong style={{ color: 'var(--accent-blue)' }}>The Problem with LRU:</strong> LRU evicts based only on "least recently used".
          A frequently-accessed key that hasn't been touched for a few seconds gets evicted over a rarely-used key that was just accessed.
        </p>
        <p style={{ margin: '0.5rem 0 0 0', fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
          <strong style={{ color: 'var(--accent-purple)' }}>ML Advantage:</strong> Considers access frequency, recency, AND patterns to make smarter eviction decisions.
        </p>
      </div>

      {/* Control Buttons */}
      <div style={{ display: 'flex', gap: '1rem', marginBottom: '1rem' }}>
        <button
          className="btn"
          onClick={runComparison}
          disabled={running}
          style={{
            flex: 1,
            justifyContent: 'center',
            background: 'var(--accent-blue)',
            border: 'none'
          }}
        >
          <Play size={16} /> {running ? 'Running Comparison...' : 'Run Comparison Demo'}
        </button>
        {results && (
          <button className="btn" onClick={reset} style={{ background: 'transparent' }}>
            <RotateCcw size={16} /> Reset
          </button>
        )}
      </div>

      {/* Progress Steps */}
      {step > 0 && (
        <div style={{ marginBottom: '1rem' }}>
          <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.5rem' }}>
            {[1, 2, 3, 4, 5].map(s => (
              <div key={s} style={{
                flex: 1,
                height: '4px',
                borderRadius: '2px',
                background: s <= step ? 'var(--accent-blue)' : 'var(--border-color)',
                transition: 'background 0.3s'
              }} />
            ))}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Step {step}/5: {
              step === 1 ? 'Setting up keys' :
              step === 2 ? 'Generating access patterns' :
              step === 3 ? 'Aging timestamps' :
              step === 4 ? 'Analyzing strategies' :
              'Complete!'
            }
          </div>
        </div>
      )}

      {/* Step Log */}
      {stepLog.length > 0 && (
        <div style={{
          background: 'rgba(0, 0, 0, 0.3)',
          borderRadius: '8px',
          padding: '0.75rem',
          marginBottom: '1rem',
          maxHeight: '150px',
          overflowY: 'auto',
          fontFamily: 'monospace',
          fontSize: '0.8rem'
        }}>
          {stepLog.map((log, i) => (
            <div key={i} style={{
              color: log.type === 'error' ? 'var(--error)' :
                     log.type === 'success' ? 'var(--accent-green)' :
                     log.type === 'warning' ? 'var(--warning)' :
                     'var(--text-secondary)',
              marginBottom: '0.25rem'
            }}>
              <span style={{ opacity: 0.5 }}>[{log.time}]</span> {log.message}
            </div>
          ))}
        </div>
      )}

      {/* Results */}
      {results && (
        <div>
          {/* Access Stats Table */}
          <div style={{
            background: 'rgba(255, 255, 255, 0.03)',
            borderRadius: '8px',
            padding: '1rem',
            marginBottom: '1rem'
          }}>
            <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '0.75rem' }}>
              Key Access Statistics
            </div>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--border-color)' }}>
                  <th style={{ textAlign: 'left', padding: '0.5rem' }}>Key</th>
                  <th style={{ textAlign: 'center', padding: '0.5rem' }}>Access Count</th>
                  <th style={{ textAlign: 'center', padding: '0.5rem' }}>ML Probability</th>
                </tr>
              </thead>
              <tbody>
                {results.stats.map(stat => (
                  <tr key={stat.key} style={{ borderBottom: '1px solid var(--border-color)' }}>
                    <td style={{ padding: '0.5rem', fontWeight: 'bold' }}>{stat.key}</td>
                    <td style={{ textAlign: 'center', padding: '0.5rem' }}>{stat.accessCount}x</td>
                    <td style={{ textAlign: 'center', padding: '0.5rem' }}>
                      <span style={{
                        color: stat.mlProbability < 0.3 ? 'var(--error)' :
                               stat.mlProbability < 0.6 ? 'var(--warning)' :
                               'var(--accent-green)'
                      }}>
                        {Math.round(stat.mlProbability * 100)}%
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Comparison Cards */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            {/* LRU Result */}
            <div style={{
              background: results.lru.correct ? 'rgba(0, 255, 157, 0.1)' : 'rgba(255, 77, 77, 0.1)',
              borderRadius: '8px',
              padding: '1rem',
              border: `1px solid ${results.lru.correct ? 'var(--accent-green)' : 'var(--error)'}`
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
                <Clock size={18} color="var(--warning)" />
                <span style={{ fontWeight: 'bold' }}>LRU Strategy</span>
                {results.lru.correct ? (
                  <CheckCircle size={16} color="var(--accent-green)" style={{ marginLeft: 'auto' }} />
                ) : (
                  <XCircle size={16} color="var(--error)" style={{ marginLeft: 'auto' }} />
                )}
              </div>
              <div style={{ fontSize: '1.1rem', fontWeight: 'bold', marginBottom: '0.25rem' }}>
                Would evict: <span style={{ color: results.lru.correct ? 'var(--accent-green)' : 'var(--error)' }}>
                  {results.lru.wouldEvict}
                </span>
              </div>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                {results.lru.reason}
              </div>
            </div>

            {/* ML Result */}
            <div style={{
              background: results.ml.correct ? 'rgba(0, 255, 157, 0.1)' : 'rgba(255, 77, 77, 0.1)',
              borderRadius: '8px',
              padding: '1rem',
              border: `1px solid ${results.ml.correct ? 'var(--accent-green)' : 'var(--error)'}`
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
                <Brain size={18} color="var(--accent-purple)" />
                <span style={{ fontWeight: 'bold' }}>ML Strategy</span>
                {results.ml.correct ? (
                  <CheckCircle size={16} color="var(--accent-green)" style={{ marginLeft: 'auto' }} />
                ) : (
                  <XCircle size={16} color="var(--error)" style={{ marginLeft: 'auto' }} />
                )}
              </div>
              <div style={{ fontSize: '1.1rem', fontWeight: 'bold', marginBottom: '0.25rem' }}>
                Would evict: <span style={{ color: results.ml.correct ? 'var(--accent-green)' : 'var(--error)' }}>
                  {results.ml.wouldEvict}
                </span>
                <span style={{ fontSize: '0.85rem', fontWeight: 'normal', marginLeft: '0.5rem' }}>
                  ({Math.round(results.ml.probability * 100)}% prob)
                </span>
              </div>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                {results.ml.reason}
              </div>
            </div>
          </div>

          {/* Verdict */}
          {results.lru.wouldEvict !== results.ml.wouldEvict && (
            <div style={{
              marginTop: '1rem',
              background: 'rgba(138, 43, 226, 0.1)',
              borderRadius: '8px',
              padding: '1rem',
              border: '1px solid var(--accent-purple)',
              textAlign: 'center'
            }}>
              <div style={{ fontWeight: 'bold', color: 'var(--accent-purple)', marginBottom: '0.5rem' }}>
                ML Made a Smarter Decision!
              </div>
              <div style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
                LRU would have evicted <strong>"{results.lru.wouldEvict}"</strong> (accessed {results.stats.find(s => s.key === results.lru.wouldEvict)?.accessCount}x)
                just because it wasn't accessed in the last few seconds.
                <br />
                ML correctly identified <strong>"{results.ml.wouldEvict}"</strong> as the true cold key based on access patterns.
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default EvictionComparison
