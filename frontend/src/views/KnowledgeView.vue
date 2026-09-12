<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { deleteKnowledge, listKnowledge, uploadKnowledge, type KnowledgeDoc } from '../api'

const docs = ref<KnowledgeDoc[]>([])
const uploading = ref(false)
const error = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

async function refresh() {
  try {
    docs.value = await listKnowledge()
    error.value = ''
  } catch {
    error.value = '无法获取知识库列表，请确认后端与 pgvector 已启动'
  }
}

async function uploadFile(file: File) {
  uploading.value = true
  error.value = ''
  try {
    await uploadKnowledge(file)
    await refresh()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '上传失败'
  } finally {
    uploading.value = false
  }
}

function onFileChange(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) uploadFile(file)
  target.value = ''
}

function onDrop(e: DragEvent) {
  const file = e.dataTransfer?.files?.[0]
  if (file) uploadFile(file)
}

async function onDelete(doc: KnowledgeDoc) {
  try {
    await deleteKnowledge(doc.doc_id)
    await refresh()
  } catch {
    error.value = '删除失败'
  }
}

onMounted(refresh)
</script>

<template>
  <div class="knowledge-page">
    <h1>📚 恋爱知识库</h1>
    <p class="desc">
      上传 PDF / Word / Markdown 恋爱相关文档，系统会自动解析、切分并向量化入库，
      之后恋爱大师对话时会自动检索这些内容作为参考。
    </p>

    <div
      class="upload-zone"
      :class="{ uploading }"
      @click="fileInput?.click()"
      @dragover.prevent
      @drop.prevent="onDrop"
    >
      <input
        ref="fileInput"
        type="file"
        accept=".pdf,.doc,.docx,.md,.txt,.ppt,.pptx"
        hidden
        :disabled="uploading"
        @change="onFileChange"
      />
      <div v-if="uploading" class="zone-text">⏳ 正在解析并向量化…</div>
      <div v-else class="zone-text">
        <div class="zone-icon">📤</div>
        点击或拖拽文件到此处上传
        <div class="zone-hint">支持 PDF / Word / PPT / Markdown / TXT，最大 20MB</div>
      </div>
    </div>

    <div v-if="error" class="error-bar">{{ error }}</div>

    <h2>已入库文档（{{ docs.length }}）</h2>
    <table v-if="docs.length" class="doc-table">
      <thead>
        <tr>
          <th>文件名</th>
          <th>分块数</th>
          <th>doc_id</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="d in docs" :key="d.doc_id">
          <td>{{ d.file_name }}</td>
          <td>{{ d.chunks }}</td>
          <td class="mono">{{ d.doc_id.slice(0, 8) }}…</td>
          <td>
            <button class="del-btn" @click="onDelete(d)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="empty">暂无文档，先上传一份试试吧。</p>
  </div>
</template>

<style scoped>
.knowledge-page {
  max-width: 860px;
  margin: 0 auto;
  padding: 28px 20px;
  color: var(--text-2);
  overflow-y: auto;
  height: 100%;
}
h1 {
  color: var(--text);
  font-size: 22px;
}
.desc {
  font-size: 13px;
  color: var(--text-4);
}
.upload-zone {
  margin: 18px 0;
  border: 2px dashed var(--border-strong);
  border-radius: 16px;
  padding: 40px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s;
}
.upload-zone:hover,
.upload-zone.uploading {
  border-color: var(--a2);
  background: var(--bg-card);
}
.zone-icon {
  font-size: 32px;
  margin-bottom: 8px;
}
.zone-text {
  color: var(--text-3);
  font-size: 15px;
}
.zone-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-5);
}
.error-bar {
  background: var(--danger-bg);
  color: var(--danger-text);
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 13px;
  margin-bottom: 14px;
}
h2 {
  font-size: 16px;
  color: var(--text);
  margin-top: 24px;
}
.doc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  margin-top: 12px;
}
.doc-table th,
.doc-table td {
  text-align: left;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
}
.doc-table th {
  color: var(--text-4);
  font-weight: 500;
}
.mono {
  font-family: monospace;
  color: var(--text-4);
}
.del-btn {
  background: none;
  border: 1px solid var(--danger-bg);
  color: var(--danger-text);
  border-radius: 6px;
  padding: 3px 10px;
  cursor: pointer;
  font-size: 12px;
}
.del-btn:hover {
  background: var(--danger-bg);
}
.empty {
  color: var(--text-5);
  font-size: 13px;
}
</style>
