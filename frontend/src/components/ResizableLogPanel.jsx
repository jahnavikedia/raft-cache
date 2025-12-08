import { useState, useRef, useEffect } from 'react'
import { Clock, ChevronDown, ChevronUp } from 'lucide-react'
import LogViewer from './LogViewer'

const ResizableLogPanel = ({ logs }) => {
  const [height, setHeight] = useState(300)
  const [isCollapsed, setIsCollapsed] = useState(false)
  const [isDragging, setIsDragging] = useState(false)
  const dragRef = useRef(null)

  useEffect(() => {
    const handleMouseMove = (e) => {
      if (!isDragging) return
      
      const newHeight = window.innerHeight - e.clientY
      if (newHeight >= 100 && newHeight <= window.innerHeight - 200) {
        setHeight(newHeight)
      }
    }

    const handleMouseUp = () => {
      setIsDragging(false)
    }

    if (isDragging) {
      document.addEventListener('mousemove', handleMouseMove)
      document.addEventListener('mouseup', handleMouseUp)
      document.body.style.cursor = 'ns-resize'
      document.body.style.userSelect = 'none'
    }

    return () => {
      document.removeEventListener('mousemove', handleMouseMove)
      document.removeEventListener('mouseup', handleMouseUp)
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }
  }, [isDragging])

  return (
    <div style={{
      position: 'fixed',
      bottom: 0,
      left: 0,
      right: 0,
      height: isCollapsed ? '40px' : `${height}px`,
      background: 'var(--bg-dark)',
      borderTop: '1px solid var(--border-color)',
      zIndex: 1000,
      display: 'flex',
      flexDirection: 'column',
      transition: isCollapsed ? 'height 0.2s ease' : 'none'
    }}>
      {/* Drag Handle */}
      <div
        ref={dragRef}
        onMouseDown={() => !isCollapsed && setIsDragging(true)}
        style={{
          height: '40px',
          background: 'var(--bg-card)',
          borderBottom: '1px solid var(--border-color)',
          cursor: isCollapsed ? 'pointer' : 'ns-resize',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 1.5rem',
          userSelect: 'none'
        }}
        onClick={() => isCollapsed && setIsCollapsed(false)}
      >
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '0.5rem',
          fontSize: '0.9rem',
          color: 'var(--text-secondary)',
          textTransform: 'uppercase',
          letterSpacing: '0.05em',
          fontWeight: 500
        }}>
          <Clock size={16} />
          <span>Raft Consensus Log</span>
          <span style={{ 
            fontSize: '0.75rem', 
            color: 'var(--text-secondary)', 
            opacity: 0.6 
          }}>
            ({logs.length} entries)
          </span>
        </div>
        
        <button
          onClick={(e) => {
            e.stopPropagation()
            setIsCollapsed(!isCollapsed)
          }}
          style={{
            background: 'transparent',
            border: 'none',
            color: 'var(--text-secondary)',
            cursor: 'pointer',
            padding: '0.25rem',
            display: 'flex',
            alignItems: 'center',
            transition: 'color 0.2s'
          }}
          onMouseEnter={(e) => e.target.style.color = 'var(--text-primary)'}
          onMouseLeave={(e) => e.target.style.color = 'var(--text-secondary)'}
        >
          {isCollapsed ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
        </button>
      </div>

      {/* Log Content */}
      {!isCollapsed && (
        <div style={{
          flex: 1,
          overflow: 'hidden',
          padding: '1rem 1.5rem',
          background: 'var(--bg-dark)'
        }}>
          <div style={{
            height: '100%',
            overflowY: 'auto',
            fontFamily: 'monospace',
            fontSize: '0.85rem',
            display: 'flex',
            flexDirection: 'column',
            gap: '0.5rem',
            paddingRight: '0.5rem'
          }}>
            {[...logs].reverse().length === 0 ? (
              <div style={{ 
                color: 'var(--text-secondary)', 
                fontStyle: 'italic', 
                textAlign: 'center', 
                marginTop: '2rem' 
              }}>
                No log entries yet.
              </div>
            ) : (
              [...logs].reverse().map((log, i) => {
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
                    action = "WRITE"
                    const parts = log.command.split(" ")
                    if (parts.length > 1) details = parts.slice(1).join(" ")
                  }
                } catch (e) {
                  // Keep default
                }

                return (
                  <div 
                    key={i} 
                    style={{ 
                      display: 'grid', 
                      gridTemplateColumns: '50px 50px 70px 1fr', 
                      gap: '1rem', 
                      alignItems: 'center',
                      padding: '0.5rem',
                      borderBottom: '1px solid rgba(255,255,255,0.05)',
                      transition: 'background 0.2s'
                    }}
                    onMouseEnter={(e) => e.currentTarget.style.background = 'rgba(255,255,255,0.02)'}
                    onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                  >
                    <span style={{ color: 'var(--text-secondary)', fontSize: '0.8rem' }}>#{log.index}</span>
                    <span style={{ color: 'var(--accent-purple)', fontSize: '0.8rem' }}>T{log.term}</span>
                    <span 
                      className={`badge ${action === 'WRITE' ? 'badge-leader' : action === 'DELETE' ? 'badge-danger' : action === 'SYSTEM' ? 'badge-neutral' : 'badge-follower'}`} 
                      style={{ 
                        fontSize: '0.7rem', 
                        justifyContent: 'center', 
                        background: action === 'SYSTEM' ? 'rgba(255,255,255,0.1)' : undefined, 
                        color: action === 'SYSTEM' ? '#aaa' : undefined 
                      }}
                    >
                      {action}
                    </span>
                    <span style={{ 
                      color: 'var(--text-primary)', 
                      whiteSpace: 'nowrap', 
                      overflow: 'hidden', 
                      textOverflow: 'ellipsis' 
                    }}>
                      {details}
                    </span>
                  </div>
                )
              })
            )}
          </div>
        </div>
      )}
    </div>
  )
}

export default ResizableLogPanel
