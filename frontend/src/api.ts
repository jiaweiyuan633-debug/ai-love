/**
 * SSE 流式对话：基于 EventSource 封装，返回一个取消函数。
 */
export function openSseStream(
  url: string,
  onData: (text: string) => void,
  onDone?: () => void,
  onError?: (e: Event) => void,
): () => void {
  const source = new EventSource(url)
  source.onmessage = (e) => {
    if (e.data === undefined) return
    onData(e.data)
  }
  source.onerror = (e) => {
    // 服务端完成时会正常关闭连接，readyState CLOSED 视为结束
    if (source.readyState === EventSource.CLOSED) {
      onDone?.()
    } else {
      onError?.(e)
    }
    source.close()
  }
  return () => source.close()
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
