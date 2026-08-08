import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './styles/tokens.css';
import '@fontsource/funnel-display/400.css';
import '@fontsource/funnel-display/700.css';
import '@fontsource/funnel-sans/400.css';
import '@fontsource/funnel-sans/500.css';
import '@fontsource/funnel-sans/600.css';
import '@fontsource/funnel-sans/700.css';
import '@fontsource/jetbrains-mono/400.css';
import { initI18n } from './i18n';
import { initTheme } from './hooks/useTheme';

initI18n();
initTheme();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>,
);
