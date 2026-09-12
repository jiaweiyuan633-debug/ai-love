import { describe, it, expect, beforeEach, vi } from 'vitest'
import { auth, setSession, clearSession, onUnauthorized } from './auth'

const user = { id: 1, username: 'tester', nickname: '测试', memoryEnabled: true, email: null }

/** jsdom 的 location.assign 只读,但 window.location 属性可整体替换 */
function interceptLocationAssign() {
  const assign = vi.fn()
  const original = window.location
  Object.defineProperty(window, 'location', { value: { ...original, assign }, configurable: true })
  return { assign, restore: () => Object.defineProperty(window, 'location', { value: original, configurable: true }) }
}

describe('stores/auth', () => {
  beforeEach(() => {
    localStorage.clear()
    clearSession()
    auth.enabled = null
  })

  it('setSession 写入状态并持久化 token', () => {
    setSession('tok-123', user)
    expect(auth.token).toBe('tok-123')
    expect(auth.user).toEqual(user)
    expect(localStorage.getItem('ailove:token')).toBe('tok-123')
  })

  it('clearSession 清空状态与存储', () => {
    setSession('tok-123', user)
    clearSession()
    expect(auth.token).toBe('')
    expect(auth.user).toBeNull()
    expect(localStorage.getItem('ailove:token')).toBeNull()
  })

  it('onUnauthorized 在登录态下清会话并跳转登录页', () => {
    auth.enabled = true
    setSession('tok-123', user)
    const { assign, restore } = interceptLocationAssign()
    try {
      onUnauthorized()
    } finally {
      restore()
    }
    expect(assign).toHaveBeenCalledWith('/login')
    expect(auth.token).toBe('')
  })

  it('onUnauthorized 在体验模式（enabled=false）下不做任何动作', () => {
    auth.enabled = false
    setSession('tok-123', user)
    const { assign, restore } = interceptLocationAssign()
    try {
      onUnauthorized()
    } finally {
      restore()
    }
    expect(assign).not.toHaveBeenCalled()
    expect(auth.token).toBe('tok-123')
  })

  it('模块初始化时从 localStorage 恢复 token', async () => {
    localStorage.setItem('ailove:token', 'persisted')
    vi.resetModules()
    const mod = await import('./auth')
    expect(mod.auth.token).toBe('persisted')
  })
})
