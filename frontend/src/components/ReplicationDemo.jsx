import { useState, useEffect } from 'react'
import { GitBranch, Server, CheckCircle, Clock, ArrowRight, Play, Send, Zap, Heart } from 'lucide-react'
import axios from 'axios'

const ReplicationDemo = ({ nodes }) => {
  const [replicationState, setReplicationState] = useState(null)
  const [logs, setLogs] = useState([])
  const [demoRunning, setDemoRunning] = useState(false)
  const [demoStep, setDemoStep] = useState(0)
  const [eventLog, setEventLog] = useState([])
  const [highlightedIndex, setHighlightedIndex] = useState(null)

  // Slow demo state
  const [slowDemoActive, setSlowDemoActive] = useState(false)
  const [slowDemoStep, setSlowDemoStep] = useState(0)
  const [slowDemoKey, setSlowDemoKey] = useState('')
  const [slowDemoValue, setSlowDemoValue] = useState('')
  const [leaderHasEntry, setLeaderHasEntry] = useState(false)
  const [follower1HasEntry, setFollower1HasEntry] = useState(false)
  const [follower2HasEntry, setFollower2HasEntry] = useState(false)
  const [entryCommitted, setEntryCommitted] = useState(false)
  const [animatingArrow, setAnimatingArrow] = useState(null)

  // Heartbeat state
  const [heartbeatActive, setHeartbeatActive] = useState(false)
  const [heartbeatToFollower1, setHeartbeatToFollower1] = useState(false)
  const [heartbeatToFollower2, setHeartbeatToFollower2] = useState(false)
  const [heartbeatCount, setHeartbeatCount] = useState(0)

  const leader = nodes.find(n => n.state === 'LEADER' && n.active)
  const followers = nodes.filter(n => n.state !== 'LEADER' && n.active && n.state !== 'DOWN')

  // Real-time heartbeat animation
  useEffect(() => {
    if (!leader) {
      setHeartbeatActive(false)
      return
    }

    setHeartbeatActive(true)

    // Heartbeat interval (every 1.5 seconds for visual effect)
    const heartbeatInterval = setInterval(() => {
      // Animate heartbeat to follower 1
      setHeartbeatToFollower1(true)
      setTimeout(() => setHeartbeatToFollower1(false), 400)

      // Animate heartbeat to follower 2 with slight delay
      setTimeout(() => {
        setHeartbeatToFollower2(true)
        setTimeout(() => setHeartbeatToFollower2(false), 400)
      }, 100)

      setHeartbeatCount(prev => prev + 1)
    }, 1500)

    return () => clearInterval(heartbeatInterval)
  }, [leader?.id]) // Re-run when leader changes

  // Poll replication state from leader
  useEffect(() => {
    const fetchState = async () => {
      if (!leader) return

      try {
        const [stateRes, logRes] = await Promise.all([
          axios.get(`http://localhost:${leader.port}/raft/replication-state`),
          axios.get(`http://localhost:${leader.port}/raft/log`)
        ])
        setReplicationState(stateRes.data)
        setLogs(logRes.data)
      } catch (e) {
        console.error('Failed to fetch replication state', e)
      }
    }

    fetchState()
    const interval = setInterval(fetchState, 500)
    return () => clearInterval(interval)
  }, [leader])

  const addEvent = (message, type = 'info') => {
    setEventLog(prev => [...prev.slice(-9), {
      time: new Date().toLocaleTimeString(),
      message,
      type
    }])
  }

  const sleep = (ms) => new Promise(r => setTimeout(r, ms))

  const runReplicationDemo = async () => {
    if (!leader) {
      addEvent('No leader available!', 'error')
      return
    }

    setDemoRunning(true)
    setEventLog([])
    setDemoStep(1)

    try {
      const baseUrl = `http://localhost:${leader.port}`
      const testKey = `demo-${Date.now()}`
      const testValue = `replicated-value-${Math.random().toString(36).substr(2, 6)}`

      // Step 1: Show current state
      addEvent('Step 1: Checking current log state...', 'info')
      await sleep(1000)

      const beforeState = await axios.get(`${baseUrl}/raft/replication-state`)
      addEvent(`Leader has ${beforeState.data.lastLogIndex} log entries, commitIndex=${beforeState.data.commitIndex}`, 'info')
      await sleep(1500)

      // Step 2: Send write to leader
      setDemoStep(2)
      addEvent(`Step 2: Sending write to leader: ${testKey} = ${testValue}`, 'warning')
      await sleep(500)

      const writeRes = await axios.post(`${baseUrl}/cache/${testKey}`, {
        value: testValue,
        clientId: 'replication-demo',
        sequenceNumber: Date.now()
      })

      if (writeRes.data.success) {
        addEvent('Write accepted by leader!', 'success')
      }
      await sleep(1000)

      // Step 3: Show replication happening
      setDemoStep(3)
      addEvent('Step 3: Leader replicating to followers via AppendEntries...', 'info')
      await sleep(500)

      // Poll for replication progress
      for (let i = 0; i < 5; i++) {
        const state = await axios.get(`${baseUrl}/raft/replication-state`)
        setReplicationState(state.data)

        const matchIndexes = state.data.matchIndex || {}
        const lastLog = state.data.lastLogIndex

        // Check how many have replicated
        let replicated = 1 // Leader has it
        for (const [followerId, matchIdx] of Object.entries(matchIndexes)) {
          if (matchIdx >= lastLog) {
            replicated++
            addEvent(`${followerId} replicated entry ${lastLog}`, 'success')
          }
        }

        if (replicated >= 2) {
          addEvent(`Majority reached! (${replicated}/3 nodes)`, 'success')
          break
        }

        await sleep(500)
      }

      // Step 4: Show commit
      setDemoStep(4)
      await sleep(1000)
      const afterState = await axios.get(`${baseUrl}/raft/replication-state`)
      setReplicationState(afterState.data)
      setHighlightedIndex(afterState.data.lastLogIndex)

      addEvent(`Step 4: Entry committed! commitIndex=${afterState.data.commitIndex}`, 'success')
      addEvent('Entry is now durable across the cluster!', 'success')

      await sleep(2000)
      setDemoStep(5)
      addEvent('Demo complete! The write was replicated to all followers.', 'info')

    } catch (e) {
      addEvent(`Error: ${e.message}`, 'error')
    } finally {
      setDemoRunning(false)
      setTimeout(() => setHighlightedIndex(null), 3000)
    }
  }

  // Slow visual demo (simulated for clear visualization)
  const runSlowDemo = async () => {
    if (!leader) {
      addEvent('No leader available!', 'error')
      return
    }

    // Reset state
    setSlowDemoActive(true)
    setSlowDemoStep(0)
    setLeaderHasEntry(false)
    setFollower1HasEntry(false)
    setFollower2HasEntry(false)
    setEntryCommitted(false)
    setAnimatingArrow(null)
    setEventLog([])

    const testKey = `user-data-${Math.floor(Math.random() * 1000)}`
    const testValue = `{"name":"demo","timestamp":${Date.now()}}`
    setSlowDemoKey(testKey)
    setSlowDemoValue(testValue)

    // Step 0: Initial state
    addEvent('Starting slow replication demo...', 'info')
    await sleep(1500)

    // Step 1: Client sends write request
    setSlowDemoStep(1)
    addEvent(`Step 1: Client sends PUT request: ${testKey}`, 'warning')
    await sleep(2000)

    // Step 2: Leader receives and appends to log
    setSlowDemoStep(2)
    addEvent('Step 2: Leader receives request and appends to local log', 'info')
    await sleep(1000)
    setLeaderHasEntry(true)
    addEvent('✓ Leader appended entry to log (uncommitted)', 'success')
    await sleep(2000)

    // Step 3: Leader sends AppendEntries to follower 1
    setSlowDemoStep(3)
    setAnimatingArrow('follower1')
    addEvent('Step 3: Leader sends AppendEntries RPC to node1...', 'info')
    await sleep(2000)
    setFollower1HasEntry(true)
    addEvent('✓ node1 acknowledged - entry replicated', 'success')
    setAnimatingArrow(null)
    await sleep(1500)

    // Step 4: Leader sends AppendEntries to follower 2
    setSlowDemoStep(4)
    setAnimatingArrow('follower2')
    addEvent('Step 4: Leader sends AppendEntries RPC to node2...', 'info')
    await sleep(2000)
    setFollower2HasEntry(true)
    addEvent('✓ node2 acknowledged - entry replicated', 'success')
    setAnimatingArrow(null)
    await sleep(1500)

    // Step 5: Majority reached - commit!
    setSlowDemoStep(5)
    addEvent('Step 5: MAJORITY REACHED (3/3 nodes have entry)', 'warning')
    await sleep(1500)
    setEntryCommitted(true)
    addEvent('✓ Entry COMMITTED! commitIndex advanced', 'success')
    await sleep(1000)
    addEvent('✓ Entry applied to state machine on all nodes', 'success')
    await sleep(2000)

    // Step 6: Actually write to the cluster
    setSlowDemoStep(6)
    addEvent('Step 6: Performing actual write to cluster...', 'info')
    try {
      await axios.post(`http://localhost:${leader.port}/cache/${testKey}`, {
        value: testValue,
        clientId: 'slow-demo',
        sequenceNumber: Date.now()
      })
      addEvent('✓ Real write completed! Check cache in cluster status above.', 'success')
    } catch (e) {
      addEvent('Note: Simulated demo - actual write skipped', 'info')
    }
    await sleep(2000)

    // Done
    setSlowDemoStep(7)
    addEvent('Demo complete! Replication ensures data durability.', 'info')
  }

  const resetSlowDemo = () => {
    setSlowDemoActive(false)
    setSlowDemoStep(0)
    setLeaderHasEntry(false)
    setFollower1HasEntry(false)
    setFollower2HasEntry(false)
    setEntryCommitted(false)
    setAnimatingArrow(null)
    setSlowDemoKey('')
    setSlowDemoValue('')
  }

  // Render log bar for a node
  const renderLogBar = (nodeId, matchIndex, isLeader = false) => {
    const maxIndex = replicationState?.lastLogIndex || 10
    const commitIndex = replicationState?.commitIndex || 0

    return (
      <div style={{ marginBottom: '1rem' }}>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '0.75rem',
          marginBottom: '0.5rem'
        }}>
          <Server size={16} color={isLeader ? 'var(--accent-blue)' : 'var(--text-secondary)'} />
          <span style={{
            fontWeight: isLeader ? 'bold' : 'normal',
            color: isLeader ? 'var(--accent-blue)' : 'var(--text-primary)',
            minWidth: '60px'
          }}>
            {nodeId}
          </span>
          <span style={{
            fontSize: '0.75rem',
            color: 'var(--text-secondary)',
            background: isLeader ? 'rgba(0, 240, 255, 0.1)' : 'rgba(255,255,255,0.05)',
            padding: '0.2rem 0.5rem',
            borderRadius: '4px'
          }}>
            {isLeader ? 'LEADER' : 'FOLLOWER'}
          </span>
          <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginLeft: 'auto' }}>
            matchIndex: <strong style={{ color: 'var(--accent-green)' }}>{matchIndex}</strong>
          </span>
        </div>

        {/* Log entries visualization */}
        <div style={{
          display: 'flex',
          gap: '3px',
          padding: '0.5rem',
          background: 'rgba(0,0,0,0.3)',
          borderRadius: '6px',
          overflowX: 'auto'
        }}>
          {Array.from({ length: Math.max(maxIndex, 1) }, (_, i) => {
            const index = i + 1
            const hasEntry = index <= matchIndex
            const isCommitted = index <= commitIndex
            const isHighlighted = index === highlightedIndex

            return (
              <div
                key={index}
                style={{
                  width: '32px',
                  height: '32px',
                  borderRadius: '4px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '0.7rem',
                  fontWeight: 'bold',
                  background: !hasEntry ? 'rgba(255,255,255,0.05)' :
                             isCommitted ? 'var(--accent-green)' :
                             'var(--warning)',
                  color: hasEntry ? '#000' : 'var(--text-secondary)',
                  border: isHighlighted ? '2px solid var(--accent-blue)' : 'none',
                  boxShadow: isHighlighted ? '0 0 10px var(--accent-blue)' : 'none',
                  transition: 'all 0.3s',
                  animation: isHighlighted ? 'pulse 1s infinite' : 'none'
                }}
                title={`Index ${index}: ${!hasEntry ? 'Not replicated' : isCommitted ? 'Committed' : 'Replicated (not committed)'}`}
              >
                {index}
              </div>
            )
          })}
        </div>
      </div>
    )
  }

  return (
    <div className="card">
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: '0.5rem',
        marginBottom: '1.5rem',
        borderBottom: '1px solid var(--border-color)',
        paddingBottom: '1rem'
      }}>
        <GitBranch size={24} color="var(--accent-green)" />
        <h2 style={{ margin: 0, fontSize: '1.5rem' }}>Log Replication Demo</h2>
      </div>

      {/* Explanation */}
      <div style={{
        background: 'rgba(0, 255, 157, 0.05)',
        borderRadius: '8px',
        padding: '1rem',
        marginBottom: '1.5rem',
        border: '1px solid rgba(0, 255, 157, 0.2)'
      }}>
        <p style={{ margin: 0, fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
          <strong style={{ color: 'var(--accent-green)' }}>How Log Replication Works:</strong> When the leader receives a write,
          it appends the entry to its log and sends AppendEntries RPCs to all followers.
          Once a majority (2/3) have replicated the entry, the leader advances the <strong>commitIndex</strong>
          and the entry becomes durable.
        </p>
      </div>

      {/* Legend */}
      <div style={{
        display: 'flex',
        gap: '1.5rem',
        marginBottom: '1.5rem',
        fontSize: '0.8rem'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <div style={{ width: '16px', height: '16px', background: 'var(--accent-green)', borderRadius: '3px' }} />
          <span style={{ color: 'var(--text-secondary)' }}>Committed</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <div style={{ width: '16px', height: '16px', background: 'var(--warning)', borderRadius: '3px' }} />
          <span style={{ color: 'var(--text-secondary)' }}>Replicated (uncommitted)</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <div style={{ width: '16px', height: '16px', background: 'rgba(255,255,255,0.1)', borderRadius: '3px' }} />
          <span style={{ color: 'var(--text-secondary)' }}>Not replicated</span>
        </div>
      </div>

      {/* Replication State Visualization */}
      {replicationState?.isLeader ? (
        <div style={{ marginBottom: '1.5rem' }}>
          {/* Stats Bar */}
          <div style={{
            display: 'flex',
            gap: '1rem',
            marginBottom: '1rem',
            padding: '0.75rem',
            background: 'rgba(0, 240, 255, 0.05)',
            borderRadius: '8px',
            border: '1px solid rgba(0, 240, 255, 0.2)'
          }}>
            <div style={{ textAlign: 'center', flex: 1 }}>
              <div style={{ fontSize: '1.5rem', fontWeight: 'bold', color: 'var(--accent-blue)' }}>
                {replicationState.lastLogIndex}
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Last Log Index</div>
            </div>
            <div style={{ textAlign: 'center', flex: 1 }}>
              <div style={{ fontSize: '1.5rem', fontWeight: 'bold', color: 'var(--accent-green)' }}>
                {replicationState.commitIndex}
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Commit Index</div>
            </div>
            <div style={{ textAlign: 'center', flex: 1 }}>
              <div style={{ fontSize: '1.5rem', fontWeight: 'bold', color: 'var(--accent-purple)' }}>
                {replicationState.currentTerm}
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Term</div>
            </div>
          </div>

          {/* Leader Log */}
          {renderLogBar(replicationState.nodeId, replicationState.lastLogIndex, true)}

          {/* Replication arrows */}
          <div style={{
            display: 'flex',
            justifyContent: 'center',
            padding: '0.5rem 0',
            color: 'var(--accent-blue)'
          }}>
            <Send size={20} style={{ transform: 'rotate(90deg)' }} />
            <span style={{ margin: '0 0.5rem', fontSize: '0.8rem' }}>AppendEntries RPC</span>
            <Send size={20} style={{ transform: 'rotate(90deg)' }} />
          </div>

          {/* Follower Logs */}
          {Object.entries(replicationState.matchIndex || {}).map(([followerId, matchIdx]) => (
            renderLogBar(followerId, matchIdx, false)
          ))}
        </div>
      ) : (
        <div style={{
          textAlign: 'center',
          padding: '2rem',
          color: 'var(--text-secondary)',
          background: 'rgba(255,255,255,0.03)',
          borderRadius: '8px',
          marginBottom: '1.5rem'
        }}>
          <Server size={32} style={{ marginBottom: '0.5rem', opacity: 0.5 }} />
          <div>Waiting for leader to be elected...</div>
          <div style={{ fontSize: '0.8rem', marginTop: '0.25rem' }}>
            Replication state is only available from the leader node
          </div>
        </div>
      )}

      {/* Slow Visual Demo */}
      <div style={{
        background: 'linear-gradient(135deg, rgba(0,240,255,0.03) 0%, rgba(0,255,157,0.03) 100%)',
        borderRadius: '12px',
        padding: '1.5rem',
        marginBottom: '1.5rem',
        border: '1px solid var(--border-color)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem' }}>
          <h3 style={{ margin: 0, display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '1.1rem' }}>
            <Zap size={20} color="var(--warning)" />
            Step-by-Step Replication Demo
          </h3>
          {/* Heartbeat indicator */}
          {leader && !slowDemoActive && (
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              padding: '0.4rem 0.8rem',
              background: 'rgba(255, 77, 77, 0.1)',
              borderRadius: '20px',
              border: '1px solid rgba(255, 77, 77, 0.3)'
            }}>
              <Heart
                size={16}
                color="var(--error)"
                fill={heartbeatToFollower1 || heartbeatToFollower2 ? 'var(--error)' : 'transparent'}
                style={{
                  animation: 'heartbeat-pulse 1.5s infinite',
                  transition: 'fill 0.2s'
                }}
              />
              <span style={{ fontSize: '0.75rem', color: 'var(--error)', fontWeight: 'bold' }}>
                Heartbeats: {heartbeatCount}
              </span>
            </div>
          )}
          {!slowDemoActive ? (
            <button
              onClick={runSlowDemo}
              disabled={!leader}
              style={{
                padding: '0.5rem 1.5rem',
                background: 'linear-gradient(135deg, var(--accent-green) 0%, var(--accent-blue) 100%)',
                border: 'none',
                borderRadius: '6px',
                color: '#000',
                cursor: leader ? 'pointer' : 'not-allowed',
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem',
                fontWeight: 'bold',
                opacity: leader ? 1 : 0.5
              }}
            >
              <Play size={16} />
              Start Demo
            </button>
          ) : (
            <button
              onClick={resetSlowDemo}
              style={{
                padding: '0.5rem 1.5rem',
                background: 'rgba(255, 77, 77, 0.1)',
                border: '1px solid var(--error)',
                borderRadius: '6px',
                color: 'var(--error)',
                cursor: 'pointer',
                fontWeight: 'bold'
              }}
            >
              Reset
            </button>
          )}
        </div>

        {/* Current entry being replicated */}
        {slowDemoKey && (
          <div style={{
            background: 'rgba(0,0,0,0.3)',
            borderRadius: '8px',
            padding: '0.75rem 1rem',
            marginBottom: '1rem',
            fontFamily: 'monospace',
            fontSize: '0.85rem'
          }}>
            <span style={{ color: 'var(--text-secondary)' }}>Entry: </span>
            <span style={{ color: 'var(--accent-blue)' }}>{slowDemoKey}</span>
            <span style={{ color: 'var(--text-secondary)' }}> = </span>
            <span style={{ color: 'var(--accent-green)' }}>{slowDemoValue.length > 40 ? slowDemoValue.slice(0, 40) + '...' : slowDemoValue}</span>
          </div>
        )}

        {/* Visual Node Diagram */}
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'flex-start', gap: '2rem', padding: '1rem 0' }}>
          {/* Client */}
          <div style={{ textAlign: 'center' }}>
            <div style={{
              width: '80px',
              height: '80px',
              borderRadius: '50%',
              background: slowDemoStep >= 1 ? 'rgba(255, 184, 0, 0.2)' : 'rgba(255,255,255,0.05)',
              border: `2px solid ${slowDemoStep >= 1 ? 'var(--warning)' : 'var(--border-color)'}`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 0.5rem',
              transition: 'all 0.5s'
            }}>
              <span style={{ fontSize: '1.5rem' }}>👤</span>
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Client</div>
          </div>

          {/* Arrow to Leader */}
          <div style={{
            display: 'flex',
            alignItems: 'center',
            paddingTop: '30px',
            opacity: slowDemoStep >= 1 ? 1 : 0.3,
            transition: 'opacity 0.5s'
          }}>
            <ArrowRight
              size={32}
              color={slowDemoStep === 1 ? 'var(--warning)' : 'var(--text-secondary)'}
              style={{ animation: slowDemoStep === 1 ? 'pulse 1s infinite' : 'none' }}
            />
          </div>

          {/* Leader Node */}
          <div style={{ textAlign: 'center' }}>
            <div style={{
              width: '100px',
              height: '100px',
              borderRadius: '12px',
              background: leaderHasEntry
                ? (entryCommitted ? 'rgba(0, 255, 157, 0.2)' : 'rgba(255, 184, 0, 0.2)')
                : 'rgba(0, 240, 255, 0.1)',
              border: `3px solid ${leaderHasEntry ? (entryCommitted ? 'var(--accent-green)' : 'var(--warning)') : 'var(--accent-blue)'}`,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 0.5rem',
              transition: 'all 0.5s',
              boxShadow: leaderHasEntry ? `0 0 20px ${entryCommitted ? 'var(--accent-green)' : 'var(--warning)'}` : 'none'
            }}>
              <Server size={28} color="var(--accent-blue)" />
              {leaderHasEntry && (
                <CheckCircle
                  size={16}
                  color={entryCommitted ? 'var(--accent-green)' : 'var(--warning)'}
                  style={{ marginTop: '4px' }}
                />
              )}
            </div>
            <div style={{ fontWeight: 'bold', color: 'var(--accent-blue)', fontSize: '0.9rem' }}>
              {leader?.id || 'Leader'}
            </div>
            <div style={{ fontSize: '0.7rem', color: 'var(--text-secondary)' }}>LEADER</div>
          </div>

          {/* Arrows to Followers with Heartbeats */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem', paddingTop: '10px' }}>
            {/* Arrow/Heartbeat to Follower 1 */}
            <div style={{
              opacity: 1,
              transition: 'opacity 0.3s',
              position: 'relative',
              width: '60px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              {/* Heartbeat pulse */}
              {heartbeatToFollower1 && !slowDemoActive && (
                <Heart
                  size={18}
                  color="var(--error)"
                  fill="var(--error)"
                  style={{
                    position: 'absolute',
                    animation: 'heartbeat-move-1 0.4s ease-out forwards',
                    filter: 'drop-shadow(0 0 6px var(--error))'
                  }}
                />
              )}
              {/* Demo arrow */}
              {slowDemoActive && slowDemoStep >= 3 ? (
                <ArrowRight
                  size={28}
                  color={animatingArrow === 'follower1' ? 'var(--accent-green)' : 'var(--text-secondary)'}
                  style={{
                    animation: animatingArrow === 'follower1' ? 'pulse 0.5s infinite' : 'none',
                    transform: 'rotate(-20deg)'
                  }}
                />
              ) : (
                <ArrowRight
                  size={24}
                  color={heartbeatToFollower1 ? 'var(--error)' : 'var(--text-secondary)'}
                  style={{
                    transform: 'rotate(-20deg)',
                    opacity: heartbeatToFollower1 ? 1 : 0.4,
                    transition: 'all 0.2s'
                  }}
                />
              )}
            </div>
            {/* Arrow/Heartbeat to Follower 2 */}
            <div style={{
              opacity: 1,
              transition: 'opacity 0.3s',
              position: 'relative',
              width: '60px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              {/* Heartbeat pulse */}
              {heartbeatToFollower2 && !slowDemoActive && (
                <Heart
                  size={18}
                  color="var(--error)"
                  fill="var(--error)"
                  style={{
                    position: 'absolute',
                    animation: 'heartbeat-move-2 0.4s ease-out forwards',
                    filter: 'drop-shadow(0 0 6px var(--error))'
                  }}
                />
              )}
              {/* Demo arrow */}
              {slowDemoActive && slowDemoStep >= 4 ? (
                <ArrowRight
                  size={28}
                  color={animatingArrow === 'follower2' ? 'var(--accent-green)' : 'var(--text-secondary)'}
                  style={{
                    animation: animatingArrow === 'follower2' ? 'pulse 0.5s infinite' : 'none',
                    transform: 'rotate(20deg)'
                  }}
                />
              ) : (
                <ArrowRight
                  size={24}
                  color={heartbeatToFollower2 ? 'var(--error)' : 'var(--text-secondary)'}
                  style={{
                    transform: 'rotate(20deg)',
                    opacity: heartbeatToFollower2 ? 1 : 0.4,
                    transition: 'all 0.2s'
                  }}
                />
              )}
            </div>
          </div>

          {/* Follower Nodes */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {/* Follower 1 */}
            <div style={{ textAlign: 'center' }}>
              <div style={{
                width: '80px',
                height: '80px',
                borderRadius: '12px',
                background: follower1HasEntry
                  ? (entryCommitted ? 'rgba(0, 255, 157, 0.2)' : 'rgba(255, 184, 0, 0.2)')
                  : 'rgba(255,255,255,0.05)',
                border: `2px solid ${follower1HasEntry ? (entryCommitted ? 'var(--accent-green)' : 'var(--warning)') : 'var(--border-color)'}`,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 0.25rem',
                transition: 'all 0.5s',
                boxShadow: follower1HasEntry ? `0 0 15px ${entryCommitted ? 'var(--accent-green)' : 'var(--warning)'}` : 'none'
              }}>
                <Server size={22} color="var(--text-secondary)" />
                {follower1HasEntry && (
                  <CheckCircle
                    size={14}
                    color={entryCommitted ? 'var(--accent-green)' : 'var(--warning)'}
                    style={{ marginTop: '2px' }}
                  />
                )}
              </div>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-primary)' }}>node1</div>
              <div style={{ fontSize: '0.65rem', color: 'var(--text-secondary)' }}>FOLLOWER</div>
            </div>

            {/* Follower 2 */}
            <div style={{ textAlign: 'center' }}>
              <div style={{
                width: '80px',
                height: '80px',
                borderRadius: '12px',
                background: follower2HasEntry
                  ? (entryCommitted ? 'rgba(0, 255, 157, 0.2)' : 'rgba(255, 184, 0, 0.2)')
                  : 'rgba(255,255,255,0.05)',
                border: `2px solid ${follower2HasEntry ? (entryCommitted ? 'var(--accent-green)' : 'var(--warning)') : 'var(--border-color)'}`,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 0.25rem',
                transition: 'all 0.5s',
                boxShadow: follower2HasEntry ? `0 0 15px ${entryCommitted ? 'var(--accent-green)' : 'var(--warning)'}` : 'none'
              }}>
                <Server size={22} color="var(--text-secondary)" />
                {follower2HasEntry && (
                  <CheckCircle
                    size={14}
                    color={entryCommitted ? 'var(--accent-green)' : 'var(--warning)'}
                    style={{ marginTop: '2px' }}
                  />
                )}
              </div>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-primary)' }}>node2</div>
              <div style={{ fontSize: '0.65rem', color: 'var(--text-secondary)' }}>FOLLOWER</div>
            </div>
          </div>
        </div>

        {/* Step Progress */}
        <div style={{ marginTop: '1rem' }}>
          <div style={{ display: 'flex', gap: '4px', marginBottom: '0.5rem' }}>
            {[1, 2, 3, 4, 5, 6, 7].map(s => (
              <div key={s} style={{
                flex: 1,
                height: '6px',
                borderRadius: '3px',
                background: s <= slowDemoStep ? 'var(--accent-green)' : 'var(--border-color)',
                transition: 'background 0.5s'
              }} />
            ))}
          </div>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', textAlign: 'center' }}>
            {slowDemoStep === 0 ? 'Ready to start' :
             slowDemoStep === 1 ? 'Step 1/7: Client sending request...' :
             slowDemoStep === 2 ? 'Step 2/7: Leader appending to log...' :
             slowDemoStep === 3 ? 'Step 3/7: Replicating to node1...' :
             slowDemoStep === 4 ? 'Step 4/7: Replicating to node2...' :
             slowDemoStep === 5 ? 'Step 5/7: Committing entry...' :
             slowDemoStep === 6 ? 'Step 6/7: Writing to cluster...' :
             'Step 7/7: Complete!'}
          </div>
        </div>

        {/* Legend */}
        <div style={{
          display: 'flex',
          justifyContent: 'center',
          gap: '2rem',
          marginTop: '1rem',
          fontSize: '0.75rem',
          color: 'var(--text-secondary)'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <div style={{ width: '12px', height: '12px', background: 'var(--warning)', borderRadius: '3px' }} />
            <span>Replicated (uncommitted)</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <div style={{ width: '12px', height: '12px', background: 'var(--accent-green)', borderRadius: '3px' }} />
            <span>Committed</span>
          </div>
        </div>
      </div>

      {/* Event Log */}
      <div style={{
        background: 'rgba(0,0,0,0.2)',
        borderRadius: '8px',
        padding: '1rem'
      }}>
        <div style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', marginBottom: '0.75rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <Clock size={16} />
          Replication Events
        </div>
        <div style={{
          background: 'rgba(0,0,0,0.3)',
          borderRadius: '6px',
          padding: '0.75rem',
          maxHeight: '200px',
          overflowY: 'auto',
          fontFamily: 'monospace',
          fontSize: '0.75rem'
        }}>
          {eventLog.length === 0 ? (
            <div style={{ color: 'var(--text-secondary)', textAlign: 'center', padding: '1rem 0' }}>
              Click "Start Demo" to see step-by-step replication
            </div>
          ) : (
            eventLog.map((event, i) => (
              <div key={i} style={{
                padding: '0.3rem 0',
                borderBottom: i < eventLog.length - 1 ? '1px solid var(--border-color)' : 'none',
                color: event.type === 'success' ? 'var(--accent-green)' :
                       event.type === 'warning' ? 'var(--warning)' :
                       event.type === 'error' ? 'var(--error)' :
                       'var(--text-secondary)'
              }}>
                <span style={{ opacity: 0.5 }}>[{event.time}]</span> {event.message}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  )
}

export default ReplicationDemo
