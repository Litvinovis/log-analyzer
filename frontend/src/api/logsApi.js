const BASE = '/api/logs'

async function get(path, params = {}) {
  const url = new URL(BASE + path, window.location.origin)
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') url.searchParams.append(k, v)
  })
  const res = await fetch(url)
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
  return res.json()
}

async function post(path, body) {
  const res = await fetch(BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
  return res.json()
}

export const logsApi = {
  getErrors:    (params) => get('/errors', params),
  getStats:     (params) => get('/stats',  params),
  trace:        (traceId, app, from, to) => get(`/trace/${traceId}`, { ...(app ? { app } : {}), ...(from ? { from } : {}), ...(to ? { to } : {}) }),
  startAnalysis:(body) => post('/analyze', body),
  getJob:       (id) => get(`/jobs/${id}`),
  getAllEntries: (params) => get('/all', params),
  getApps:      () => get('/apps'),
}
