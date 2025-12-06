import React, { useState } from 'react'
import { Send, Zap, Trash2, RefreshCw, Database } from 'lucide-react'
import axios from 'axios'

const Controls = ({ nodes, onOperationComplete }) => {
  const [key, setKey] = useState('key1')
  const [value, setValue] = useState('value1')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)

  const getLeaderUrl = () => {
    const leader = nodes.find(n => n.state === 'LEADER' && n.active)
    return leader ? `http://localhost:${leader.port}` : `http://localhost:${nodes[0].port}`
  }

  const handleWrite = async () => {
    setLoading(true)
    setResult(null)
    try {
      const url = getLeaderUrl()
      await axios.post(`${url}/cache/${key}`, {
        clientId: 'demo-ui',
        value: value,
        sequenceNumber: Date.now()
      })
      setResult({ success: true, message: `Written ${key}=${value} to Leader` })
      onOperationComplete()
    } catch (e) {
      setResult({ success: false, message: e.response?.data?.message || e.message })
    } finally {
      setLoading(false)
    }
  }

  const handleRead = async (consistency) => {
    setLoading(true)
    setResult(null)
    try {
      // Pick a random node to demonstrate consistency
      // For lease read, we ideally want to hit the leader to see the lease effect
      // For eventual, we can hit any follower
      let targetNode = nodes.find(n => n.state === 'LEADER' && n.active)
      if (!targetNode) targetNode = nodes[0]
      
      if (consistency === 'eventual') {
         // Try to find a follower
         const follower = nodes.find(n => n.state === 'FOLLOWER' && n.active)
         if (follower) targetNode = follower
      }

      const startTime = performance.now()
      const res = await axios.get(`http://localhost:${targetNode.port}/cache/${key}?consistency=${consistency}`)
      const endTime = performance.now()
      
      const latency = (endTime - startTime).toFixed(2)
      const leaseHeader = res.headers['x-lease-remaining-ms']
      
      setResult({ 
        success: true, 
        message: `Read ${key}=${res.data.value}`,
        details: `Latency: ${latency}ms | Node: ${targetNode.id} | Mode: ${consistency} ${leaseHeader ? `| Lease: ${leaseHeader}ms` : ''}`
      })
    } catch (e) {
      setResult({ success: false, message: e.response?.data?.message || e.message })
    } finally {
      setLoading(false)
    }
  }

  const generateTraffic = async () => {
    setLoading(true)
    setResult({ success: true, message: "Generating traffic pattern..." })
    
    try {
        // Write keys first to ensure they exist
        const leader = nodes.find(n => n.state === 'LEADER' && n.active) || nodes[0]
        const baseUrl = `http://localhost:${leader.port}`

        // Write 10 keys (key1 to key10)
        for (let i = 1; i <= 10; i++) {
            await axios.post(`${baseUrl}/cache/key${i}`, { 
                value: i <= 5 ? "hot" : "cold", 
                clientId: "traffic", 
                sequenceNumber: Date.now() 
            })
        }

        // Access key1-key5 frequently (Hot)
        for(let i=0; i<5; i++) {
            for (let k = 1; k <= 5; k++) {
                await axios.get(`${baseUrl}/cache/key${k}`)
            }
        }
        
        // Access key6-key10 once or rarely (Cold)
        for (let k = 6; k <= 10; k++) {
             await axios.get(`${baseUrl}/cache/key${k}`)
        }
        
        setResult({ success: true, message: "Traffic generation complete (10 keys). Check ML Stats." })
    } catch {
        setResult({ success: false, message: "Traffic generation failed" })
    } finally {
        setLoading(false)
    }
  }

  const handleDelete = async () => {
    setLoading(true)
    setResult(null)
    try {
      const url = getLeaderUrl()
      await axios.delete(`${url}/cache/${key}`, {
        data: {
          clientId: 'demo-ui',
          sequenceNumber: Date.now()
        }
      })
      setResult({ success: true, message: `Deleted ${key} from Leader` })
      onOperationComplete()
    } catch (e) {
      setResult({ success: false, message: e.response?.data?.message || e.message })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem' }}>
        <input 
          type="text" 
          value={key} 
          onChange={e => setKey(e.target.value)}
          placeholder="Key"
          style={{ 
            background: 'rgba(255,255,255,0.05)', 
            border: '1px solid var(--border-color)', 
            padding: '0.5rem', 
            color: 'white', 
            borderRadius: '4px' 
          }}
        />
        <input 
          type="text" 
          value={value} 
          onChange={e => setValue(e.target.value)}
          placeholder="Value"
          style={{ 
            background: 'rgba(255,255,255,0.05)', 
            border: '1px solid var(--border-color)', 
            padding: '0.5rem', 
            color: 'white', 
            borderRadius: '4px' 
          }}
        />
      </div>

      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
        <button className="btn btn-primary" onClick={handleWrite} disabled={loading}>
          <Send size={16} /> Write
        </button>
        <button className="btn btn-danger" onClick={handleDelete} disabled={loading} style={{ background: 'rgba(255, 77, 77, 0.2)', color: 'var(--error)', border: '1px solid var(--error)' }}>
          <Trash2 size={16} /> Delete
        </button>
        <button className="btn" onClick={() => handleRead('strong')} disabled={loading}>
          <Database size={16} /> Strong Read
        </button>
        <button className="btn btn-success" onClick={() => handleRead('lease')} disabled={loading}>
          <Zap size={16} /> Lease Read
        </button>
         <button className="btn" onClick={generateTraffic} disabled={loading} style={{ marginLeft: 'auto' }}>
          <RefreshCw size={16} /> Generate Traffic
        </button>
      </div>

      {result && (
        <div style={{ 
          marginTop: '0.5rem', 
          padding: '0.75rem', 
          borderRadius: '6px', 
          background: result.success ? 'rgba(0, 255, 157, 0.1)' : 'rgba(255, 77, 77, 0.1)',
          border: `1px solid ${result.success ? 'var(--success)' : 'var(--error)'}`,
          fontSize: '0.9rem'
        }}>
          <div style={{ fontWeight: 'bold', color: result.success ? 'var(--success)' : 'var(--error)' }}>
            {result.success ? 'Success' : 'Error'}
          </div>
          <div>{result.message}</div>
          {result.details && <div style={{ marginTop: '0.25rem', fontSize: '0.8rem', opacity: 0.8 }}>{result.details}</div>}
        </div>
      )}
    </div>
  )
}

export default Controls
