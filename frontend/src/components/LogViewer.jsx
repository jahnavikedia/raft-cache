import React from 'react'
import { Scroll, Terminal } from 'lucide-react'

const LogViewer = ({ logs }) => {
  // Reverse logs to show recent first
  const reversedLogs = [...logs].reverse()

  return (
    <div className="card" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem', borderBottom: '1px solid var(--border-color)', paddingBottom: '1rem' }}>
        <Terminal size={20} color="var(--accent-green)" />
        <h2 style={{ margin: 0, fontSize: '1.25rem' }}>Raft Log</h2>
        <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginLeft: '0.5rem' }}>
          ({logs.length} entries)
        </span>
      </div>

      <div style={{
        maxHeight: '300px',
        overflowY: 'auto',
        fontFamily: 'monospace',
        fontSize: '0.85rem',
        display: 'flex',
        flexDirection: 'column',
        gap: '0.5rem',
        paddingRight: '0.5rem'
      }}>
        {reversedLogs.length === 0 ? (
          <div style={{ color: 'var(--text-secondary)', fontStyle: 'italic', textAlign: 'center', marginTop: '2rem' }}>
            No log entries yet.
          </div>
        ) : (
          reversedLogs.map((log, i) => {
            // Parse command if possible (e.g., "SET key=value")
            let action = "UNKNOWN"
            let details = log.command
            
            try {
                if (log.type === "NO_OP") {
                    action = "SYSTEM"
                    details = "Election / Heartbeat"
                } else if (log.command && log.command.startsWith("{")) {
                    const cmdObj = JSON.parse(log.command)
                    action = cmdObj.type
                    if (action === "PUT") {
                        action = "WRITE"
                        details = `${cmdObj.key}=${cmdObj.value}`
                    } else if (action === "DELETE") {
                        details = cmdObj.key
                    } else if (action === "GET") {
                         details = cmdObj.key
                    }
                } else if (log.command && log.command.startsWith("SET")) {
                     // Legacy/Fallback parsing
                    action = "WRITE"
                    const parts = log.command.split(" ")
                    if (parts.length > 1) details = parts.slice(1).join(" ")
                }
            } catch (e) {
                // Keep default
            }

            return (
            <div key={i} className="log-entry" style={{ display: 'grid', gridTemplateColumns: '40px 40px 60px 1fr', gap: '1rem', alignItems: 'center' }}>
              <span style={{ color: 'var(--text-secondary)', fontSize: '0.8rem' }}>#{log.index}</span>
              <span style={{ color: 'var(--accent-purple)', fontSize: '0.8rem' }}>T{log.term}</span>
              <span className={`badge ${action === 'WRITE' ? 'badge-leader' : action === 'DELETE' ? 'badge-danger' : action === 'SYSTEM' ? 'badge-neutral' : 'badge-follower'}`} style={{ fontSize: '0.7rem', justifyContent: 'center', background: action === 'SYSTEM' ? 'rgba(255,255,255,0.1)' : undefined, color: action === 'SYSTEM' ? '#aaa' : undefined }}>{action}</span>
              <span style={{ color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{details}</span>
            </div>
          )})
        )
        }
      </div>
    </div>
  )
}

export default LogViewer
