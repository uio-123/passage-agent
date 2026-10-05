const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL

// Local development default. Deployments override VITE_API_BASE_URL in their environment file.
export const API_BASE_URL = configuredApiBaseUrl && configuredApiBaseUrl.trim()
  ? configuredApiBaseUrl.trim()
  : 'http://localhost:8123/api'
