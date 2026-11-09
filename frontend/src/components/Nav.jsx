import React from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { signOut } from '../api';
import { useAuth } from '../auth';
import '../styles/components/Nav.css';

export default function Nav() {
  const navigate = useNavigate();
  const { user, setUser } = useAuth();
  const [mobileOpen, setMobileOpen] = React.useState(false);

  React.useEffect(() => {
    if (!user) return undefined;
    document.body.classList.add('dashboard-body');
    document.body.classList.remove('login-body');
    return () => {
      document.body.classList.remove('dashboard-body');
    };
  }, [user]);

  async function onLogout() {
    setMobileOpen(false);
    try {
      await signOut();
    } catch {
      setUser(null);
      navigate('/', { replace: true });
    }
  }

  function toggleMobileMenu() {
    setMobileOpen((v) => !v);
  }

  function closeMobileMenu() {
    setMobileOpen(false);
  }

  React.useEffect(() => {
    if (!mobileOpen) return undefined;
    function onKeyDown(e) {
      if (e.key === 'Escape') setMobileOpen(false);
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [mobileOpen]);

  if (!user) return null;

  return (
    <nav className="sidebar" aria-label="Primary">
      <div className="sidebar-header">
        <Link className="sidebar-logo-link" to="/" aria-label="Go to home page">
          <img className="sidebar-logo-img" src="/MomentumLogo.png" alt="Momentum logo" />
        </Link>

        <button
          type="button"
          className="mobile-menu-btn"
          aria-label={mobileOpen ? 'Close menu' : 'Open menu'}
          aria-expanded={mobileOpen}
          onClick={toggleMobileMenu}
        >
          <span className={mobileOpen ? 'mobile-menu-icon mobile-menu-icon--open' : 'mobile-menu-icon'} aria-hidden="true" />
        </button>
      </div>

      <div className="sidebar-nav">
        <NavLink to="/dashboard" className={({ isActive }) => (isActive ? 'is-active' : undefined)}>Dashboard</NavLink>
        <NavLink to="/workouts/new" className={({ isActive }) => (isActive ? 'is-active' : undefined)}>Add Workout</NavLink>
        <NavLink to="/history" className={({ isActive }) => (isActive ? 'is-active' : undefined)}>Workout History</NavLink>
        <NavLink to="/settings" className={({ isActive }) => (isActive ? 'is-active' : undefined)}>Settings</NavLink>
      </div>

      <div className="sidebar-footer">
        <div className="sidebar-profile" aria-label="Signed in user">
          <div className="sidebar-profile__label">Signed in as</div>
          <div className="sidebar-profile__name">{user.username}</div>
        </div>

        <div className="logout-form-container">
          <button type="button" className="logout-btn" onClick={onLogout}>Logout</button>
        </div>
      </div>

      <div className={mobileOpen ? 'mobile-menu mobile-menu--open' : 'mobile-menu'} aria-hidden={!mobileOpen}>
        <div className="mobile-menu__content" role="dialog" aria-label="Menu">
          <div className="mobile-menu__header">
            <Link className="mobile-menu__brand" to="/" aria-label="Go to home page" onClick={closeMobileMenu}>
              <img className="mobile-menu__logo" src="/MomentumLogo.png" alt="Momentum logo" />
            </Link>
            <button
              type="button"
              className="mobile-menu__close"
              aria-label="Close menu"
              onClick={closeMobileMenu}
            >
              ×
            </button>
          </div>
          <div className="mobile-menu__nav" aria-label="Mobile navigation">
            <NavLink to="/dashboard" className={({ isActive }) => (isActive ? 'is-active' : undefined)} onClick={closeMobileMenu}>Dashboard</NavLink>
            <NavLink to="/workouts/new" className={({ isActive }) => (isActive ? 'is-active' : undefined)} onClick={closeMobileMenu}>Add Workout</NavLink>
            <NavLink to="/history" className={({ isActive }) => (isActive ? 'is-active' : undefined)} onClick={closeMobileMenu}>Workout History</NavLink>
            <NavLink to="/settings" className={({ isActive }) => (isActive ? 'is-active' : undefined)} onClick={closeMobileMenu}>Settings</NavLink>
          </div>
          <div className="mobile-menu__footer">
            <div className="mobile-menu__profile" aria-label="Signed in user">{user.username}</div>
            <button type="button" className="logout-btn" onClick={onLogout}>Logout</button>
          </div>
        </div>
      </div>
    </nav>
  );
}
