import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth';
import Brand from './Brand';


export default function HomeHeader() {
  const { user } = useAuth();

  return (
    <header className="home-header">
      <div className="home-header__inner">
        <Link className="home-logo-link" to="/" aria-label="Go to home page">
          <Brand />
        </Link>
        <Link className="home-login-link" to={user ? '/dashboard' : '/signin'}>
          {user ? 'Dashboard' : 'Login'}
        </Link>
      </div>
    </header>
  );
}