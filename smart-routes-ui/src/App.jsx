import { lazy } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './context/contexts';
import AlertsProvider from './context/AlertsProvider';
import Layout from './components/Layout';
import { PageLoader } from './components/ui';
import AuthPage from './pages/AuthPage';
import PlannerPage from './pages/PlannerPage';

// Pages other than the planner load on first visit, keeping the first download small.
const RoutesPage = lazy(() => import('./pages/RoutesPage'));
const RouteDetailPage = lazy(() => import('./pages/RouteDetailPage'));
const AlertsPage = lazy(() => import('./pages/AlertsPage'));
const AccountPage = lazy(() => import('./pages/AccountPage'));

function RequireAuth({ children }) {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'loading') {
    return (
      <div className="h-dvh bg-slate-950">
        <PageLoader label="Signing you in…" />
      </div>
    );
  }
  if (status !== 'authenticated') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return children;
}

function GuestOnly({ children }) {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'authenticated') {
    return <Navigate to={location.state?.from ?? '/'} replace />;
  }
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<GuestOnly><AuthPage mode="login" /></GuestOnly>} />
      <Route path="/signup" element={<GuestOnly><AuthPage mode="signup" /></GuestOnly>} />
      <Route
        element={
          <RequireAuth>
            <AlertsProvider>
              <Layout />
            </AlertsProvider>
          </RequireAuth>
        }
      >
        <Route index element={<PlannerPage />} />
        <Route path="routes" element={<RoutesPage />} />
        <Route path="routes/:routeId" element={<RouteDetailPage />} />
        <Route path="alerts" element={<AlertsPage />} />
        <Route path="account" element={<AccountPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
