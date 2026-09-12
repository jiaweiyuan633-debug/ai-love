import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import AgreementView from './AgreementView.vue'

function makeRouter(initialPath: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/agreement', component: AgreementView },
      { path: '/privacy', component: AgreementView },
    ],
  })
  void router.push(initialPath)
  return router
}

describe('AgreementView', () => {
  it('/agreement 渲染用户服务协议', async () => {
    const router = makeRouter('/agreement')
    await router.isReady()
    const w = mount(AgreementView, { global: { plugins: [router] } })
    expect(w.find('h1').text()).toBe('用户服务协议')
    expect(w.text()).toContain('虚拟 AI 角色')
    expect(w.text()).toContain('健康使用提示')
    expect(w.text()).toContain('免责声明')
  })

  it('/privacy 渲染隐私政策', async () => {
    const router = makeRouter('/privacy')
    await router.isReady()
    const w = mount(AgreementView, { global: { plugins: [router] } })
    expect(w.find('h1').text()).toBe('隐私政策')
    expect(w.text()).toContain('未成年人保护')
    expect(w.text()).toContain('不会向任何第三方出售你的数据')
    expect(w.text()).toContain('阿里云通义千问/DashScope')
  })

  it('同一组件随路由切换内容', async () => {
    const router = makeRouter('/agreement')
    await router.isReady()
    const w = mount(AgreementView, { global: { plugins: [router] } })
    expect(w.find('h1').text()).toBe('用户服务协议')
    await router.push('/privacy')
    expect(w.find('h1').text()).toBe('隐私政策')
  })
})
