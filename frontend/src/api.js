const API_BASE = import.meta.env.VITE_API_BASE || 'http://127.0.0.1:8080/api'

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
    throw new Error(data.message || '请求失败')
  }
  return data
}

export const api = {
  login: (payload) => request('/auth/login', { method: 'POST', body: JSON.stringify(payload) }),
  stations: (keyword = '') => request(`/stations?keyword=${encodeURIComponent(keyword)}`),
  station: (id) => request(`/stations/${id}`),
  availability: (id, date) => request(`/stations/${id}/availability?date=${date}`),
  reserve: (payload) => request('/reservations', { method: 'POST', body: JSON.stringify(payload) })
}

