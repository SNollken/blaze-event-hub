import { lazy, Suspense } from 'react';
import { Routes, Route } from 'react-router-dom';
import { ErrorBoundary } from './components/ErrorBoundary';
import { Layout } from './components/Layout';
import { LanguageSwitcher } from './components/LanguageSwitcher';
import { useI18n } from './i18n/I18nContext';

const Dashboard = lazy(() => import('./pages/Dashboard'));
const Events = lazy(() => import('./pages/Events'));
const CreateEvent = lazy(() => import('./pages/CreateEvent'));
const EditEvent = lazy(() => import('./pages/EditEvent'));
const EventDetail = lazy(() => import('./pages/EventDetail'));
const LiveDraw = lazy(() => import('./pages/LiveDraw'));
const MyEvents = lazy(() => import('./pages/MyEvents'));
const Login = lazy(() => import('./pages/Login'));

export default function App() {
  const { t } = useI18n();

  return (
    <ErrorBoundary>
      <LanguageSwitcher />
      <Layout>
        <Suspense fallback={<div style={{ padding: 24, color: 'var(--muted)' }}>{t('appLoading')}</div>}>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/events" element={<Events />} />
            <Route path="/events/create" element={<CreateEvent />} />
            <Route path="/events/:id" element={<EventDetail />} />
            <Route path="/events/:id/edit" element={<EditEvent />} />
            <Route path="/events/:id/draw" element={<LiveDraw />} />
            <Route path="/my-events" element={<MyEvents />} />
            <Route path="/login" element={<Login />} />
            <Route path="*" element={<Dashboard />} />
          </Routes>
        </Suspense>
      </Layout>
    </ErrorBoundary>
  );
}
