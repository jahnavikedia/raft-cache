import React, { useState } from 'react'
import { ChevronRight, ChevronDown, CheckCircle, Circle, Sparkles, Database, Zap, Brain, Crown, GitBranch } from 'lucide-react'

const features = [
  {
    id: 'consensus',
    title: 'Raft Consensus',
    icon: Crown,
    color: 'var(--accent-blue)',
    steps: [
      'Look at the Cluster Status - one node is marked as LEADER',
      'The leader coordinates all writes and replicates to followers',
      'Try the Election Demo: Kill the leader and watch a new one get elected',
      'Notice the Term number increases after each election'
    ]
  },
  {
    id: 'replication',
    title: 'Log Replication',
    icon: GitBranch,
    color: 'var(--accent-green)',
    steps: [
      'Use the Control Panel to PUT a key-value pair',
      'Watch the Raft Log update with the new entry',
      'The log shows: Term | Index | Command | Status',
      'All nodes replicate the same log for consistency'
    ]
  },
  {
    id: 'cache',
    title: 'Distributed Cache',
    icon: Database,
    color: 'var(--accent-pink)',
    steps: [
      'PUT: Store a key-value pair (replicated via Raft)',
      'GET: Retrieve a value by key',
      'DELETE: Remove a key from the cache',
      'Watch the Cache Contents section update in real-time'
    ]
  },
  {
    id: 'lease',
    title: 'Read Lease Optimization',
    icon: Zap,
    color: 'var(--warning)',
    steps: [
      'Click "Run Benchmark" in the Read Latency section',
      'Compare Strong Consistency vs Lease reads',
      'Strong reads: Go to leader, verify with followers (~5-10ms)',
      'Lease reads: Direct from leader if lease valid (~0.5ms)',
      'Notice the ~10-100x speedup with leases!'
    ]
  },
  {
    id: 'ml',
    title: 'ML-Based Eviction',
    icon: Brain,
    color: 'var(--accent-purple)',
    steps: [
      'Scroll to "Eviction Strategy Comparison" section',
      'Click "Run Comparison Demo" to see LRU vs ML in action',
      'Watch as it creates keys with different access patterns',
      'See how LRU might evict a frequently-used key just because it\'s "old"',
      'ML correctly identifies the truly cold key based on access frequency',
      'The comparison shows why ML eviction improves cache hit rates'
    ]
  }
]

const DemoGuide = () => {
  const [expandedFeature, setExpandedFeature] = useState('consensus')
  const [completedSteps, setCompletedSteps] = useState({})

  const toggleFeature = (featureId) => {
    setExpandedFeature(expandedFeature === featureId ? null : featureId)
  }

  const toggleStep = (featureId, stepIndex) => {
    const key = `${featureId}-${stepIndex}`
    setCompletedSteps(prev => ({
      ...prev,
      [key]: !prev[key]
    }))
  }

  const getCompletedCount = (featureId) => {
    const feature = features.find(f => f.id === featureId)
    if (!feature) return 0
    return feature.steps.filter((_, i) => completedSteps[`${featureId}-${i}`]).length
  }

  return (
    <div className="card" style={{ background: 'linear-gradient(135deg, rgba(255,255,255,0.02) 0%, rgba(255,255,255,0.01) 100%)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.5rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '1rem' }}>
        <Sparkles size={24} color="var(--warning)" />
        <h2 style={{ margin: 0, fontSize: '1.5rem' }}>Demo Guide</h2>
        <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginLeft: 'auto' }}>
          Click each feature to see demo steps
        </span>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        {features.map((feature) => {
          const Icon = feature.icon
          const isExpanded = expandedFeature === feature.id
          const completed = getCompletedCount(feature.id)
          const total = feature.steps.length

          return (
            <div
              key={feature.id}
              style={{
                border: `1px solid ${isExpanded ? feature.color : 'var(--border-color)'}`,
                borderRadius: '8px',
                overflow: 'hidden',
                transition: 'all 0.2s'
              }}
            >
              <div
                onClick={() => toggleFeature(feature.id)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '1rem',
                  padding: '1rem',
                  cursor: 'pointer',
                  background: isExpanded ? `${feature.color}10` : 'transparent'
                }}
              >
                <Icon size={20} color={feature.color} />
                <span style={{ fontWeight: 'bold', flex: 1 }}>{feature.title}</span>
                <span style={{
                  fontSize: '0.8rem',
                  color: completed === total ? 'var(--accent-green)' : 'var(--text-secondary)',
                  background: 'rgba(255,255,255,0.05)',
                  padding: '0.25rem 0.5rem',
                  borderRadius: '4px'
                }}>
                  {completed}/{total}
                </span>
                {isExpanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
              </div>

              {isExpanded && (
                <div style={{
                  padding: '0 1rem 1rem 1rem',
                  borderTop: '1px solid var(--border-color)'
                }}>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginTop: '1rem' }}>
                    {feature.steps.map((step, index) => {
                      const stepKey = `${feature.id}-${index}`
                      const isDone = completedSteps[stepKey]

                      return (
                        <div
                          key={index}
                          onClick={() => toggleStep(feature.id, index)}
                          style={{
                            display: 'flex',
                            alignItems: 'flex-start',
                            gap: '0.75rem',
                            padding: '0.5rem',
                            borderRadius: '6px',
                            cursor: 'pointer',
                            background: isDone ? 'rgba(0, 255, 157, 0.05)' : 'rgba(255,255,255,0.02)',
                            transition: 'all 0.2s'
                          }}
                        >
                          {isDone ? (
                            <CheckCircle size={18} color="var(--accent-green)" style={{ flexShrink: 0, marginTop: '2px' }} />
                          ) : (
                            <Circle size={18} color="var(--text-secondary)" style={{ flexShrink: 0, marginTop: '2px' }} />
                          )}
                          <span style={{
                            fontSize: '0.9rem',
                            color: isDone ? 'var(--text-secondary)' : 'var(--text-primary)',
                            textDecoration: isDone ? 'line-through' : 'none',
                            lineHeight: 1.5
                          }}>
                            {step}
                          </span>
                        </div>
                      )
                    })}
                  </div>
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

export default DemoGuide
