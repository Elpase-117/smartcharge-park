const API_BASE = (import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8084/api').replace(/\/$/, '')

async function request(path, options = {}) {
  const token = localStorage.getItem('smartcharge-token')
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {})
    }
  })
  const data = await response.json()
  if (!response.ok) {
    const error = new Error(data.message || data.detail || '请求失败')
    error.status = response.status
    throw error
  }
  return data
}

export const api = {
  login: (payload) => request('/auth/login', { method: 'POST', body: JSON.stringify(payload) }),
  me: () => request('/auth/me'),
  stations: (keyword = '') => request(`/stations?keyword=${encodeURIComponent(keyword)}`),
  station: (id) => request(`/stations/${id}`),
  reservationRules: () => request('/reservations/rules', { cache: 'no-store' }),
  availability: (id, date) => request(`/reservations/availability?stationId=${id}&date=${date}`),
  reserve: (payload) => request('/reservations', { method: 'POST', body: JSON.stringify(payload) })
}

