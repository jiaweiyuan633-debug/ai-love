import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({ breaks: true, gfm: true })

/** Markdown → 消毒后的 HTML（防 XSS），聊天与智能体输出统一走这里渲染 */
export function renderMarkdown(text: string): string {
  const html = marked.parse(text ?? '', { async: false }) as string
  return DOMPurify.sanitize(html)
}
