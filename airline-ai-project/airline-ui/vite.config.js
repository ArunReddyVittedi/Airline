import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Where the backend is. Defaults to the port Spring Boot uses, and overridable so the UI can
// be pointed at a backend on another port without editing a committed file:
//
//   BACKEND_URL=http://localhost:8099 npm run dev -- --port 5199
const backend = process.env.BACKEND_URL || 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Proxying /api means the browser only ever talks to one origin, so there is no CORS
    // preflight in development and no base URL to switch when deploying.
    proxy: {
      '/api': backend,
      // Actuator too, so the Ops page can link straight at the raw metrics.
      '/actuator': backend,
    },
  },
})
