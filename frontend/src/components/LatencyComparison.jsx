import React, { useState } from 'react'
import { Zap, Clock, BarChart3 } from 'lucide-react'
import axios from 'axios'

const LatencyComparison = ({ nodes, testKey }) => {
  const [results, setResults] = useState(null)
  const [loading, setLoading] = useState(false)

  const runBenchmark = async () => {
    setLoading(true)
    setResults(null)

    try {
      const leader = nodes.find(n => n.state === 'LEADER' && n.active) || nodes[0]
      const baseUrl = `http://localhost:${leader.port}`

      // Use key from control panel, or create one if cache is empty
      let keyToTest = testKey || 'test_key'

      // Check if key exists, if not create it
      try {
        await axios.get(`${baseUrl}/cache/${keyToTest}?consistency=eventual`)
      } catch {
        await axios.post(`${baseUrl}/cache/${keyToTest}`, {
          value: 'test_value',
          clientId: 'latency-test',
          sequenceNumber: Date.now()
        })
      }

      // Run Strong reads
      const strongLatencies = []
      for (let i = 0; i < 5; i++) {
        const start = performance.now()
        await axios.get(`${baseUrl}/cache/${keyToTest}?consistency=strong`)
        strongLatencies.push(performance.now() - start)
      }

      // Run Lease reads
      const leaseLatencies = []
      for (let i = 0; i < 5; i++) {
        const start = performance.now()
        await axios.get(`${baseUrl}/cache/${keyToTest}?consistency=lease`)
        leaseLatencies.push(performance.now() - start)
      }

      const avgStrong = strongLatencies.reduce((a, b) => a + b, 0) / strongLatencies.length
      const avgLease = leaseLatencies.reduce((a, b) => a + b, 0) / leaseLatencies.length
      const speedup = avgStrong / avgLease

      setResults({
        strong: avgStrong.toFixed(2),
        lease: avgLease.toFixed(2),
        speedup: speedup.toFixed(1),
        testKey: keyToTest
      })
    } catch (e) {
      console.error("Benchmark failed", e)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="card">
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '1rem' }}>
        <BarChart3 size={20} color="var(--accent-green)" />
        <h2 style={{ margin: 0, fontSize: '1.25rem' }}>Read Latency</h2>
      </div>

      <button
        className="btn"
        onClick={runBenchmark}
        disabled={loading}
        style={{ width: '100%', justifyContent: 'center', marginBottom: '1rem' }}
      >
        <Zap size={16} /> {loading ? 'Running...' : 'Compare Strong vs Lease'}
      </button>

      {results && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '0.75rem',
            background: 'rgba(255, 77, 77, 0.1)',
            borderRadius: '6px',
            border: '1px solid rgba(255, 77, 77, 0.3)'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Clock size={16} color="var(--error)" />
              <span>Strong Read</span>
            </div>
            <span style={{ fontFamily: 'monospace', fontWeight: 'bold' }}>{results.strong}ms</span>
          </div>

          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '0.75rem',
            background: 'rgba(0, 255, 157, 0.1)',
            borderRadius: '6px',
            border: '1px solid rgba(0, 255, 157, 0.3)'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Zap size={16} color="var(--success)" />
              <span>Lease Read</span>
            </div>
            <span style={{ fontFamily: 'monospace', fontWeight: 'bold' }}>{results.lease}ms</span>
          </div>

          <div style={{
            textAlign: 'center',
            padding: '0.75rem',
            background: 'rgba(0, 240, 255, 0.1)',
            borderRadius: '6px',
            border: '1px solid rgba(0, 240, 255, 0.3)'
          }}>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '0.25rem' }}>
              Lease Speedup
            </div>
            <div style={{ fontSize: '1.5rem', fontWeight: 'bold', color: 'var(--accent-blue)' }}>
              {results.speedup}x faster
            </div>
          </div>

          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', textAlign: 'center', marginTop: '0.5rem' }}>
            Tested with key: <span style={{ color: 'var(--accent-green)', fontFamily: 'monospace' }}>{results.testKey}</span>
          </div>
        </div>
      )}

      {!results && !loading && (
        <div style={{ textAlign: 'center', color: 'var(--text-secondary)', fontSize: '0.85rem', padding: '1rem' }}>
          Compare read latencies between Strong (quorum) and Lease (local) reads.
        </div>
      )}
    </div>
  )
}

export default LatencyComparison
