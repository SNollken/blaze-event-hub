import { lazy, Suspense } from 'react';
import { Routes, Route } from 'react-router-dom';
import { ErrorBoundary } from './components/ErrorBoundary';
import { t } from './i18n';

const NotFound = () => (
  <div className="p-8 text-center">
    <h2>404</h2>
    <p>{t('app.notFound')}</p>
  </div>
);

const Dashboard = lazy(() => import('./pages/Dashboard'));
const LiveEvents = lazy(() => import('./pages/LiveEvents'));
const BlazeChannel = lazy(() => import('./pages/BlazeChannel'));
const Alerts = lazy(() => import('./pages/Alerts'));
const Giveaways = lazy(() => import('./pages/Giveaways'));
const Overlays = lazy(() => import('./pages/Overlays'));

export default function App() {
  return (
    <ErrorBoundary>
      <Suspense fallback={<div className="skeleton-list m-6" />}>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/events" element={<LiveEvents />} />
          <Route path="/blaze" element={<BlazeChannel />} />
          <Route path="/alerts" element={<Alerts />} />
          <Route path="/giveaways" element={<Giveaways />} />
          <Route path="/overlays" element={<Overlays />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Suspense>
    </ErrorBoundary>
  );
}
