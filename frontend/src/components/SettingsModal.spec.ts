import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import SettingsModal from './SettingsModal.vue'
import { auth } from '../stores/auth'
import { createMembershipOrder, fetchMembership, payMembershipOrder } from '../api'

vi.mock('../api', () => ({
  bindCouple: vi.fn().mockResolvedValue({ bound: false, pending: false, code: null, partnerNickname: null, anniversaryDate: null, daysTogether: null, daysToAnniversary: null }),
  createMembershipOrder: vi.fn(),
  deleteMemoryItem: vi.fn().mockResolvedValue(undefined),
  fetchAchievements: vi.fn().mockResolvedValue([]),
  fetchMembership: vi.fn(),
  fetchMembershipPlans: vi.fn().mockResolvedValue([]),
  generateCoupleCode: vi.fn().mockResolvedValue('CODE12'),
  getCoupleStatus: vi.fn().mockResolvedValue({ bound: false, pending: false, code: null, partnerNickname: null, anniversaryDate: null, daysTogether: null, daysToAnniversary: null }),
  listMemory: vi.fn().mockResolvedValue({ enabled: false, items: [] }),
  patchMe: vi.fn().mockResolvedValue({ id: 1, username: 'u', nickname: 'n', memoryEnabled: true }),
  clearMemory: vi.fn().mockResolvedValue(undefined),
  payMembershipOrder: vi.fn(),
  setAnniversary: vi.fn().mockResolvedValue({ bound: false, pending: false, code: null, partnerNickname: null, anniversaryDate: null, daysTogether: null, daysToAnniversary: null }),
  unbindCouple: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('../composables/useSpeech', () => ({
  zhVoices: vi.fn().mockReturnValue([]),
  speakFull: vi.fn(),
}))

describe('SettingsModal', () => {
  beforeEach(() => {
    localStorage.clear()
    auth.enabled = false
    auth.token = ''
    auth.user = null
  })

  function mountView() {
    // Teleport 内联渲染,否则查询不到弹窗内容
    return mount(SettingsModal, { global: { stubs: { teleport: true } } })
  }

  it('挂载后默认展示外观 tab 与六个设置入口', () => {
    const w = mountView()
    expect(w.find('header h2').text()).toBe('⚙️ 设置')
    const tabs = w.findAll('.tabs button')
    expect(tabs).toHaveLength(6)
    expect(tabs[0].classes()).toContain('active')
    expect(w.text()).toContain('外观')
  })

  it('关闭按钮触发 close 事件', async () => {
    const w = mountView()
    await w.find('button.close').trigger('click')
    expect(w.emitted('close')).toHaveLength(1)
  })

  it('账号 tab 展示法律文档入口链接', async () => {
    const w = mountView()
    await w.findAll('.tabs button').find((b) => b.text().includes('记忆与账号'))!.trigger('click')
    expect(w.find('.legal-links').exists()).toBe(true)
    expect(w.find('.legal-links a[href="/agreement"]').exists()).toBe(true)
    expect(w.find('.legal-links a[href="/privacy"]').exists()).toBe(true)
  })

  it('体验模式隐藏会员入口', () => {
    const w = mountView()
    expect(w.findAll('.tabs button').map((b) => b.text()).join()).not.toContain('会员')
  })

  it('会员 tab:免费版状态展示,模拟支付后变为 VIP', async () => {
    auth.enabled = true
    auth.user = { id: 1, username: 'u', nickname: '小爱', memoryEnabled: true }
    vi.mocked(fetchMembership).mockResolvedValue({ vip: false, plan: null, vipUntil: null, dailyUsed: 3, dailyLimit: 20 })
    const { fetchMembershipPlans } = await import('../api')
    vi.mocked(fetchMembershipPlans).mockResolvedValue([
      { id: 'month', label: '月度 VIP', priceFen: 1800, days: 30 },
      { id: 'quarter', label: '季度 VIP', priceFen: 4800, days: 90 },
      { id: 'year', label: '年度 VIP', priceFen: 15800, days: 365 },
    ])
    const w = mountView()
    await w.findAll('.tabs button').find((b) => b.text().includes('会员'))!.trigger('click')
    await vi.waitFor(() => expect(w.text()).toContain('免费版'))
    expect(w.text()).toContain('3/20')
    expect(w.findAll('.plan-card')).toHaveLength(3)

    // 模拟支付闭环：下单 → 支付 → VIP
    vi.mocked(createMembershipOrder).mockResolvedValue({ id: 'o1', plan: 'year', priceFen: 15800, status: 'pending' })
    vi.mocked(payMembershipOrder).mockResolvedValue({ vip: true, plan: 'year', vipUntil: '2027-09-12T00:00:00Z', dailyUsed: 0, dailyLimit: 20 })
    await w.findAll('.plan-card')[2].trigger('click')
    await vi.waitFor(() => expect(createMembershipOrder).toHaveBeenCalledWith('year'))
    await vi.waitFor(() => expect(w.text()).toContain('VIP 会员'))
    expect(w.text()).toContain('2027')
  })
})
