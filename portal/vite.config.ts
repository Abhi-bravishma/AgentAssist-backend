import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev server proxies API calls to the Spring Boot backend so the portal runs
// origin-free in development. Override the target with VITE_API_TARGET when
// the backend is not on 8085.
const target = process.env.VITE_API_TARGET || 'http://localhost:8085';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target, changeOrigin: true },
      '/actuator': { target, changeOrigin: true },
    },
  },
});
