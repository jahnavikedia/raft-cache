import { useState, useEffect } from 'react'
import { Crown, AlertTriangle, CheckCircle, PlayCircle, RotateCcw, Power, Clock, Users, Activity, Vote, Circle } from 'lucide-react'
import axios from 'axios'

const ELECTION_TIMEOUT_MIN = 150
const ELECTION_TIMEOUT_MAX = 300

const ElectionDemo = ({ nodes }) => {
  // Demo states: idle, stopping_cluster, starting_fresh, timeout_countdown, candidate_phase, voting_phase, leader_elected, running
  const [demoMode, setDemoMode] = useState('running') // 'guided' or 'running'
  const [demoStep, setDemoStep] = useState('idle')
  const [stepMessage, setStepMessage] = useState('')

  // Election visualization
  const [timeoutValues, setTimeoutValues] = useState({})
  const [countdownValues, setCountdownValues] = useState({}) // Track all nodes' countdowns
  const [countdownNode, setCountdownNode] = useState(null)
  const [candidateNode, setCandidateNode] = useState(null)
  const [votes, setVotes] = useState({})
  const [electedLeader, setElectedLeader] = useState(null)

  // Existing state
  const [demoState, setDemoState] = useState('idle')
  const [killedNode, setKilledNode] = useState(null)
  const [previousLeader, setPreviousLeader] = useState(null)
  const [newLeader, setNewLeader] = useState(null)
  const [electionStartTime, setElectionStartTime] = useState(null)
  const [electionDuration, setElectionDuration] = useState(null)
  const [termBefore, setTermBefore] = useState(null)
  const [termAfter, setTermAfter] = useState(null)
  const [eventLog, setEventLog] = useState([])

  const currentLeader = nodes.find(n => n.state === 'LEADER' && n.active)
  const activeNodes = nodes.filter(n => n.active && n.state !== 'DOWN' && n.state !== 'UNKNOWN')
  const downNodes = nodes.filter(n => !n.active || n.state === 'DOWN' || n.state === 'UNKNOWN')

  const addEvent = (message, type = 'info') => {
    const timestamp = new Date().toLocaleTimeString()
    setEventLog(prev => [...prev.slice(-14), { timestamp, message, type }])
  }

  // Generate random timeout for each node
  const generateTimeouts = () => {
    const timeouts = {}
    nodes.forEach(node => {
      timeouts[node.id] = Math.floor(Math.random() * (ELECTION_TIMEOUT_MAX - ELECTION_TIMEOUT_MIN)) + ELECTION_TIMEOUT_MIN
    })
    return timeouts
  }

  // Start the guided demo - SIMULATION ONLY (doesn't touch actual cluster)
  const startGuidedDemo = async () => {
    setDemoMode('guided')
    setEventLog([])
    setCountdownValues({})

    // This is a SIMULATION to explain how Raft election works
    // It doesn't actually stop/start nodes - just animates the process

    setDemoStep('starting_fresh')
    setStepMessage('Simulating: Cluster starts with all nodes as FOLLOWERS (no leader yet)...')
    addEvent('SIMULATION: Showing how initial leader election works', 'info')
    addEvent('All nodes start as FOLLOWERS with no leader', 'info')

    await sleep(2000)

    // Generate random timeouts for visualization
    const timeouts = generateTimeouts()
    setTimeoutValues(timeouts)
    addEvent(`Each node has a random election timeout:`, 'info')
    Object.entries(timeouts).forEach(([k, v]) => {
      addEvent(`  ${k}: ${v}ms`, 'info')
    })

    await sleep(1500)

    // Find which node has the lowest timeout (will become candidate first)
    const firstCandidate = Object.entries(timeouts).reduce((a, b) => a[1] < b[1] ? a : b)[0]
    const shortestTimeout = timeouts[firstCandidate]

    setDemoStep('timeout_countdown')
    setStepMessage(`No heartbeat received - ALL nodes' election timeouts counting down...`)
    addEvent(`All timers started! ${firstCandidate} will win (shortest: ${shortestTimeout}ms)`, 'warning')

    // Animate countdown for ALL nodes simultaneously
    // The animation runs until the shortest timeout reaches 0
    const totalSteps = 30
    const displayTime = 4000 // Total animation time
    const stepTime = displayTime / totalSteps

    // Initialize countdown values for all nodes
    const initialCountdowns = {}
    nodes.forEach(node => {
      initialCountdowns[node.id] = timeouts[node.id]
    })
    setCountdownValues(initialCountdowns)

    // Animate all countdowns
    for (let i = totalSteps; i >= 0; i--) {
      const progress = i / totalSteps
      const newCountdowns = {}

      nodes.forEach(node => {
        const nodeTimeout = timeouts[node.id]
        // Calculate remaining time based on progress
        // Shortest timeout should reach 0 when progress = 0
        const relativeProgress = Math.max(0, progress - (1 - shortestTimeout / nodeTimeout))
        const adjustedProgress = relativeProgress / (shortestTimeout / nodeTimeout)
        newCountdowns[node.id] = Math.max(0, Math.round(nodeTimeout * adjustedProgress))
      })

      // Simpler approach: all count down proportionally, shortest hits 0 first
      nodes.forEach(node => {
        newCountdowns[node.id] = Math.round(timeouts[node.id] * progress)
      })

      setCountdownValues(newCountdowns)
      setCountdownNode(firstCandidate) // Highlight the one that will win

      await sleep(stepTime)
    }

    setDemoStep('candidate_phase')
    setCandidateNode(firstCandidate)
    setStepMessage(`${firstCandidate}'s timeout expired FIRST! It becomes a CANDIDATE and increments term to 1...`)
    addEvent(`${firstCandidate} timeout expired first → becomes CANDIDATE`, 'warning')
    addEvent(`${firstCandidate} increments term to 1 and votes for itself`, 'info')

    await sleep(2000)

    // Voting phase
    setDemoStep('voting_phase')
    setStepMessage(`${firstCandidate} sends RequestVote RPCs to other nodes...`)
    addEvent(`${firstCandidate} sends RequestVote to all other nodes`, 'info')

    await sleep(1000)

    const voteResults = {}
    voteResults[firstCandidate] = true // Votes for itself
    setVotes({...voteResults})
    addEvent(`${firstCandidate} votes for itself (1/${nodes.length} votes)`, 'info')

    await sleep(1000)

    // Other nodes vote
    const otherNodes = nodes.filter(n => n.id !== firstCandidate)
    for (const node of otherNodes) {
      await sleep(1000)
      voteResults[node.id] = true
      setVotes({...voteResults})
      addEvent(`${node.id} grants vote to ${firstCandidate} (${Object.keys(voteResults).length}/${nodes.length} votes)`, 'success')
    }

    await sleep(1500)

    // Leader elected
    setDemoStep('leader_elected')
    setElectedLeader(firstCandidate)
    const majority = Math.floor(nodes.length / 2) + 1
    setStepMessage(`${firstCandidate} received ${Object.keys(voteResults).length} votes (majority=${majority}) → becomes LEADER!`)
    addEvent(`${firstCandidate} elected as LEADER with ${Object.keys(voteResults).length}/${nodes.length} votes`, 'success')
    addEvent('Leader now sends heartbeats every ~50ms to maintain leadership', 'info')

    await sleep(4000)

    // Transition to running mode
    setDemoStep('running')
    setDemoMode('running')
    setStepMessage('')
    addEvent('SIMULATION COMPLETE - This showed how Raft elects a leader', 'info')
    addEvent('Now try killing the REAL leader above to see an actual election!', 'success')
  }

  const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms))

  // Watch for new leader election (for manual kills)
  useEffect(() => {
    if (demoState === 'electing') {
      const leader = nodes.find(n => n.state === 'LEADER' && n.active && n.id !== killedNode)
      if (leader) {
        const duration = Date.now() - electionStartTime
        setNewLeader(leader)
        setTermAfter(leader.currentTerm || leader.term)
        setElectionDuration(duration)
        setDemoState('complete')
        addEvent(`New leader elected: ${leader.id}`, 'success')
        addEvent(`Election completed in ${duration}ms`, 'success')
      }
    }
  }, [nodes, demoState, killedNode, electionStartTime])

  const killNode = async (nodeToKill) => {
    const wasLeader = nodeToKill.state === 'LEADER'

    if (wasLeader) {
      setPreviousLeader(nodeToKill)
      setTermBefore(nodeToKill.currentTerm || nodeToKill.term)
      setKilledNode(nodeToKill.id)
      setDemoState('killing')
      addEvent(`Killing leader node: ${nodeToKill.id}`, 'warning')
    } else {
      addEvent(`Killing follower node: ${nodeToKill.id}`, 'warning')
    }

    try {
      await axios.post(`http://localhost:${nodeToKill.port}/admin/shutdown`)
    } catch (e) {
      // Expected - connection refused after shutdown
    }

    if (wasLeader) {
      setTimeout(() => {
        setElectionStartTime(Date.now())
        setDemoState('electing')
        addEvent('Election timeout triggered - followers requesting votes', 'info')
      }, 300)
    } else {
      addEvent(`Node ${nodeToKill.id} stopped`, 'info')
    }
  }

  const startNode = async (nodeId) => {
    addEvent(`Starting node: ${nodeId}`, 'info')
    try {
      await axios.post(`http://localhost:5002/start/${nodeId}`)
      addEvent(`Node ${nodeId} starting up...`, 'success')
    } catch (e) {
      addEvent(`Failed to start ${nodeId} - is node-manager running?`, 'error')
    }
  }

  const resetDemo = () => {
    setDemoMode('running')
    setDemoStep('idle')
    setDemoState('idle')
    setKilledNode(null)
    setPreviousLeader(null)
    setNewLeader(null)
    setElectionStartTime(null)
    setElectionDuration(null)
    setTermBefore(null)
    setTermAfter(null)
    setCountdownNode(null)
    setCountdownValues({})
    setCandidateNode(null)
    setVotes({})
    setElectedLeader(null)
    setTimeoutValues({})
    setStepMessage('')
  }

  // Render the step-by-step visualization
  const renderStepVisualization = () => {
    if (demoMode !== 'guided') return null

    return (
      <div style={{
        background: 'rgba(0, 0, 0, 0.4)',
        borderRadius: '12px',
        padding: '1.5rem',
        marginBottom: '1.5rem',
        border: '2px solid var(--accent-blue)'
      }}>
        {/* Step indicator */}
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          marginBottom: '1.5rem',
          position: 'relative'
        }}>
          {/* Progress line */}
          <div style={{
            position: 'absolute',
            top: '15px',
            left: '40px',
            right: '40px',
            height: '2px',
            background: 'var(--border-color)'
          }} />

          {['Start', 'Timeout', 'Candidate', 'Voting', 'Leader'].map((step, i) => {
            const stepMap = {
              0: ['stopping_cluster', 'starting_fresh'],
              1: ['timeout_countdown'],
              2: ['candidate_phase'],
              3: ['voting_phase'],
              4: ['leader_elected', 'running']
            }
            const isActive = stepMap[i]?.includes(demoStep)
            const isPast = i < Object.keys(stepMap).findIndex(k => stepMap[k]?.includes(demoStep))

            return (
              <div key={step} style={{ textAlign: 'center', zIndex: 1 }}>
                <div style={{
                  width: '30px',
                  height: '30px',
                  borderRadius: '50%',
                  background: isActive ? 'var(--accent-blue)' : isPast ? 'var(--accent-green)' : 'var(--bg-card)',
                  border: `2px solid ${isActive ? 'var(--accent-blue)' : isPast ? 'var(--accent-green)' : 'var(--border-color)'}`,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  margin: '0 auto 0.5rem',
                  color: (isActive || isPast) ? '#000' : 'var(--text-secondary)',
                  fontWeight: 'bold',
                  fontSize: '0.8rem'
                }}>
                  {isPast ? '✓' : i + 1}
                </div>
                <span style={{
                  fontSize: '0.75rem',
                  color: isActive ? 'var(--accent-blue)' : isPast ? 'var(--accent-green)' : 'var(--text-secondary)'
                }}>
                  {step}
                </span>
              </div>
            )
          })}
        </div>

        {/* Current step message */}
        <div style={{
          background: 'rgba(0, 240, 255, 0.1)',
          border: '1px solid var(--accent-blue)',
          borderRadius: '8px',
          padding: '1rem',
          marginBottom: '1.5rem',
          textAlign: 'center'
        }}>
          <div style={{ fontSize: '1.1rem', fontWeight: 'bold', color: 'var(--accent-blue)' }}>
            {stepMessage || 'Preparing demo...'}
          </div>
        </div>

        {/* Visual representation of nodes */}
        <div style={{
          display: 'flex',
          justifyContent: 'center',
          gap: '2rem',
          flexWrap: 'wrap'
        }}>
          {nodes.map(node => {
            const timeout = timeoutValues[node.id]
            const currentCountdown = countdownValues[node.id]
            const isCountingDown = demoStep === 'timeout_countdown'
            const isWinningNode = countdownNode === node.id
            const isCandidate = candidateNode === node.id && ['candidate_phase', 'voting_phase', 'leader_elected'].includes(demoStep)
            const hasVoted = votes[node.id]
            const isLeader = electedLeader === node.id
            const timerFinished = isCountingDown && currentCountdown === 0

            return (
              <div
                key={node.id}
                style={{
                  width: '160px',
                  padding: '1rem',
                  borderRadius: '12px',
                  background: isLeader ? 'rgba(0, 255, 157, 0.1)' :
                             isCandidate ? 'rgba(255, 184, 0, 0.1)' :
                             timerFinished ? 'rgba(255, 184, 0, 0.1)' :
                             isCountingDown && isWinningNode ? 'rgba(0, 240, 255, 0.1)' :
                             'rgba(255, 255, 255, 0.03)',
                  border: `2px solid ${isLeader ? 'var(--accent-green)' :
                          isCandidate ? 'var(--warning)' :
                          timerFinished ? 'var(--warning)' :
                          isCountingDown && isWinningNode ? 'var(--accent-blue)' :
                          isCountingDown ? 'var(--accent-purple)' :
                          'var(--border-color)'}`,
                  textAlign: 'center',
                  transition: 'all 0.3s'
                }}
              >
                {/* Node icon */}
                <div style={{
                  width: '50px',
                  height: '50px',
                  borderRadius: '50%',
                  background: isLeader ? 'var(--accent-green)' :
                             isCandidate ? 'var(--warning)' :
                             'var(--bg-card)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  margin: '0 auto 0.75rem',
                  position: 'relative'
                }}>
                  {isLeader ? (
                    <Crown size={24} color="#000" />
                  ) : isCandidate ? (
                    <Vote size={24} color="#000" />
                  ) : (
                    <Circle size={24} color="var(--text-secondary)" />
                  )}

                  {/* Vote indicator */}
                  {hasVoted && demoStep === 'voting_phase' && (
                    <div style={{
                      position: 'absolute',
                      top: '-5px',
                      right: '-5px',
                      background: 'var(--accent-green)',
                      borderRadius: '50%',
                      width: '20px',
                      height: '20px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center'
                    }}>
                      <CheckCircle size={14} color="#000" />
                    </div>
                  )}
                </div>

                {/* Node name */}
                <div style={{ fontWeight: 'bold', marginBottom: '0.25rem' }}>{node.id}</div>

                {/* Role */}
                <div style={{
                  fontSize: '0.75rem',
                  padding: '0.2rem 0.5rem',
                  borderRadius: '4px',
                  display: 'inline-block',
                  background: isLeader ? 'rgba(0, 255, 157, 0.2)' :
                             isCandidate ? 'rgba(255, 184, 0, 0.2)' :
                             'rgba(255, 255, 255, 0.05)',
                  color: isLeader ? 'var(--accent-green)' :
                        isCandidate ? 'var(--warning)' :
                        'var(--text-secondary)'
                }}>
                  {isLeader ? 'LEADER' : isCandidate ? 'CANDIDATE' : 'FOLLOWER'}
                </div>

                {/* Timeout display - shows for all nodes during countdown */}
                {timeout && demoStep !== 'leader_elected' && (
                  <div style={{ marginTop: '0.75rem' }}>
                    <div style={{ fontSize: '0.7rem', color: 'var(--text-secondary)' }}>
                      {isCountingDown ? 'Time Left' : 'Timeout'}
                    </div>
                    <div style={{
                      fontSize: isCountingDown ? '1.4rem' : '0.9rem',
                      fontFamily: 'monospace',
                      color: timerFinished ? 'var(--warning)' :
                             isCountingDown && isWinningNode ? 'var(--accent-blue)' :
                             isCountingDown ? 'var(--accent-purple)' :
                             'var(--text-secondary)',
                      fontWeight: isCountingDown ? 'bold' : 'normal'
                    }}>
                      {isCountingDown ?
                        (timerFinished ? 'EXPIRED!' : `${currentCountdown}ms`) :
                        `${timeout}ms`}
                    </div>

                    {/* Progress bar for countdown - show for ALL nodes */}
                    {isCountingDown && (
                      <div style={{
                        marginTop: '0.5rem',
                        height: '6px',
                        background: 'var(--border-color)',
                        borderRadius: '3px',
                        overflow: 'hidden'
                      }}>
                        <div style={{
                          height: '100%',
                          background: timerFinished ? 'var(--warning)' :
                                     isWinningNode ? 'var(--accent-blue)' : 'var(--accent-purple)',
                          width: `${timeout > 0 ? (currentCountdown / timeout) * 100 : 0}%`,
                          transition: 'width 0.1s linear'
                        }} />
                      </div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>

        {/* Vote count during voting phase */}
        {demoStep === 'voting_phase' && (
          <div style={{
            marginTop: '1.5rem',
            textAlign: 'center',
            padding: '1rem',
            background: 'rgba(255, 184, 0, 0.1)',
            borderRadius: '8px'
          }}>
            <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>Votes for {candidateNode}</div>
            <div style={{ fontSize: '2rem', fontWeight: 'bold', color: 'var(--warning)' }}>
              {Object.keys(votes).length} / {nodes.length}
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
              Need majority: {Math.floor(nodes.length / 2) + 1} votes
            </div>
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="card" style={{ background: 'linear-gradient(135deg, rgba(0,240,255,0.03) 0%, rgba(138,43,226,0.03) 100%)' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '1rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <Crown size={24} color="var(--accent-blue)" />
          <h2 style={{ margin: 0, fontSize: '1.5rem' }}>Raft Consensus Demo</h2>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          {demoMode === 'running' && demoStep !== 'idle' && (
            <button
              onClick={resetDemo}
              style={{
                padding: '0.5rem 1rem',
                background: 'rgba(255,255,255,0.05)',
                border: '1px solid var(--border-color)',
                borderRadius: '6px',
                color: 'var(--text-secondary)',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem',
                fontSize: '0.85rem'
              }}
            >
              <RotateCcw size={14} /> Reset
            </button>
          )}
        </div>
      </div>

      {/* Guided Demo Visualization */}
      {renderStepVisualization()}

      {/* Start Demo Button - shown when demo not running */}
      {demoMode === 'running' && demoStep !== 'running' && (
        <div style={{
          textAlign: 'center',
          padding: '1.5rem',
          marginBottom: '1.5rem',
          background: 'rgba(0, 240, 255, 0.05)',
          borderRadius: '12px',
          border: '2px dashed var(--accent-blue)'
        }}>
          <h3 style={{ margin: '0 0 0.75rem 0', color: 'var(--accent-blue)' }}>Learn How Raft Election Works</h3>
          <p style={{ color: 'var(--text-secondary)', marginBottom: '1rem', fontSize: '0.9rem' }}>
            Watch a step-by-step simulation of the leader election process
          </p>
          <button
            onClick={startGuidedDemo}
            style={{
              padding: '0.75rem 1.5rem',
              background: 'linear-gradient(135deg, var(--accent-blue) 0%, var(--accent-purple) 100%)',
              border: 'none',
              borderRadius: '8px',
              color: '#000',
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.5rem',
              fontSize: '1rem',
              fontWeight: 'bold'
            }}
          >
            <PlayCircle size={20} /> Play Election Simulation
          </button>
          <p style={{ color: 'var(--text-secondary)', marginTop: '0.75rem', fontSize: '0.8rem', opacity: 0.7 }}>
            (This is a visualization only - won't affect the running cluster)
          </p>
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
        {/* Left: Node Control */}
        <div>
          <h3 style={{ margin: '0 0 1rem 0', fontSize: '1rem', color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Users size={16} /> Node Control
          </h3>

          {/* Live Stats */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, 1fr)',
            gap: '0.75rem',
            marginBottom: '1rem'
          }}>
            <div style={{
              background: 'rgba(0, 255, 157, 0.1)',
              padding: '0.75rem',
              borderRadius: '8px',
              textAlign: 'center'
            }}>
              <div style={{ fontSize: '1.5rem', fontWeight: 'bold', color: 'var(--accent-green)' }}>{activeNodes.length}</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Active</div>
            </div>
            <div style={{
              background: 'rgba(255, 77, 77, 0.1)',
              padding: '0.75rem',
              borderRadius: '8px',
              textAlign: 'center'
            }}>
              <div style={{ fontSize: '1.5rem', fontWeight: 'bold', color: 'var(--error)' }}>{downNodes.length}</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Down</div>
            </div>
            <div style={{
              background: 'rgba(138, 43, 226, 0.1)',
              padding: '0.75rem',
              borderRadius: '8px',
              textAlign: 'center'
            }}>
              <div style={{ fontSize: '1.5rem', fontWeight: 'bold', color: 'var(--accent-purple)' }}>
                {currentLeader?.currentTerm || currentLeader?.term || '-'}
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Term</div>
            </div>
          </div>

          {/* Node Buttons */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {nodes.map(node => {
              const isLeader = node.state === 'LEADER'
              const isDown = !node.active || node.state === 'DOWN' || node.state === 'UNKNOWN'

              return (
                <div
                  key={node.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '0.75rem 1rem',
                    background: isDown ? 'rgba(255, 77, 77, 0.05)' : isLeader ? 'rgba(0, 240, 255, 0.05)' : 'rgba(255,255,255,0.02)',
                    border: `1px solid ${isLeader ? 'var(--accent-blue)' : isDown ? 'var(--error)' : 'var(--border-color)'}`,
                    borderRadius: '8px'
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                    <div style={{
                      width: '10px',
                      height: '10px',
                      borderRadius: '50%',
                      background: isDown ? 'var(--error)' : 'var(--accent-green)',
                      boxShadow: isDown ? 'none' : '0 0 8px var(--accent-green)'
                    }} />
                    <div>
                      <div style={{ fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        {node.id}
                        {isLeader && <Crown size={14} color="var(--accent-blue)" />}
                      </div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                        {isDown ? 'OFFLINE' : node.state} • Port {node.port}
                      </div>
                    </div>
                  </div>

                  {!isDown ? (
                    <button
                      onClick={() => killNode(node)}
                      disabled={demoState === 'killing' || demoState === 'electing' || demoMode === 'guided'}
                      style={{
                        padding: '0.4rem 0.75rem',
                        background: 'rgba(255, 77, 77, 0.1)',
                        border: '1px solid var(--error)',
                        borderRadius: '6px',
                        color: 'var(--error)',
                        cursor: (demoState === 'killing' || demoState === 'electing' || demoMode === 'guided') ? 'not-allowed' : 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '0.4rem',
                        fontSize: '0.8rem',
                        opacity: (demoState === 'killing' || demoState === 'electing' || demoMode === 'guided') ? 0.5 : 1
                      }}
                    >
                      <Power size={12} /> Kill
                    </button>
                  ) : (
                    <button
                      onClick={() => startNode(node.id)}
                      disabled={demoMode === 'guided'}
                      style={{
                        padding: '0.4rem 0.75rem',
                        background: 'rgba(0, 255, 157, 0.1)',
                        border: '1px solid var(--accent-green)',
                        borderRadius: '6px',
                        color: 'var(--accent-green)',
                        cursor: demoMode === 'guided' ? 'not-allowed' : 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '0.4rem',
                        fontSize: '0.8rem',
                        opacity: demoMode === 'guided' ? 0.5 : 1
                      }}
                    >
                      <PlayCircle size={12} /> Start
                    </button>
                  )}
                </div>
              )
            })}
          </div>

          {/* Election Info Box */}
          <div style={{
            marginTop: '1rem',
            padding: '1rem',
            background: 'rgba(0, 240, 255, 0.05)',
            border: '1px solid rgba(0, 240, 255, 0.2)',
            borderRadius: '8px'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
              <Clock size={14} color="var(--accent-blue)" />
              <span style={{ fontWeight: 'bold', fontSize: '0.9rem' }}>Election Timeout</span>
            </div>
            <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
              <strong>150-300ms</strong> randomized timeout. When a follower doesn't receive heartbeat from leader, it becomes a candidate and requests votes.
            </div>
          </div>
        </div>

        {/* Right: Event Log & Status */}
        <div>
          <h3 style={{ margin: '0 0 1rem 0', fontSize: '1rem', color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Activity size={16} /> Election Events
          </h3>

          {/* Election Status */}
          {demoState !== 'idle' && demoMode === 'running' && (
            <div style={{
              padding: '1rem',
              marginBottom: '1rem',
              borderRadius: '8px',
              background: demoState === 'complete' ? 'rgba(0, 255, 157, 0.1)' :
                         demoState === 'killing' ? 'rgba(255, 77, 77, 0.1)' : 'rgba(0, 240, 255, 0.1)',
              border: `1px solid ${demoState === 'complete' ? 'var(--accent-green)' :
                      demoState === 'killing' ? 'var(--error)' : 'var(--accent-blue)'}`
            }}>
              {demoState === 'killing' && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                  <div className="pulse">
                    <AlertTriangle size={24} color="var(--error)" />
                  </div>
                  <div>
                    <div style={{ fontWeight: 'bold', color: 'var(--error)' }}>Killing Node...</div>
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                      Simulating {previousLeader?.id} failure
                    </div>
                  </div>
                </div>
              )}

              {demoState === 'electing' && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                  <div className="spin" style={{
                    width: '24px',
                    height: '24px',
                    borderRadius: '50%',
                    border: '2px solid var(--border-color)',
                    borderTopColor: 'var(--accent-blue)'
                  }} />
                  <div>
                    <div style={{ fontWeight: 'bold', color: 'var(--accent-blue)' }}>Election In Progress</div>
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                      Followers voting for new leader...
                    </div>
                  </div>
                </div>
              )}

              {demoState === 'complete' && (
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1rem' }}>
                    <CheckCircle size={24} color="var(--accent-green)" />
                    <div>
                      <div style={{ fontWeight: 'bold', color: 'var(--accent-green)' }}>Election Complete!</div>
                      <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                        Completed in <strong>{electionDuration}ms</strong>
                      </div>
                    </div>
                  </div>

                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-around',
                    padding: '0.75rem',
                    background: 'rgba(0,0,0,0.2)',
                    borderRadius: '6px'
                  }}>
                    <div style={{ textAlign: 'center' }}>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Old Leader</div>
                      <div style={{ color: 'var(--error)', fontWeight: 'bold' }}>{previousLeader?.id}</div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Term {termBefore}</div>
                    </div>
                    <div style={{ fontSize: '1.5rem', color: 'var(--accent-blue)' }}>→</div>
                    <div style={{ textAlign: 'center' }}>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>New Leader</div>
                      <div style={{ color: 'var(--accent-green)', fontWeight: 'bold' }}>{newLeader?.id}</div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Term {termAfter}</div>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Event Log */}
          <div style={{
            background: 'rgba(0,0,0,0.3)',
            borderRadius: '8px',
            padding: '0.75rem',
            height: '280px',
            overflowY: 'auto',
            fontFamily: 'monospace',
            fontSize: '0.8rem'
          }}>
            {eventLog.length === 0 ? (
              <div style={{ color: 'var(--text-secondary)', textAlign: 'center', padding: '2rem 0' }}>
                <Activity size={24} style={{ marginBottom: '0.5rem', opacity: 0.5 }} />
                <div>Click "Start Guided Demo" to begin</div>
                <div style={{ fontSize: '0.75rem', marginTop: '0.25rem' }}>Or kill a node to trigger an election</div>
              </div>
            ) : (
              eventLog.map((event, i) => (
                <div
                  key={i}
                  style={{
                    padding: '0.4rem 0',
                    borderBottom: i < eventLog.length - 1 ? '1px solid var(--border-color)' : 'none',
                    display: 'flex',
                    gap: '0.75rem'
                  }}
                >
                  <span style={{ color: 'var(--text-secondary)', flexShrink: 0 }}>{event.timestamp}</span>
                  <span style={{
                    color: event.type === 'success' ? 'var(--accent-green)' :
                           event.type === 'warning' ? 'var(--warning)' :
                           event.type === 'error' ? 'var(--error)' : 'var(--text-primary)'
                  }}>
                    {event.message}
                  </span>
                </div>
              ))
            )}
          </div>

          {/* How It Works */}
          <div style={{
            marginTop: '1rem',
            padding: '1rem',
            background: 'rgba(138, 43, 226, 0.05)',
            border: '1px solid rgba(138, 43, 226, 0.2)',
            borderRadius: '8px'
          }}>
            <div style={{ fontWeight: 'bold', fontSize: '0.9rem', marginBottom: '0.5rem', color: 'var(--accent-purple)' }}>
              How Raft Election Works:
            </div>
            <ol style={{ margin: 0, paddingLeft: '1.25rem', color: 'var(--text-secondary)', fontSize: '0.8rem', lineHeight: 1.8 }}>
              <li>Leader sends heartbeats every ~50ms</li>
              <li>Follower times out after 150-300ms without heartbeat</li>
              <li>Follower becomes <strong>Candidate</strong>, increments term</li>
              <li>Candidate requests votes from other nodes</li>
              <li>Majority vote = new <strong>Leader</strong></li>
            </ol>
          </div>
        </div>
      </div>
    </div>
  )
}

export default ElectionDemo
