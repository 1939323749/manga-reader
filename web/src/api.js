// Thin client for the manga-reader REST API.
// Source ids stay strings end to end: they are 64-bit and don't fit in a JS number.

async function request(method, path, body) {
  const res = await fetch(path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
  if (res.status === 204) return null
  const text = await res.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = null
  }
  if (!res.ok) {
    const message = data?.error || `请求失败（HTTP ${res.status}）`
    throw new ApiError(message, res.status)
  }
  return data
}

export class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.status = status
  }
}

const enc = encodeURIComponent

export const api = {
  repoExtensions: (refresh = false) => request('GET', `/api/repo/extensions${refresh ? '?refresh=1' : ''}`),
  installed: () => request('GET', '/api/extensions'),
  install: (pkg) => request('POST', `/api/extensions/${enc(pkg)}/install`),
  uninstall: (pkg) => request('POST', `/api/extensions/${enc(pkg)}/uninstall`),

  sources: () => request('GET', '/api/sources'),
  source: (id) => request('GET', `/api/sources/${id}`),
  popular: (id, page) => request('GET', `/api/sources/${id}/popular?page=${page}`),
  latest: (id, page) => request('GET', `/api/sources/${id}/latest?page=${page}`),
  search: (id, q, page) => request('GET', `/api/sources/${id}/search?q=${enc(q)}&page=${page}`),
  manga: (id, manga) => request('POST', `/api/sources/${id}/manga`, manga),
  pages: (id, chapter) => request('POST', `/api/sources/${id}/pages`, chapter),

  coverUrl: (id, url) => (url ? `/api/sources/${id}/cover?url=${enc(url)}` : null),
  imageUrl: (id, page) =>
    `/api/sources/${id}/image?url=${enc(page.url || '')}&imageUrl=${enc(page.imageUrl || '')}`,
}
