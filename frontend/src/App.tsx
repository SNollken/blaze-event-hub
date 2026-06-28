import { Routes, Route } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import LiveEvents from './pages/LiveEvents';
import BlazeChannel from './pages/BlazeChannel';
import Alerts from './pages/Alerts';
import Giveaways from './pages/Giveaways';
import Overlays from './pages/Overlays';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Dashboard />} />
      <Route path="/events" element={<LiveEvents />} />
      <Route path="/blaze" element={<BlazeChannel />} />
      <Route path="/alerts" element={<Alerts />} />
      <Route path="/giveaways" element={<Giveaways />} />
      <Route path="/overlays" element={<Overlays />} />
      <Route path="*" element={<Dashboard />} />
    </Routes>
  );
}
