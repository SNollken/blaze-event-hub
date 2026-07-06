import { lazy, Suspense } from 'react';
import { Routes, Route } from 'react-router-dom';
import { ErrorBoundary } from './components/ErrorBoundary';

const Dashboard = lazy(() => import('./pages/Dashboard'));
const Events = lazy(() => import('./pages/Events'));
const CreateEvent = lazy(() => import('./pages/CreateEvent'));
const EventDetail = lazy(() => import('./pages/EventDetail'));
const MyEvents = lazy(() => import('./pages/MyEvents'));

export default function App() {
  return (
    <ErrorBoundary>
      <Suspense fallback={<div className="skeleton-list" style={{ margin: 24 }} />}>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/events" element={<Events />} />
          <Route path="/events/create" element={<CreateEvent />} />
          <Route path="/events/:id" element={<EventDetail />} />
          <Route path="/my-events" element={<MyEvents />} />
          <Route path="*" element={<Dashboard />} />
        </Routes>
      </Suspense>
    </ErrorBoundary>
  );
}
