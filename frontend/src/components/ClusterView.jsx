import React from 'react'
import { Server, Crown, Database, Clock } from 'lucide-react'
import { motion } from 'framer-motion' // eslint-disable-line no-unused-vars

const NodeCard = ({ node }) => {
  const isLeader = node.state === 'LEADER'
  const isDown = node.state === 'DOWN' || node.state === 'UNKNOWN'
  
  return (
    <motion.div 
      className={`card ${isLeader ? 'border-blue-500' : ''}`}
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      style={{ 
        borderColor: isLeader ? 'var(--accent-blue)' : isDown ? 'var(--error)' : 'var(--border-color)',
        opacity: isDown ? 0.7 : 1,
        position: 'relative',
        overflow: 'hidden'
      }}
    >
      {isLeader && (
        <div style={{ 
          position: 'absolute', 
          top: 0, 
          right: 0, 
          background: 'var(--accent-blue)', 
          color: '#000', 
          padding: '0.25rem 0.75rem', 
          borderBottomLeftRadius: '8px',
          fontWeight: 'bold',
          fontSize: '0.75rem',
          display: 'flex',
          alignItems: 'center',
          gap: '0.25rem'
        }}>
          <Crown size={12} /> LEADER
        </div>
      )}

      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1rem' }}>
        <div style={{ 
          background: isDown ? 'rgba(255, 77, 77, 0.1)' : 'rgba(255, 255, 255, 0.05)', 
          padding: '0.75rem', 
          borderRadius: '50%',
          color: isDown ? 'var(--error)' : 'var(--text-primary)'
        }}>
          <Server size={24} />
        </div>
        <div>
          <h3 style={{ margin: 0, fontSize: '1.1rem' }}>{node.id}</h3>
          <span style={{ 
            fontSize: '0.8rem', 
            color: isDown ? 'var(--error)' : 'var(--text-secondary)' 
          }}>
            {node.httpAddress || `localhost:${node.port}`}
          </span>
        </div>
      </div>

      <div style={{ display: 'grid', gap: '0.75rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.9rem' }}>
          <span style={{ color: 'var(--text-secondary)' }}>Term</span>
          <span style={{ fontFamily: 'monospace', color: 'var(--accent-purple)' }}>{node.currentTerm || node.term}</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.9rem' }}>
          <span style={{ color: 'var(--text-secondary)' }}>Log Index</span>
          <span style={{ fontFamily: 'monospace', color: 'var(--accent-green)' }}>{node.commitIndex || 0}</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.9rem' }}>
          <span style={{ color: 'var(--text-secondary)' }}>Role</span>
          <span className={
            node.state === 'LEADER' ? 'badge badge-leader' : 
            node.state === 'CANDIDATE' ? 'badge badge-candidate' : 
            'badge badge-follower'
          }>
            {node.state}
          </span>
        </div>
      </div>

      {node.hasValidReadLease && (
        <div style={{ 
          marginTop: '1rem', 
          padding: '0.5rem', 
          background: 'rgba(0, 255, 157, 0.1)', 
          borderRadius: '6px',
          display: 'flex', 
          alignItems: 'center', 
          gap: '0.5rem',
          fontSize: '0.8rem',
          color: 'var(--accent-green)'
        }}>
          <Clock size={14} />
          <span>Read Lease Active ({node.leaseRemainingMs}ms)</span>
        </div>
      )}
    </motion.div>
  )
}

const ClusterView = ({ nodes }) => {
  return (
    <>
      {nodes.map(node => (
        <NodeCard key={node.id} node={node} />
      ))}
    </>
  )
}

export default ClusterView
