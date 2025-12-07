import React, { useState, useEffect, useRef } from 'react'
import { Database, RefreshCw, Trash2 } from 'lucide-react'
import axios from 'axios'

const CacheView = ({ nodes }) => {
  const [cacheData, setCacheData] = useState([])
  const [loading, setLoading] = useState(false)
  const keyTimestamps = useRef({})

  const fetchCache = async () => {
    setLoading(true)
    try {
      // Try leader first, then any active node
      const leader = nodes.find(n => n.state === 'LEADER' && n.active)
      const activeNode = leader || nodes.find(n => n.active) || nodes[0]
      const res = await axios.get(`http://localhost:${activeNode.port}/cache/all`)
      const now = Date.now()

      // Convert object to array of {key, value}
      const entries = Object.entries(res.data.data || {})
        .map(([key, value]) => {
          // Track when we first saw each key
          if (!keyTimestamps.current[key]) {
            keyTimestamps.current[key] = now
          }
          return { key, value, timestamp: keyTimestamps.current[key] }
        })
        // Sort by timestamp descending (most recent first)
        .sort((a, b) => b.timestamp - a.timestamp)

      setCacheData(entries)
    } catch (e) {
      console.error("Failed to fetch cache", e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchCache()
    const interval = setInterval(fetchCache, 2000)  // Poll every 2 seconds
    return () => clearInterval(interval)
  }, [])

  return (
    <div className="card" style={{ height: '100%' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '1rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <Database size={20} color="var(--accent-blue)" />
          <h2 style={{ margin: 0, fontSize: '1.25rem' }}>Cache Contents</h2>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginLeft: '0.5rem' }}>
            ({cacheData.length} keys)
          </span>
        </div>
        <button
          className="btn"
          onClick={fetchCache}
          disabled={loading}
          style={{ padding: '0.25rem 0.5rem', fontSize: '0.8rem' }}
        >
          <RefreshCw size={14} className={loading ? 'spinning' : ''} />
        </button>
      </div>

      <div style={{
        maxHeight: '200px',
        overflowY: 'auto',
        display: 'flex',
        flexDirection: 'column',
        gap: '0.25rem'
      }}>
        {cacheData.length === 0 ? (
          <div style={{ color: 'var(--text-secondary)', fontStyle: 'italic', textAlign: 'center', padding: '1rem' }}>
            Cache is empty
          </div>
        ) : (
          cacheData.map(({ key, value }) => (
            <div
              key={key}
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                padding: '0.5rem',
                background: 'rgba(255,255,255,0.03)',
                borderRadius: '4px',
                fontFamily: 'monospace',
                fontSize: '0.85rem'
              }}
            >
              <span style={{ color: 'var(--accent-green)' }}>{key}</span>
              <span style={{ color: 'var(--text-secondary)', maxWidth: '60%', overflow: 'hidden', textOverflow: 'ellipsis' }}>{value}</span>
            </div>
          ))
        )}
      </div>
    </div>
  )
}

export default CacheView
