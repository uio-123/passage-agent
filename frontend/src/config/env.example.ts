/**
 * Environment configuration example.
 *
 * `src/config/env.ts` is tracked and reads Vite environment variables.
 * Configure VITE_API_BASE_URL in .env.development or .env.production,
 * then import API_BASE_URL from `@/config/env`.
 */
const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL

export const API_BASE_URL = configuredApiBaseUrl && configuredApiBaseUrl.trim()
  ? configuredApiBaseUrl.trim()
  : 'http://localhost:8123/api'
