import { Crown, Database, Brain, Clock, GitBranch } from 'lucide-react'

const Navbar = ({ activePage, setActivePage }) => {
  const pages = [
    { id: 'election', label: 'Raft Election', icon: Crown },
    { id: 'replication', label: 'Log Replication', icon: GitBranch },
    { id: 'cache', label: 'Cache Operations', icon: Database },
    { id: 'performance', label: 'Performance', icon: Clock },
    { id: 'ml', label: 'ML Eviction', icon: Brain },
  ]

  return (
    <nav className="navbar">
      <div className="nav-links">
        {pages.map(page => (
          <button
            key={page.id}
            className={`nav-link ${activePage === page.id ? 'active' : ''}`}
            onClick={() => setActivePage(page.id)}
          >
            <page.icon size={16} />
            {page.label}
          </button>
        ))}
      </div>
    </nav>
  )
}

export default Navbar
