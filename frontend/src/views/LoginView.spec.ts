import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import LoginView from './LoginView.vue'
import { login, register } from '../api'
import { auth } from '../stores/auth'

vi.mock('../api', () => ({
  login: vi.fn(),
  register: vi.fn(),
}))

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/login', component: LoginView },
    { path: '/', component: { template: '<div>chat</div>' } },
    { path: '/agreement', component: { template: '<div/>' } },
    { path: '/privacy', component: { template: '<div/>' } },
  ],
})

describe('LoginView', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    localStorage.clear()
    auth.enabled = true
    auth.token = ''
    auth.user = null
    await router.push('/login')
  })

  function mountView() {
    return mount(LoginView, { global: { plugins: [router] } })
  }

  it('渲染登录表单、密码提示与法律链接', () => {
    const w = mountView()
    expect(w.text()).toContain('AI 恋爱大师')
    expect(w.find('input[type="password"]').attributes('placeholder')).toContain('8 位')
    expect(w.find('a[href="/agreement"]').exists()).toBe(true)
    expect(w.find('a[href="/privacy"]').exists()).toBe(true)
    expect(w.text()).toContain('内容由 AI 生成')
  })

  it('提交调用 login 并携带 trim 后的用户名,成功后跳转首页', async () => {
    vi.mocked(login).mockResolvedValue({
      token: 'tok',
      user: { id: 1, username: 'alice', nickname: '小爱', memoryEnabled: true },
    })
    const w = mountView()
    await w.find('input[autocomplete="username"]').setValue('  alice  ')
    await w.find('input[type="password"]').setValue('secret1')
    await w.find('form').trigger('submit')
    await vi.waitFor(() => expect(login).toHaveBeenCalledWith('alice', 'secret1'))
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/'))
    expect(auth.token).toBe('tok')
    expect(localStorage.getItem('ailove:token')).toBe('tok')
  })

  it('登录失败时展示后端错误文案', async () => {
    vi.mocked(login).mockRejectedValue(new Error('用户名或密码错误'))
    const w = mountView()
    await w.find('input[autocomplete="username"]').setValue('alice')
    await w.find('input[type="password"]').setValue('wrong')
    await w.find('form').trigger('submit')
    await vi.waitFor(() => expect(w.find('.error').text()).toBe('用户名或密码错误'))
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('切换注册模式:出现昵称字段,提交走 register 并携带昵称', async () => {
    vi.mocked(register).mockResolvedValue({
      token: 't2',
      user: { id: 2, username: 'bob', nickname: '小波', memoryEnabled: true },
    })
    const w = mountView()
    await w.findAll('.mode-tabs button')[1].trigger('click')
    expect(w.text()).toContain('昵称（可选）')
    await w.find('input[autocomplete="username"]').setValue('bob')
    await w.find('input[autocomplete="nickname"]').setValue('小波')
    await w.find('input[type="password"]').setValue('pass1234')
    await w.find('form').trigger('submit')
    await vi.waitFor(() => expect(register).toHaveBeenCalledWith('bob', 'pass1234', '小波'))
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/'))
  })

  it('用户名或密码为空时提交按钮禁用', () => {
    const w = mountView()
    const submit = w.find('button.submit')
    expect(submit.attributes('disabled')).toBeDefined()
  })
})
