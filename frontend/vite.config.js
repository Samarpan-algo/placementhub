import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In dev, the browser only talks to Vite (localhost:5173). Vite forwards every /api request to
// Spring Boot, so the browser sees one origin and no CORS is needed.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
