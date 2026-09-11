/**
 * SSE 流式对话：基于 EventSource 封装，返回一个取消函数。
 * 后端以 data:[DONE] 标记正常结束；收到后主动关闭，避免 EventSource 自动重连重复请求。
 */
export function openSseStream(
  url: string,
  onData: (text: string) => void,
  onDone?: () => void,
  onError?: (e: Event) => void,
): () => void {
  const source = new EventSource(url)
  let received = false
  let finished = false

  source.onmessage = (e) => {
    if (e.data === undefined) return
    if (e.data === '[DONE]') {
      finished = true
      source.close()
      onDone?.()
      return
    }
    received = true
    onData(e.data)
  }

  source.onerror = () => {
    source.close()
    if (finished) return
    // 连接关闭但未收到 [DONE]：已收到内容视为正常完成（服务端可能直接关流），否则报错
    if (received) {
      onDone?.()
    } else {
      onError?.(new Event('connection-failed'))
    }
  }

  return () => {
    finished = true
    source.close()
  }
}

export async function uploadKnowledge(file: File): Promise<{ file_name: string; chunks: number }> {
  const form = new FormData()
  form.append('file', file)
  const resp = await fetch('/knowledge/upload', { method: 'POST', body: form })
  if (!resp.ok) throw new Error(`上传失败: ${resp.status}`)
  return resp.json()
}

export interface KnowledgeDoc {
  file_name: string
  doc_id: string
  chunks: number
}

export async function listKnowledge(): Promise<KnowledgeDoc[]> {
  const resp = await fetch('/knowledge/list')
  if (!resp.ok) throw new Error(`获取列表失败: ${resp.status}`)
  return resp.json()
}

export async function deleteKnowledge(docId: string): Promise<void> {
  const resp = await fetch(`/knowledge/${docId}`, { method: 'DELETE' })
  if (!resp.ok) throw new Error(`删除失败: ${resp.status}`)
}