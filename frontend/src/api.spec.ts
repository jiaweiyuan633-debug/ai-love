import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { fetchAuthStatus, login, register, fetchMe, openSseStream } from './api'
import { auth, setSession, clearSession } from './stores/auth'

const user = { id: 1, username: 'u', nickname: 'n', memoryEnabled: true, email: null }

/** 构造一个可控的 SSE/JSON 假 Response（无需真实 ReadableStream） */
function fakeResponse(
  chunks: string[],
  init: { ok?: boolean; status?: number; json?: () => Promise<unknown> } = {},
): Response {
  const encoder = new TextEncoder()
  let i = 0
  return {
    ok: init.ok ?? true,
    status: init.status ?? 200,
    body: {
      getReader: () => ({
        read: async () =>
          i < chunks.length
            ? { done: false, value: encoder.encode(chunks[i++]) }
            : { done: true, value: undefined },
      }),
    },
    json: init.json,
  } as unknown as Response
}

/** jsdom 的 location.assign 只读,但 window.location 属性可整体替换 */
function interceptLocationAssign() {
  const assign = vi.fn()
  const original = window.location
  Object.defineProperty(window, 'location', { value: { ...original, assign }, configurable: true })
  return { assign, restore: () => Object.defineProperty(window, 'location', { value: original, configurable: true }) }
}

describe('api 认证请求', () => {
  beforeEach(() => {
    localStorage.clear()
    clearSession()
    auth.enabled = true
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('fetchAuthStatus 返回后端开关', async () => {
    const f = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ enabled: true }) })
    vi.stubGlobal('fetch', f)
    await expect(fetchAuthStatus()).resolves.toBe(true)
    expect(f).toHaveBeenCalledWith('/auth/status')
  })

  it('fetchAuthStatus 非 200 视为未启用', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, json: async () => ({}) }))
    await expect(fetchAuthStatus()).resolves.toBe(false)
  })

  it('login 发送 JSON 请求体并返回 token 响应', async () => {
    const f = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ token: 't', user }) })
    vi.stubGlobal('fetch', f)
    await expect(login('alice', 'pw123')).resolves.toEqual({ token: 't', user })
    const [url, init] = f.mock.calls[0]
    expect(url).toBe('/auth/login')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({ username: 'alice', password: 'pw123' })
  })

  it('login 失败时抛出后端 error 文案', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: false, status: 401, json: async () => ({ error: '用户名或密码错误' }) }),
    )
    await expect(login('alice', 'bad')).rejects.toThrow('用户名或密码错误')
  })

  it('register 的可选昵称未提供时不进入请求体', async () => {
    const f = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ token: 't', user }) })
    vi.stubGlobal('fetch', f)
    await register('bob', 'pw123')
    expect(JSON.parse(f.mock.calls[0][1].body)).toEqual({ username: 'bob', password: 'pw123' })
  })

  it('fetchMe 携带 Bearer Authorization 头', async () => {
    setSession('tok-1', user)
    const f = vi.fn().mockResolvedValue({ ok: true, json: async () => user })
    vi.stubGlobal('fetch', f)
    await fetchMe()
    const [url, init] = f.mock.calls[0]
    expect(url).toBe('/auth/me')
    expect(init.headers.Authorization).toBe('Bearer tok-1')
  })

  it('authRequest 收到 401 时清会话并跳转登录页', async () => {
    setSession('tok-1', user)
    const { assign, restore } = interceptLocationAssign()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401, json: async () => ({}) }))
    try {
      await fetchMe().catch(() => {})
      expect(assign).toHaveBeenCalledWith('/login')
      expect(auth.token).toBe('')
      expect(localStorage.getItem('ailove:token')).toBeNull()
    } finally {
      restore()
    }
  })
})

