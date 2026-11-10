import React from 'react';
import { BrowserRouter, Route, Routes, Navigate, useLocation } from 'react-router-dom';

import { AuthProvider, useAuth } from './auth';
import Nav from './components/Nav';
import { ToastProvider } from './utils/toast';

const Home = React.lazy(() => import('./pages/Home'));
const SignIn = React.lazy(() => import('./pages/SignIn'));
const Dashboard = React.lazy(() => import('./pages/Dashboard'));
const AddWorkout = React.lazy(() => import('./pages/AddWorkout'));
const EditWorkout = React.lazy(() => import('./pages/EditWorkout'));
const History = React.lazy(() => import('./pages/History'));
const Settings = React.lazy(() => import('./pages/Settings'));

function RequireAuth({ children }) {
  const { user, loading } = useAuth();
  if (loading) {
    return <div className="loading-line" style={{ padding: 16 }}>Loading…</div>;
  }
  if (!user) {
    return <Navigate to="/signin" replace />;
  }
  return children;
}

function Layout({ children }) {
  const location = useLocation();
  const showDashboardChrome = location.pathname !== '/' && location.pathname !== '/signin';

  React.useEffect(() => {
    if (showDashboardChrome) {
      document.body.classList.add('dashboard-body');
      document.body.classList.remove('login-body');
      return () => {
        document.body.classList.remove('dashboard-body');
      };
    }

    document.body.classList.remove('dashboard-body');
    return undefined;
  }, [showDashboardChrome]);

  return (
    <>
      {showDashboardChrome ? <Nav /> : null}
      {children}
    </>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <BrowserRouter>
        <Layout>
          <React.Suspense fallback={<div className="loading-line" style={{ padding: 16 }}>Loading…</div>}>
            <Routes>
              <Route path="/" element={<Home />} />
              <Route path="/signin" element={<SignIn />} />
              <Route
                path="/dashboard"
                element={(
                  <RequireAuth>
                    <Dashboard />
                  </RequireAuth>
                )}
              />
              <Route
                path="/workouts/new"
                element={(
                  <RequireAuth>
                    <AddWorkout />
                  </RequireAuth>
                )}
              />
              <Route
                path="/workouts/:id/edit"
                element={(
                  <RequireAuth>
                    <EditWorkout />
                  </RequireAuth>
                )}
              />
              <Route
                path="/history"
                element={(
                  <RequireAuth>
                    <History />
                  </RequireAuth>
                )}
              />
              <Route
                path="/settings"
                element={(
                  <RequireAuth>
                    <Settings />
                  </RequireAuth>
                )}
              />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </React.Suspense>
        </Layout>
      </BrowserRouter>
      </ToastProvider>
    </AuthProvider>
  );
}
