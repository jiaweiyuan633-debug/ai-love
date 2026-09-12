/*
 * AI 恋爱大师 Service Worker（PWA 离线外壳）
 * 策略：
 * - 构建产物 /assets/*（带 hash，不可变）→ 缓存优先
 * - 页面导航 → 网络优先，断网回退缓存的 index.html（SPA 单页）
 * - /ai、/api、/auth、/knowledge 接口（含 SSE 流）→ 永不缓存
 */
const CACHE = 'ailove-v1'

self.addEventListener('install', () => {
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys()
      await Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
      await self.clients.claim()
    })(),
  )
})

self.addEventListener('fetch', (event) => {
  const request = event.request
  if (request.method !== 'GET') return
  const url = new URL(request.url)
  if (url.origin !== self.location.origin) return
  if (url.pathname.startsWith('/ai') || url.pathname.startsWith('/api') || url.pathname.startsWith('/auth') || url.pathname.startsWith('/knowledge')) {
    return // 接口与 SSE 流直连网络
  }

  if (url.pathname.startsWith('/assets/')) {
    // 带内容 hash 的静态资源：缓存优先
    event.respondWith(
      (async () => {
        const cache = await caches.open(CACHE)
        const hit = await cache.match(request)
        if (hit) return hit
        const resp = await fetch(request)
        if (resp.ok) cache.put(request, resp.clone())
        return resp
      })(),
    )
    return
  }

  if (request.mode === 'navigate') {
    // 页面导航：网络优先，断网回退
    event.respondWith(
      (async () => {
        try {
          const resp = await fetch(request)
          const cache = await caches.open(CACHE)
          cache.put('/index.html', resp.clone())
          return resp
        } catch {
          const cache = await caches.open(CACHE)
          return (await cache.match('/index.html')) || Response.error()
        }
      })(),
    )
  }
})