describe('openSseStream', () => {
  beforeEach(() => {
    localStorage.clear()
    clearSession()
    auth.enabled = true
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('按帧推送 data 内容并以 [DONE] 正常结束', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(fakeResponse(['data: hello\n\n', 'data: world\n\ndata: [DONE]\n\n'])),
    )
    const onData = vi.fn()
    const onDone = vi.fn()
    const onError = vi.fn()
    openSseStream('/ai/chat-stream', onData, onDone, onError)
    await vi.waitFor(() => expect(onDone).toHaveBeenCalled())
    expect(onData).toHaveBeenNthCalledWith(1, 'hello')
    expect(onData).toHaveBeenNthCalledWith(2, 'world')
    expect(onError).not.toHaveBeenCalled()
  })

  it('合并多行 data（token 含换行时）', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(fakeResponse(['data: line1\ndata: line2\n\ndata: [DONE]\n\n'])),
    )
    const onData = vi.fn()
    openSseStream('/ai/x', onData)
    await vi.waitFor(() => expect(onData).toHaveBeenCalled())
    expect(onData).toHaveBeenCalledWith('line1\nline2')
  })

  it('ai-meta 事件帧触发 onMeta 且不混入正文（AI 生成隐式标识）', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(fakeResponse(['event: ai-meta\n\n', 'data: 你好\n\ndata: [DONE]\n\n'])),
    )
    const onData = vi.fn()
    const onMeta = vi.fn()
    const onError = vi.fn()
    openSseStream('/ai/x', onData, undefined, onError, onMeta)
    await vi.waitFor(() => expect(onData).toHaveBeenCalled())
    expect(onMeta).toHaveBeenCalledTimes(1)
    expect(onData).toHaveBeenCalledTimes(1)
    expect(onData).toHaveBeenCalledWith('你好')
    expect(onError).not.toHaveBeenCalled()
  })

  it('[ERROR] 帧触发 onError 且不再推送正文', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(fakeResponse(['data: [ERROR] 服务开小差了\n\ndata: [DONE]\n\n'])),
    )
    const onData = vi.fn()
    const onError = vi.fn()
    openSseStream('/ai/x', onData, undefined, onError)
    await vi.waitFor(() => expect(onError).toHaveBeenCalled())
    expect(onError).toHaveBeenCalledWith('服务开小差了')
    expect(onData).not.toHaveBeenCalled()
  })

  it('非 200 响应透传后端 JSON 错误文案（429 限流）', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        fakeResponse([], { ok: false, status: 429, json: async () => ({ error: '请求太频繁，请喝口水休息一下，稍后再试' }) }),
      ),
    )
    const onError = vi.fn()
    openSseStream('/ai/x', vi.fn(), undefined, onError)
    await vi.waitFor(() => expect(onError).toHaveBeenCalled())
    expect(onError).toHaveBeenCalledWith('请求太频繁，请喝口水休息一下，稍后再试')
  })

  it('非 JSON 错误体回退到默认连接失败文案', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        fakeResponse([], {
          ok: false,
          status: 502,
          json: async () => {
            throw new Error('not json')
          },
        }),
      ),
    )
    const onError = vi.fn()
    openSseStream('/ai/x', vi.fn(), undefined, onError)
    await vi.waitFor(() => expect(onError).toHaveBeenCalled())
    expect(onError).toHaveBeenCalledWith('连接失败 (502)')
  })

  it('取消流视为正常结束（触发 onDone 而非 onError）', async () => {
    let signal: AbortSignal | null | undefined
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation(async (_url: unknown, init: RequestInit) => {
        signal = init.signal
        return {
          ok: true,
          status: 200,
          body: {
            getReader: () => ({
              read: () =>
                new Promise((_resolve, reject) => {
                  if (signal?.aborted) return reject(new Error('AbortError'))
                  signal?.addEventListener('abort', () => reject(new Error('AbortError')))
                }),
            }),
          },
        } as unknown as Response
      }),
    )
    const onDone = vi.fn()
    const onError = vi.fn()
    const cancel = openSseStream('/ai/x', vi.fn(), onDone, onError)
    await new Promise((r) => setTimeout(r, 5))
    cancel()
    await vi.waitFor(() => expect(onDone).toHaveBeenCalled())
    expect(onError).not.toHaveBeenCalled()
  })
})
