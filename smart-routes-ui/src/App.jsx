import { lazy, Suspense } from 'react';
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
const LandingPage = lazy(() => import('./pages/LandingPage'));
const CoachPage = lazy(() => import('./pages/CoachPage'));

function RequireAuth({ children }) {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'loading') {
    return (
      <div className="h-dvh bg-canvas">
        <PageLoader label="Signing you in…" />
      </div>
    );
  }
  if (status !== 'authenticated') {
    // Signed-out visitors to the home page see what ClearWay is; anywhere else asks them to sign in.
    if (location.pathname === '/') {
      return (
        <Suspense fallback={<div className="h-dvh bg-canvas" />}>
          <LandingPage />
        </Suspense>
      );
    }
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
        <Route path="coach" element={<CoachPage />} />
        <Route path="alerts" element={<AlertsPage />} />
        <Route path="account" element={<AccountPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
