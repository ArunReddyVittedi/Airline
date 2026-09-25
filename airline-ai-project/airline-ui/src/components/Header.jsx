import { NavLink, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import { clearSession } from '../api.js'

/**
 * Two-tier navigation separating account controls from booking and support journeys.
 */
export default function Header({ user, onSignedOut }) {
  const [menuOpen, setMenuOpen] = useState(false)
  const navigate = useNavigate()

  function signOut() {
    clearSession()
    onSignedOut()
    setMenuOpen(false)
    navigate('/login')
  }

  return (
    <header className="site-header">
      <div className="topbar">
        <div className="shell topbar-inner">
          <span className="topbar-note">Fly across India. Change or cancel free up to 24 hours before departure.</span>

          {user ? (
            <div className="account">
              {/* Staff have no frequent flyer tier. The default BLUE next to "Ops Desk" is
                  not a tier, it is a column that happened to have a value in it. */}
              {user.role !== 'ADMIN' && (
                <span className={`tier tier-${user.tier?.toLowerCase() || 'blue'}`}>{user.tier}</span>
              )}
              {user.role === 'ADMIN' && <span className="staff-tag">Staff</span>}
              <span className="account-name">{user.fullName}</span>
              <button className="link-btn" onClick={signOut}>
                Sign out
              </button>
            </div>
          ) : (
            <NavLink to="/login" className="link-btn">
              Sign in
            </NavLink>
          )}
        </div>
      </div>

      <div className="navbar">
        <div className="shell navbar-inner">
          <NavLink to="/" className="brand" aria-label="Telusko Airlines home">
            <Logo />
            <span>
              Telusko <strong>Airlines</strong>
            </span>
          </NavLink>

          {/* Hidden on wide screens by CSS. A hamburger that is always visible is a
              phone design bolted onto a desktop. */}
          <button
            className="nav-toggle"
            onClick={() => setMenuOpen((open) => !open)}
            aria-expanded={menuOpen}
            aria-label="Menu"
          >
            <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
              <path d="M4 7h16M4 12h16M4 17h16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
            </svg>
          </button>

          <nav className={`main-nav ${menuOpen ? 'open' : ''}`} onClick={() => setMenuOpen(false)}>
            <NavLink to="/">Book</NavLink>
            {user && <NavLink to="/trips">My trips</NavLink>}
            {user && <NavLink to="/plan">Plan a trip</NavLink>}
            {user && <NavLink to="/assistant">Help</NavLink>}
            {user && <NavLink to="/disruption">Disruption desk</NavLink>}
            {user && <NavLink to="/support">Support</NavLink>}
            {user?.role === 'ADMIN' && <NavLink to="/admin" className="nav-ops">Operations</NavLink>}
          </nav>
        </div>
      </div>
    </header>
  )
}

/**
 * The mark: a tail fin. Drawn rather than an image file so it stays sharp at any size and
 * inherits the brand colour from CSS instead of being baked into a PNG.
 */
function Logo() {
  return (
    <svg viewBox="0 0 32 32" width="30" height="30" aria-hidden="true" className="logo-mark">
      <path d="M4 24 L20 4 h6 l-6 20 z" fill="currentColor" />
      <path d="M4 24 h16 l-2 4 H8 z" fill="currentColor" opacity="0.55" />
    </svg>
  )
}
