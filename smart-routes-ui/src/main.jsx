import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { MotionConfig } from 'motion/react';
import '@fontsource-variable/inter';
import '@fontsource-variable/bricolage-grotesque';
import '@fontsource-variable/space-grotesk';
import '@fontsource-variable/jetbrains-mono';
import 'maplibre-gl/dist/maplibre-gl.css';
import './index.css';
import App from './App.jsx';
import AuthProvider from './context/AuthProvider';
import ThemeProvider from './context/ThemeProvider';
import ToastProvider from './context/ToastProvider';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      {/* Animations switch off for people who ask their device for reduced motion. */}
      <MotionConfig reducedMotion="user" transition={{ type: 'spring', stiffness: 380, damping: 34 }}>
      <ThemeProvider>
        <ToastProvider>
          <AuthProvider>
            <App />
          </AuthProvider>
        </ToastProvider>
      </ThemeProvider>
      </MotionConfig>
    </BrowserRouter>
  </React.StrictMode>,
);
