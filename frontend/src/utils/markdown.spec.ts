import { describe, it, expect } from 'vitest'
import { renderMarkdown } from './markdown'

describe('renderMarkdown', () => {
  it('渲染标题、加粗与列表', () => {
    const html = renderMarkdown('# 标题\n\n**重要**\n\n- 第一条\n- 第二条')
    expect(html).toContain('<h1>标题</h1>')
    expect(html).toContain('<strong>重要</strong>')
    expect(html).toContain('<li>第一条</li>')
  })

  it('渲染代码块', () => {
    const html = renderMarkdown('```\nconst a = 1\n```')
    expect(html).toContain('<code>')
  })

  it('移除 script 标签（XSS 净化）', () => {
    const html = renderMarkdown('hello <script>alert(1)</script> world')
    expect(html).not.toContain('<script')
    expect(html).not.toContain('alert(1)')
  })

  it('移除事件属性（img onerror）', () => {
    const html = renderMarkdown('<img src=x onerror="alert(1)">')
    expect(html).not.toContain('onerror')
  })

  it('移除 javascript: 协议链接', () => {
    const html = renderMarkdown('[点我](javascript:alert(1))')
    expect(html).not.toContain('javascript:')
  })

  it('空输入安全处理', () => {
    expect(renderMarkdown('')).toBe('')
    expect(renderMarkdown(null as unknown as string)).toBe('')
  })
})
