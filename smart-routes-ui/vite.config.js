import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],
  // sockjs-client expects Node's `global`; point it at the browser global.
  define: {
    global: 'globalThis',
  },
  build: {
    rollupOptions: {
      output: {
        // Long-lived library code in separate files so browsers cache it across releases.
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          map: ['leaflet', 'react-leaflet'],
          live: ['@stomp/stompjs', 'sockjs-client'],
        },
      },
    },
  },
  server: {
    port: 5173,
    strictPort: true,
  },
})
