import React, { useState } from 'react'
import { Brain, Clock, Scale, Database, Zap, CheckCircle, XCircle, RotateCcw, Loader } from 'lucide-react'
import axios from 'axios'

const EvictionDemo = ({ nodes }) => {
  const [loading, setLoading] = useState(false)
  const [accessCounts, setAccessCounts] = useState({})
  const [comparison, setComparison] = useState(null)
  const [keysInserted, setKeysInserted] = useState(false)
  const [eventLog, setEventLog] = useState([])

  const leader = nodes.find(n => n.state === 'LEADER' && n.active) || nodes[0]
  const baseUrl = leader ? `http://localhost:${leader.port}` : null

  const addLog = (message, type = 'info') => {
    setEventLog(prev => [...prev.slice(-9), { message, type, time: new Date().toLocaleTimeString() }])
  }

  // Fetch comparison data after any action
  const fetchComparison = async () => {
    if (!baseUrl) return

    try {
      const statsRes = await axios.get(`${baseUrl}/cache/access-stats`)
      const stats = statsRes.data.stats

      // Filter to only our demo keys
      const demoKeys = ['key1', 'key2', 'key3']
      const demoStats = stats.filter(s => demoKeys.includes(s.key))

      if (demoStats.length === 0) {
        setComparison(null)
        return
      }

      // Update access counts display
      const counts = {}
      demoStats.forEach(s => {
        counts[s.key] = s.totalAccessCount
      })
      setAccessCounts(counts)

      // LRU: Would evict the key with oldest lastAccessTime
      const sortedByLRU = [...demoStats].sort((a, b) => a.lastAccessTime - b.lastAccessTime)
      const lruEviction = sortedByLRU[0]

      // ML Prediction
      const mlPayload = {
        keys: demoStats.map(item => ({
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

      setComparison({
        stats: demoStats.map(s => ({
          key: s.key,
          accessCount: s.totalAccessCount,
          mlProbability: predictions.find(p => p.key === s.key)?.probability || 0
        })).sort((a, b) => b.accessCount - a.accessCount),
        lru: {
          wouldEvict: lruEviction.key,
          accessCount: lruEviction.totalAccessCount
        },
        ml: {
          wouldEvict: mlEviction.key,
          probability: mlEviction.probability
        }
      })
    } catch (e) {
      console.error('Failed to fetch comparison:', e)
    }
  }

  // Button 1: Insert 3 keys
  const insertKeys = async () => {
    if (!baseUrl) return
    setLoading(true)

    try {
      const keys = [
        { key: 'key1', value: 'product-catalog-data' },
        { key: 'key2', value: 'user-session-info' },
        { key: 'key3', value: 'temp-upload-token' }
      ]

      for (const { key, value } of keys) {
        await axios.post(`${baseUrl}/cache/${key}`, {
          value,
          clientId: 'eviction-demo',
          sequenceNumber: Date.now()
        })
      }

      setKeysInserted(true)
      setAccessCounts({ key1: 1, key2: 1, key3: 1 })
      addLog('Inserted key1, key2, key3', 'success')

      await fetchComparison()
    } catch (e) {
      addLog(`Error: ${e.message}`, 'error')
    } finally {
      setLoading(false)
    }
  }

  // Button 2: Simulate accesses (50x key1, 20x key2, 1x key3 most recent)
  const simulateAccesses = async () => {
    if (!baseUrl) return
    setLoading(true)

    try {
      addLog('Accessing key1 50 times...', 'info')
      for (let i = 0; i < 50; i++) {
        await axios.get(`${baseUrl}/cache/key1`)
      }
      addLog('key1: 50 accesses complete', 'success')

      addLog('Accessing key2 20 times...', 'info')
      for (let i = 0; i < 20; i++) {
        await axios.get(`${baseUrl}/cache/key2`)
      }
      addLog('key2: 20 accesses complete', 'success')

      // Small delay to ensure timestamp difference
      await new Promise(r => setTimeout(r, 500))

      addLog('Accessing key3 1 time (most recent)...', 'info')
      await axios.get(`${baseUrl}/cache/key3`)
      addLog('key3: 1 access (MOST RECENT)', 'warning')

      await fetchComparison()
    } catch (e) {
      addLog(`Error: ${e.message}`, 'error')
    } finally {
      setLoading(false)
    }
  }

  const reset = async () => {
    setKeysInserted(false)
    setAccessCounts({})
    setComparison(null)
    setEventLog([])
  }

  return (
    <div className="card" style={{ background: 'linear-gradient(135deg, rgba(138,43,226,0.03) 0%, rgba(0,240,255,0.03) 100%)' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '1rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <Scale size={24} color="var(--accent-purple)" />
          <h2 style={{ margin: 0, fontSize: '1.5rem' }}>LRU vs ML Eviction Demo</h2>
        </div>
        {keysInserted && (
          <button className="btn" onClick={reset} style={{ background: 'transparent', border: '1px solid var(--border-color)' }}>
            <RotateCcw size={16} /> Reset
          </button>
        )}
      </div>

      {/* Two Action Buttons */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem', marginBottom: '1.5rem' }}>
        <button
          onClick={insertKeys}
          disabled={loading || keysInserted}
          style={{
            padding: '1.25rem',
            background: keysInserted ? 'rgba(0, 255, 157, 0.1)' : 'rgba(0, 240, 255, 0.1)',
            border: `2px solid ${keysInserted ? 'var(--accent-green)' : 'var(--accent-blue)'}`,
            borderRadius: '12px',
            cursor: loading || keysInserted ? 'not-allowed' : 'pointer',
            opacity: loading || keysInserted ? 0.7 : 1,
            transition: 'all 0.3s'
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.75rem' }}>
            {loading ? <Loader size={20} className="spinning" /> : keysInserted ? <CheckCircle size={20} color="var(--accent-green)" /> : <Database size={20} color="var(--accent-blue)" />}
            <span style={{ fontWeight: 'bold', fontSize: '1rem', color: keysInserted ? 'var(--accent-green)' : 'var(--text-primary)' }}>
              {keysInserted ? 'Keys Inserted' : 'Insert 3 Keys'}
            </span>
          </div>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: '0.5rem' }}>
            key1, key2, key3
          </div>
        </button>

        <button
          onClick={simulateAccesses}
          disabled={loading || !keysInserted}
          style={{
            padding: '1.25rem',
            background: !keysInserted ? 'rgba(255,255,255,0.03)' : 'rgba(255, 184, 0, 0.1)',
            border: `2px solid ${!keysInserted ? 'var(--border-color)' : 'var(--warning)'}`,
            borderRadius: '12px',
            cursor: loading || !keysInserted ? 'not-allowed' : 'pointer',
            opacity: !keysInserted ? 0.5 : loading ? 0.7 : 1,
            transition: 'all 0.3s'
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.75rem' }}>
            {loading ? <Loader size={20} className="spinning" /> : <Zap size={20} color={keysInserted ? 'var(--warning)' : 'var(--text-secondary)'} />}
            <span style={{ fontWeight: 'bold', fontSize: '1rem', color: keysInserted ? 'var(--text-primary)' : 'var(--text-secondary)' }}>
              Simulate Accesses
            </span>
          </div>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: '0.5rem' }}>
            50x key1, 20x key2, 1x key3 (most recent)
          </div>
        </button>
      </div>

      {/* Comparison Results */}
      {comparison && (
        <div style={{ marginBottom: '1.5rem' }}>
          {/* Access Counts */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, 1fr)',
            gap: '1rem',
            marginBottom: '1.5rem'
          }}>
            {comparison.stats.map(stat => {
              const isHot = stat.mlProbability >= 0.7
              const isWarm = stat.mlProbability >= 0.3 && stat.mlProbability < 0.7
              const isCold = stat.mlProbability < 0.3
              return (
                <div key={stat.key} style={{
                  background: 'rgba(255,255,255,0.03)',
                  borderRadius: '12px',
                  padding: '1rem',
                  border: `1px solid ${isCold ? 'var(--error)' : isWarm ? 'var(--warning)' : 'var(--accent-green)'}`,
                  textAlign: 'center'
                }}>
                  <div style={{ fontFamily: 'monospace', fontWeight: 'bold', fontSize: '1.1rem', color: 'var(--accent-blue)', marginBottom: '0.5rem' }}>
                    {stat.key}
                  </div>
                  <div style={{ fontSize: '2rem', fontWeight: 'bold', color: 'var(--text-primary)' }}>
                    {stat.accessCount}x
                  </div>
                  <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '0.5rem' }}>
                    accesses
                  </div>
                  <span style={{
                    background: isHot ? 'var(--accent-green)' : isWarm ? 'var(--warning)' : 'var(--error)',
                    color: '#000',
                    padding: '0.2rem 0.6rem',
                    borderRadius: '4px',
                    fontSize: '0.75rem',
                    fontWeight: 'bold'
                  }}>
                    {isHot ? 'HOT' : isWarm ? 'WARM' : 'COLD'} ({Math.round(stat.mlProbability * 100)}%)
                  </span>
                </div>
              )
            })}
          </div>

          {/* LRU vs ML Comparison */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            {/* LRU */}
            <div style={{
              background: comparison.lru.wouldEvict === 'key3' ? 'rgba(0, 255, 157, 0.1)' : 'rgba(255, 77, 77, 0.1)',
              borderRadius: '12px',
              padding: '1.25rem',
              border: `2px solid ${comparison.lru.wouldEvict === 'key3' ? 'var(--accent-green)' : 'var(--error)'}`
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
                <Clock size={24} color="var(--warning)" />
                <span style={{ fontWeight: 'bold', fontSize: '1.2rem' }}>LRU Strategy</span>
                {comparison.lru.wouldEvict === 'key3' ? (
                  <CheckCircle size={20} color="var(--accent-green)" style={{ marginLeft: 'auto' }} />
                ) : (
                  <XCircle size={20} color="var(--error)" style={{ marginLeft: 'auto' }} />
                )}
              </div>
              <div style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', marginBottom: '0.75rem' }}>
                Evicts: oldest access timestamp
              </div>
              <div style={{
                background: 'rgba(0,0,0,0.3)',
                borderRadius: '8px',
                padding: '1rem',
                textAlign: 'center'
              }}>
                <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '0.25rem' }}>Would evict:</div>
                <div style={{
                  fontSize: '1.5rem',
                  fontWeight: 'bold',
                  color: comparison.lru.wouldEvict === 'key3' ? 'var(--accent-green)' : 'var(--error)',
                  fontFamily: 'monospace'
                }}>
                  {comparison.lru.wouldEvict}
                </div>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: '0.25rem' }}>
                  ({comparison.lru.accessCount}x accessed)
                </div>
              </div>
              {comparison.lru.wouldEvict !== 'key3' && (
                <div style={{ marginTop: '0.75rem', fontSize: '0.85rem', color: 'var(--error)', textAlign: 'center' }}>
                  Would evict frequently-used data!
                </div>
              )}
            </div>

            {/* ML */}
            <div style={{
              background: comparison.ml.wouldEvict === 'key3' ? 'rgba(0, 255, 157, 0.1)' : 'rgba(255, 77, 77, 0.1)',
              borderRadius: '12px',
              padding: '1.25rem',
              border: `2px solid ${comparison.ml.wouldEvict === 'key3' ? 'var(--accent-green)' : 'var(--error)'}`
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
                <Brain size={24} color="var(--accent-purple)" />
                <span style={{ fontWeight: 'bold', fontSize: '1.2rem' }}>ML Strategy</span>
                {comparison.ml.wouldEvict === 'key3' ? (
                  <CheckCircle size={20} color="var(--accent-green)" style={{ marginLeft: 'auto' }} />
                ) : (
                  <XCircle size={20} color="var(--error)" style={{ marginLeft: 'auto' }} />
                )}
              </div>
              <div style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', marginBottom: '0.75rem' }}>
                Evicts: lowest access probability
              </div>
              <div style={{
                background: 'rgba(0,0,0,0.3)',
                borderRadius: '8px',
                padding: '1rem',
                textAlign: 'center'
              }}>
                <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '0.25rem' }}>Would evict:</div>
                <div style={{
                  fontSize: '1.5rem',
                  fontWeight: 'bold',
                  color: comparison.ml.wouldEvict === 'key3' ? 'var(--accent-green)' : 'var(--error)',
                  fontFamily: 'monospace'
                }}>
                  {comparison.ml.wouldEvict}
                </div>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: '0.25rem' }}>
                  ({Math.round(comparison.ml.probability * 100)}% future access prob)
                </div>
              </div>
              {comparison.ml.wouldEvict === 'key3' && (
                <div style={{ marginTop: '0.75rem', fontSize: '0.85rem', color: 'var(--accent-green)', textAlign: 'center' }}>
                  Correctly identified cold data!
                </div>
              )}
            </div>
          </div>

          {/* Verdict */}
          {comparison.ml.wouldEvict === 'key3' && comparison.lru.wouldEvict !== 'key3' && (
            <div style={{
              marginTop: '1rem',
              background: 'linear-gradient(135deg, rgba(138,43,226,0.2) 0%, rgba(0,240,255,0.2) 100%)',
              borderRadius: '12px',
              padding: '1.25rem',
              border: '1px solid var(--accent-purple)',
              textAlign: 'center'
            }}>
              <div style={{ fontSize: '1.25rem', fontWeight: 'bold', color: 'var(--accent-purple)', marginBottom: '0.5rem' }}>
                ML Made the Smarter Decision!
              </div>
              <div style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
                LRU would evict <strong>{comparison.lru.wouldEvict}</strong> ({comparison.lru.accessCount}x accessed) just because it's "least recent".
                ML correctly identified <strong>key3</strong> as the truly cold key.
              </div>
            </div>
          )}
        </div>
      )}

      {/* Event Log */}
      {eventLog.length > 0 && (
        <div style={{
          background: 'rgba(0, 0, 0, 0.3)',
          borderRadius: '8px',
          padding: '0.75rem',
          maxHeight: '120px',
          overflowY: 'auto',
          fontFamily: 'monospace',
          fontSize: '0.8rem'
        }}>
          {eventLog.map((log, i) => (
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

      {/* Initial state message */}
      {!keysInserted && (
        <div style={{ textAlign: 'center', padding: '2rem 1rem', color: 'var(--text-secondary)' }}>
          <Database size={32} style={{ marginBottom: '0.5rem', opacity: 0.5 }} />
          <div style={{ fontSize: '1rem' }}>Click "Insert 3 Keys" to begin</div>
        </div>
      )}
    </div>
  )
}

export default EvictionDemo
