import { useState, useEffect } from 'react'
import { Activity, Database, Brain, Zap, Clock, Scale } from 'lucide-react'
import ClusterView from './components/ClusterView'
import LogViewer from './components/LogViewer'
import Controls from './components/Controls'
import MLStats from './components/MLStats'
import CacheView from './components/CacheView'
import LatencyComparison from './components/LatencyComparison'
import ElectionDemo from './components/ElectionDemo'
import EvictionComparison from './components/EvictionComparison'
import axios from 'axios'

function App() {
  const [nodes, setNodes] = useState([
    { id: 'node1', port: 8081, state: 'UNKNOWN', term: 0, logSize: 0, lease: false },
    { id: 'node2', port: 8082, state: 'UNKNOWN', term: 0, logSize: 0, lease: false },
    { id: 'node3', port: 8083, state: 'UNKNOWN', term: 0, logSize: 0, lease: false },
  ])
  const [logs, setLogs] = useState([])
  const [mlPrediction, setMlPrediction] = useState(null)
  const [testKey, setTestKey] = useState('key1')

  // Poll node status
  useEffect(() => {
    const interval = setInterval(async () => {
      const updatedNodes = await Promise.all(nodes.map(async (node) => {
        try {
          const res = await axios.get(`http://localhost:${node.port}/status`)
          return { ...node, ...res.data, state: res.data.role, active: true }
        } catch {
          return { ...node, active: false, state: 'DOWN' }
        }
      }))
      setNodes(updatedNodes)

      // Fetch logs from leader
      const leader = updatedNodes.find(n => n.role === 'LEADER' && n.active)
      if (leader) {
        try {
          const res = await axios.get(`http://localhost:${leader.port}/raft/log`)
          setLogs(res.data)
        } catch (e) {
          console.error("Failed to fetch logs", e)
        }
      }
    }, 1000)
    return () => clearInterval(interval)
  }, [])

  return (
    <div className="container" style={{ maxWidth: '1600px', padding: '2rem' }}>
      <header style={{ marginBottom: '2.5rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1 style={{ fontSize: '2.5rem', margin: 0, background: 'linear-gradient(to right, #00f0ff, #00ff9d)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
            Raft Cache
          </h1>
          <p style={{ color: 'var(--text-secondary)', marginTop: '0.5rem' }}>
            Distributed Consensus with ML-Driven Cache Eviction
          </p>
        </div>
        <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
          <div className="badge" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', background: 'rgba(0, 255, 157, 0.1)', color: 'var(--accent-green)', padding: '0.5rem 1rem' }}>
            <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--accent-green)', boxShadow: '0 0 8px var(--accent-green)' }} />
            Live
          </div>
        </div>
      </header>

      {/* Main Content */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
          {/* Section 1: Cluster Status */}
          <div>
            <h2 style={{
              fontSize: '1rem',
              color: 'var(--text-secondary)',
              marginBottom: '1rem',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              textTransform: 'uppercase',
              letterSpacing: '0.05em'
            }}>
              <Activity size={16} /> Cluster Status
            </h2>
            <div className="grid-cols-3">
              <ClusterView nodes={nodes} />
            </div>
          </div>

          {/* Section 2: Election Demo */}
          <ElectionDemo nodes={nodes} />

          {/* Section 3: Cache Operations & Contents */}
          <div>
            <h2 style={{
              fontSize: '1rem',
              color: 'var(--text-secondary)',
              marginBottom: '1rem',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              textTransform: 'uppercase',
              letterSpacing: '0.05em'
            }}>
              <Database size={16} /> Cache Operations
            </h2>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
              <div className="card">
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.5rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '1rem' }}>
                  <Database size={20} color="var(--accent-pink)" />
                  <h2 style={{ margin: 0, fontSize: '1.25rem' }}>Control Panel</h2>
                </div>
                <Controls nodes={nodes} onOperationComplete={() => {}} onKeyChange={setTestKey} />
              </div>
              <CacheView nodes={nodes} />
            </div>
          </div>

          {/* Section 4: Read Lease & ML */}
          <div>
            <h2 style={{
              fontSize: '1rem',
              color: 'var(--text-secondary)',
              marginBottom: '1rem',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              textTransform: 'uppercase',
              letterSpacing: '0.05em'
            }}>
              <Zap size={16} /> Performance & Intelligence
            </h2>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
              <LatencyComparison nodes={nodes} testKey={testKey} />
              <div className="card">
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.5rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '1rem' }}>
                  <Brain size={20} color="var(--accent-purple)" />
                  <h2 style={{ margin: 0, fontSize: '1.25rem' }}>ML Insights</h2>
                </div>
                <MLStats prediction={mlPrediction} setPrediction={setMlPrediction} nodes={nodes} />
              </div>
            </div>
          </div>

          {/* Section 5: LRU vs ML Eviction Comparison */}
          <div>
            <h2 style={{
              fontSize: '1rem',
              color: 'var(--text-secondary)',
              marginBottom: '1rem',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              textTransform: 'uppercase',
              letterSpacing: '0.05em'
            }}>
              <Scale size={16} /> Eviction Strategy Comparison
            </h2>
            <EvictionComparison nodes={nodes} />
          </div>

          {/* Section 6: Raft Log */}
          <div>
            <h2 style={{
              fontSize: '1rem',
              color: 'var(--text-secondary)',
              marginBottom: '1rem',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              textTransform: 'uppercase',
              letterSpacing: '0.05em'
            }}>
              <Clock size={16} /> Raft Consensus Log
            </h2>
            <LogViewer logs={logs} />
          </div>
      </div>
    </div>
  )
}

export default App
