import React from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { SIGN_IN_URL } from '../api';
import { useAuth } from '../auth';
import Brand from '../components/Brand';
import { usePageMeta } from '../utils/pageMeta';

export default function SignIn() {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, loading } = useAuth();
  const failed = new URLSearchParams(location.search || '').has('error');

  usePageMeta({ title: 'Momentum — Sign In', description: 'Sign in to Momentum to track workouts.' });

  React.useEffect(() => {
    document.body.classList.add('login-body');
    document.body.classList.remove('dashboard-body');
    return () => {
      document.body.classList.remove('login-body');
    };
  }, []);

  React.useEffect(() => {
    if (!loading && user) navigate('/dashboard', { replace: true });
  }, [loading, user, navigate]);

  return (
    <div className="signin-page">
      <div className="signin-content">
        <Link className="signin-logo-link" to="/" aria-label="Go to home page">
          <Brand size="lg" />
        </Link>
        <div className="login-form">
          <p className="kicker">Welcome back</p>
          <h1 className="login-form__title">Sign in</h1>
          {failed ? <div className="error-message">Sign-in could not be completed. Please try again.</div> : null}
          <div className="button-group">
            <a className="btn-login" href={SIGN_IN_URL}>
              Sign in or create an account
            </a>
          </div>
        </div>
      </div>
    </div>
  );
}
