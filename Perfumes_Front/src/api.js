const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export function getApiUrl() {
  return API_URL
}

export async function apiRequest(path, options = {}, token) {
  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {}),
    },
  })

  if (!response.ok) {
    const text = await response.text()
    let message = text
    try {
      const errorBody = JSON.parse(text)
      message = errorBody.message || errorBody.error || errorBody.path || text
    } catch {
      message = text
    }
    const error = new Error(message || `HTTP ${response.status}`)
    error.status = response.status
    throw error
  }

  if (response.status === 204) {
    return null
  }

  return response.json()
}
