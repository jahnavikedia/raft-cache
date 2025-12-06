import { useState, useEffect } from 'react'
import { Activity, Database, Server, Zap, Brain } from 'lucide-react'
import ClusterView from './components/ClusterView'
import LogViewer from './components/LogViewer'
import Controls from './components/Controls'
import MLStats from './components/MLStats'
import axios from 'axios'

function App() {
  const [nodes, setNodes] = useState([
    { id: 'node1', port: 8081, state: 'UNKNOWN', term: 0, logSize: 0, lease: false },
    { id: 'node2', port: 8082, state: 'UNKNOWN', term: 0, logSize: 0, lease: false },
    { id: 'node3', port: 8083, state: 'UNKNOWN', term: 0, logSize: 0, lease: false },
  ])
  const [logs, setLogs] = useState([])

  const [mlPrediction, setMlPrediction] = useState(null)

  console.log('App rendering')

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
    <div className="container">
      <header style={{ marginBottom: '2rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1 style={{ fontSize: '2.5rem', margin: 0, background: 'linear-gradient(to right, #00f0ff, #00ff9d)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
            Raft Cache
          </h1>
          <p style={{ color: 'var(--text-secondary)', marginTop: '0.5rem' }}>
            Distributed Consensus & ML-Driven Eviction
          </p>
        </div>
        <div style={{ display: 'flex', gap: '1rem' }}>
          <div className="badge" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', background: 'rgba(255,255,255,0.05)' }}>
            <Activity size={16} /> Live Monitor
          </div>
        </div>
      </header>

      <div className="grid-cols-3" style={{ marginBottom: '2rem' }}>
        <ClusterView nodes={nodes} />
      </div>

      <div style={{ marginBottom: '2rem' }}>
        <LogViewer logs={logs} />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '1.5rem', marginBottom: '2rem' }}>
        <div className="card">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '1rem' }}>
            <Database size={20} color="var(--accent-pink)" />
            <h2 style={{ margin: 0, fontSize: '1.25rem' }}>Control Panel</h2>
          </div>
          <Controls nodes={nodes} onOperationComplete={() => {}} />
        </div>

        <div className="card">
           <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '1rem' }}>
            <Brain size={20} color="var(--accent-purple)" />
            <h2 style={{ margin: 0, fontSize: '1.25rem' }}>ML Insights</h2>
          </div>
          <MLStats prediction={mlPrediction} setPrediction={setMlPrediction} nodes={nodes} />
        </div>
      </div>
    </div>
  )
}

export default App
