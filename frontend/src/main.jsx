import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { AuthProvider } from './auth/AuthContext.jsx';
import App from './App.jsx';
import './styles.css';

// The entry point: React takes over <div id="root"> in index.html and draws <App /> inside it.
// BrowserRouter lets every component read and change the URL; AuthProvider gives them the logged-in user.
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
