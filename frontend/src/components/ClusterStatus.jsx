import { useState, useEffect, useRef } from 'react'
import { Crown, Server, Power, PlayCircle, Activity, Database } from 'lucide-react'
import axios from 'axios'

const ClusterStatus = ({ nodes }) => {
  const [actionInProgress, setActionInProgress] = useState(null)
  const [nodeCaches, setNodeCaches] = useState({})
  const [heartbeatPulse, setHeartbeatPulse] = useState(false)
  const [heartbeatCount, setHeartbeatCount] = useState(0)
  const keyFirstSeen = useRef({}) // Track when we first saw each key

  const leader = nodes.find(n => n.state === 'LEADER' && n.active)

  // Heartbeat animation - smooth pulse
  useEffect(() => {
    if (!leader) {
      setHeartbeatPulse(false)
      setHeartbeatCount(0)
      return
    }

    const heartbeatInterval = setInterval(() => {
      setHeartbeatPulse(true)
      setHeartbeatCount(prev => prev + 1)
      setTimeout(() => setHeartbeatPulse(false), 600)
    }, 2000)

    return () => clearInterval(heartbeatInterval)
  }, [leader?.id])

  // Poll cache data from each node - get access stats from leader for sorting
  useEffect(() => {
    const nodeConfigs = [
      { id: 'node1', port: 8081 },
      { id: 'node2', port: 8082 },
      { id: 'node3', port: 8083 }
    ]

    const fetchCaches = async () => {
      const caches = {}

      // First, try to get access stats from the leader (they track all accesses)
      let leaderStats = []
      for (const node of nodeConfigs) {
        try {
          const statsRes = await axios.get(`http://localhost:${node.port}/cache/access-stats`)
          if (statsRes.data.stats && statsRes.data.stats.length > 0) {
            leaderStats = statsRes.data.stats
            break // Found stats, use them
          }
        } catch (e) {
          // Continue to next node
        }
      }

      // Create a map of key -> lastAccessTime from leader stats
      const accessTimes = {}
      leaderStats.forEach(s => {
        accessTimes[s.key] = s.lastAccessTime || 0
      })

      const now = Date.now()

      // Now fetch cache data from each node
      for (const node of nodeConfigs) {
        try {
          const cacheRes = await axios.get(`http://localhost:${node.port}/cache/all`)
          const data = cacheRes.data.data || {}

          // Track when we first see each key (for sorting when no access stats)
          Object.keys(data).forEach(key => {
            if (!keyFirstSeen.current[key]) {
              keyFirstSeen.current[key] = now
            }
          })

          // Get entries and sort by lastAccessTime (or first-seen time as fallback)
          const entries = Object.entries(data)
            .map(([key, value]) => ({
              key,
              value,
              // Use access time if available, otherwise use first-seen time
              sortTime: accessTimes[key] || keyFirstSeen.current[key] || 0
            }))
            .sort((a, b) => b.sortTime - a.sortTime) // Most recent first
            .slice(0, 3) // Take top 3
            .map(e => [e.key, e.value]) // Convert back to [key, value] format

          caches[node.id] = entries
        } catch (e) {
          caches[node.id] = []
        }
      }
      setNodeCaches(caches)
    }

    fetchCaches()
    const interval = setInterval(fetchCaches, 2000)
    return () => clearInterval(interval)
  }, []) // Empty deps - stable polling

  const killNode = async (node) => {
    setActionInProgress(node.id)
    try {
      await axios.post(`http://localhost:5002/kill/${node.id}`)
    } catch (e) {
      console.error('Failed to kill node:', e)
    } finally {
      setActionInProgress(null)
    }
  }

  const startNode = async (nodeId) => {
    setActionInProgress(nodeId)
    try {
      await axios.post(`http://localhost:5002/start/${nodeId}`)
    } catch (e) {
      console.error('Failed to start node:', e)
    } finally {
      setActionInProgress(null)
    }
  }

  const renderNodeCard = (node) => {
    const isLeader = node.state === 'LEADER'
    const isFollower = node.state === 'FOLLOWER' && node.active
    const isDown = !node.active || node.state === 'DOWN' || node.state === 'UNKNOWN'
    const cacheEntries = nodeCaches[node.id] || []

    return (
      <div
        key={node.id}
        className="card cluster-node-card"
        style={{
          borderColor: isLeader ? 'var(--accent-blue)' : isDown ? 'var(--error)' : 'var(--border-color)',
          opacity: isDown ? 0.7 : 1,
          position: 'relative',
          overflow: 'hidden',
          padding: '1rem',
          boxShadow: isFollower && heartbeatPulse ? '0 0 20px rgba(0, 255, 157, 0.3)' : 'none',
          transition: 'box-shadow 0.3s ease-out'
        }}
      >
        {/* Pulse ring effect for followers receiving heartbeat */}
        {isFollower && heartbeatPulse && (
          <div style={{
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            borderRadius: '12px',
            border: '2px solid var(--accent-green)',
            animation: 'pulse-ring 0.6s ease-out forwards',
            pointerEvents: 'none'
          }} />
        )}
        {isLeader && (
          <>
            <div style={{
              position: 'absolute',
              top: 0,
              right: 0,
              background: 'var(--accent-blue)',
              color: '#000',
              padding: '0.2rem 0.5rem',
              borderBottomLeftRadius: '6px',
              fontWeight: 'bold',
              fontSize: '0.65rem',
              display: 'flex',
              alignItems: 'center',
              gap: '0.25rem'
            }}>
              <Crown size={10} /> LEADER
            </div>
            {/* Expanding pulse ring for heartbeat broadcast */}
            {heartbeatPulse && (
              <>
                <div style={{
                  position: 'absolute',
                  top: '50%',
                  left: '50%',
                  transform: 'translate(-50%, -50%)',
                  width: '100%',
                  height: '100%',
                  borderRadius: '12px',
                  border: '2px solid var(--accent-blue)',
                  animation: 'leader-pulse-ring 0.8s ease-out forwards',
                  pointerEvents: 'none'
                }} />
                <div style={{
                  position: 'absolute',
                  top: '50%',
                  left: '50%',
                  transform: 'translate(-50%, -50%)',
                  width: '100%',
                  height: '100%',
                  borderRadius: '12px',
                  border: '2px solid var(--accent-blue)',
                  animation: 'leader-pulse-ring 0.8s ease-out 0.15s forwards',
                  pointerEvents: 'none'
                }} />
              </>
            )}
          </>
        )}

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.75rem' }}>
          <div style={{
            background: isDown ? 'rgba(255, 77, 77, 0.1)' : 'rgba(255, 255, 255, 0.05)',
            padding: '0.5rem',
            borderRadius: '50%',
            color: isDown ? 'var(--error)' : 'var(--text-primary)'
          }}>
            <Server size={18} />
          </div>
          <div>
            <h3 style={{ margin: 0, fontSize: '0.95rem' }}>{node.id}</h3>
            <span style={{
              fontSize: '0.7rem',
              color: isDown ? 'var(--error)' : 'var(--text-secondary)'
            }}>
              :{node.port}
            </span>
          </div>
        </div>

        <div style={{ display: 'grid', gap: '0.4rem', fontSize: '0.8rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--text-secondary)' }}>Term</span>
            <span style={{ fontFamily: 'monospace', color: 'var(--accent-purple)' }}>{node.currentTerm || node.term}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--text-secondary)' }}>Log</span>
            <span style={{ fontFamily: 'monospace', color: 'var(--accent-green)' }}>{node.commitIndex || 0}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--text-secondary)' }}>Role</span>
            <span className={
              node.state === 'LEADER' ? 'badge badge-leader' :
              node.state === 'CANDIDATE' ? 'badge badge-candidate' :
              'badge badge-follower'
            } style={{ fontSize: '0.65rem', padding: '0.15rem 0.4rem' }}>
              {node.state}
            </span>
          </div>
        </div>

        {/* Recent Cache Entries */}
        <div style={{
          marginTop: '0.75rem',
          paddingTop: '0.5rem',
          borderTop: '1px solid var(--border-color)'
        }}>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.25rem',
            marginBottom: '0.4rem',
            fontSize: '0.7rem',
            color: 'var(--text-secondary)'
          }}>
            <Database size={10} />
            <span>Recent Cache</span>
          </div>
          {isDown ? (
            <div style={{
              fontSize: '0.7rem',
              color: 'var(--error)',
              fontStyle: 'italic'
            }}>
              Node offline
            </div>
          ) : cacheEntries.length === 0 ? (
            <div style={{
              fontSize: '0.7rem',
              color: 'var(--text-secondary)',
              fontStyle: 'italic'
            }}>
              No entries
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
              {cacheEntries.map(([key, value]) => (
                <div
                  key={key}
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    background: 'rgba(0, 255, 157, 0.05)',
                    padding: '0.25rem 0.4rem',
                    borderRadius: '4px',
                    fontSize: '0.65rem',
                    fontFamily: 'monospace'
                  }}
                >
                  <span style={{
                    color: 'var(--accent-blue)',
                    maxWidth: '45%',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap'
                  }}>
                    {key}
                  </span>
                  <span style={{ color: 'var(--text-secondary)' }}>=</span>
                  <span style={{
                    color: 'var(--accent-green)',
                    maxWidth: '45%',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap'
                  }}>
                    {typeof value === 'string' && value.length > 12
                      ? value.substring(0, 12) + '...'
                      : value}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Kill/Start Node Button */}
        <div style={{ marginTop: '0.75rem', paddingTop: '0.5rem', borderTop: '1px solid var(--border-color)' }}>
          {!isDown ? (
            <button
              onClick={() => killNode(node)}
              disabled={actionInProgress === node.id}
              style={{
                width: '100%',
                padding: '0.4rem',
                background: 'rgba(255, 77, 77, 0.1)',
                border: '1px solid var(--error)',
                borderRadius: '4px',
                color: 'var(--error)',
                cursor: actionInProgress === node.id ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '0.4rem',
                fontSize: '0.75rem',
                opacity: actionInProgress === node.id ? 0.5 : 1
              }}
            >
              <Power size={12} />
              {actionInProgress === node.id ? 'Killing...' : 'Kill'}
            </button>
          ) : (
            <button
              onClick={() => startNode(node.id)}
              disabled={actionInProgress === node.id}
              style={{
                width: '100%',
                padding: '0.4rem',
                background: 'rgba(0, 255, 157, 0.1)',
                border: '1px solid var(--accent-green)',
                borderRadius: '4px',
                color: 'var(--accent-green)',
                cursor: actionInProgress === node.id ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '0.4rem',
                fontSize: '0.75rem',
                opacity: actionInProgress === node.id ? 0.5 : 1
              }}
            >
              <PlayCircle size={12} />
              {actionInProgress === node.id ? 'Starting...' : 'Start'}
            </button>
          )}
        </div>
      </div>
    )
  }

  const activeNodes = nodes.filter(n => n.active && n.state !== 'DOWN' && n.state !== 'UNKNOWN')
  const downNodes = nodes.filter(n => !n.active || n.state === 'DOWN' || n.state === 'UNKNOWN')
  const currentLeader = nodes.find(n => n.state === 'LEADER')

  return (
    <div className="cluster-status">
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: '0.75rem'
      }}>
        <h2 style={{
          fontSize: '0.85rem',
          color: 'var(--text-secondary)',
          margin: 0,
          display: 'flex',
          alignItems: 'center',
          gap: '0.5rem',
          textTransform: 'uppercase',
          letterSpacing: '0.05em'
        }}>
          <Activity size={14} /> Cluster Status
        </h2>
        <div style={{ display: 'flex', gap: '1rem', fontSize: '0.75rem', alignItems: 'center' }}>
          <span style={{ color: 'var(--accent-green)' }}>
            <strong>{activeNodes.length}</strong> Active
          </span>
          <span style={{ color: 'var(--error)' }}>
            <strong>{downNodes.length}</strong> Down
          </span>
          <span style={{ color: 'var(--accent-purple)' }}>
            Term <strong>{currentLeader?.currentTerm || currentLeader?.term || '-'}</strong>
          </span>
          {currentLeader && (
            <span style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.4rem',
              color: 'var(--accent-blue)',
              padding: '0.25rem 0.6rem',
              background: 'rgba(0, 240, 255, 0.1)',
              borderRadius: '12px',
              border: '1px solid rgba(0, 240, 255, 0.2)'
            }}>
              <div style={{
                width: '8px',
                height: '8px',
                borderRadius: '50%',
                background: heartbeatPulse ? 'var(--accent-blue)' : 'rgba(0, 240, 255, 0.4)',
                boxShadow: heartbeatPulse ? '0 0 8px var(--accent-blue)' : 'none',
                transition: 'all 0.2s ease-out'
              }} />
              <span style={{ fontSize: '0.7rem' }}>HB</span>
              <strong>{heartbeatCount}</strong>
            </span>
          )}
        </div>
      </div>
      <div className="cluster-nodes-grid">
        {nodes.map(node => renderNodeCard(node))}
      </div>
    </div>
  )
}

export default ClusterStatus
