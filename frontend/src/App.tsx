import { lazy, Suspense } from 'react';
import { Navigate, Routes, Route } from 'react-router-dom';
import { ErrorBoundary } from './components/ErrorBoundary';
import { Layout } from './components/Layout';
import RequireAuth from './components/RequireAuth';
import { useI18n } from './i18n/I18nContext';

const Dashboard = lazy(() => import('./pages/Dashboard'));
const Events = lazy(() => import('./pages/Events'));
const CreateEvent = lazy(() => import('./pages/CreateEvent'));
const EditEvent = lazy(() => import('./pages/EditEvent'));
const EventDetail = lazy(() => import('./pages/EventDetail'));
const LiveDraw = lazy(() => import('./pages/LiveDraw'));
const EventResult = lazy(() => import('./pages/EventResult'));
const MyEvents = lazy(() => import('./pages/MyEvents'));
const Login = lazy(() => import('./pages/Login'));
const StudioChannel = lazy(() => import('./pages/StudioChannel'));
const HelpSupport = lazy(() => import('./pages/HelpSupport'));
const NotFound = lazy(() => import('./pages/NotFound'));

export default function App() {
  const { t } = useI18n();

  return (
    <ErrorBoundary>
      <Layout>
        <Suspense fallback={<div className="app-loading" role="status">{t('appLoading')}</div>}>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/events" element={<Events />} />
            <Route path="/events/create" element={<RequireAuth><CreateEvent /></RequireAuth>} />
            <Route path="/events/:id" element={<EventDetail />} />
            <Route path="/events/:id/manage" element={<RequireAuth><EditEvent /></RequireAuth>} />
            <Route path="/events/:id/edit" element={<RequireAuth><EditEvent /></RequireAuth>} />
            <Route path="/events/:id/draw" element={<RequireAuth><LiveDraw /></RequireAuth>} />
            <Route path="/events/:id/result" element={<EventResult />} />
            <Route path="/my-events" element={<RequireAuth><MyEvents /></RequireAuth>} />
            <Route path="/login" element={<Login />} />
            <Route path="/settings/blaze" element={<RequireAuth><StudioChannel /></RequireAuth>} />
            <Route path="/help" element={<HelpSupport />} />
            <Route path="/studio/*" element={<Navigate to="/settings/blaze" replace />} />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </Suspense>
      </Layout>
    </ErrorBoundary>
  );
}
