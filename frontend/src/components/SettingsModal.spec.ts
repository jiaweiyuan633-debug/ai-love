import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import SettingsModal from './SettingsModal.vue'
import { auth } from '../stores/auth'

vi.mock('../api', () => ({
  bindCouple: vi.fn().mockResolvedValue({ bound: false, pending: false, code: null, partnerNickname: null, anniversaryDate: null, daysTogether: null, daysToAnniversary: null }),
  deleteMemoryItem: vi.fn().mockResolvedValue(undefined),
  fetchAchievements: vi.fn().mockResolvedValue([]),
  generateCoupleCode: vi.fn().mockResolvedValue('CODE12'),
  getCoupleStatus: vi.fn().mockResolvedValue({ bound: false, pending: false, code: null, partnerNickname: null, anniversaryDate: null, daysTogether: null, daysToAnniversary: null }),
  listMemory: vi.fn().mockResolvedValue({ enabled: false, items: [] }),
  patchMe: vi.fn().mockResolvedValue({ id: 1, username: 'u', nickname: 'n', memoryEnabled: true }),
  clearMemory: vi.fn().mockResolvedValue(undefined),
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
})
