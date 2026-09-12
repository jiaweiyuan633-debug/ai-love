<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  commentMoment,
  listMoments,
  toggleMomentLike,
  type Moment,
  type Persona,
  fetchPersonas,
} from '../api'
import { settings } from '../stores/settings'

const moments = ref<Moment[]>([])
const personas = ref<Persona[]>([])
const activePersonaId = ref(settings.voicePersona || 'jiejie')
const loading = ref(false)
const error = ref('')
const draft = ref('')
const commentingId = ref<number | null>(null)
const likedFlash = ref<number | null>(null)

const activePersona = () => personas.value.find((p) => p.id === activePersonaId.value)

async function load() {
  loading.value = true
  error.value = ''
  try {
    moments.value = await listMoments(activePersonaId.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function switchPersona(id: string) {
  activePersonaId.value = id
  settings.voicePersona = id
  void load()
}

async function like(m: Moment) {
  try {
    const liked = await toggleMomentLike(m.id)
    m.liked = liked
    if (liked) {
      likedFlash.value = m.id
      setTimeout(() => (likedFlash.value = null), 900)
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '点赞失败'
  }
}

async function sendComment(m: Moment) {
  const text = draft.value.trim()
  if (!text) return
  try {
    m.comments = await commentMoment(m.id, text)
    draft.value = ''
    commentingId.value = null
  } catch (e) {
    error.value = e instanceof Error ? e.message : '评论失败'
  }
}

function fmtDate(d: string): string {
  const today = new Date().toISOString().slice(0, 10)
  if (d === today) return '今天'
  return d.slice(5).replace('-', ' 月 ') + ' 日'
}

onMounted(async () => {
  try {
    personas.value = await fetchPersonas()
  } catch {
    // 角色列表失败时仍可展示动态
  }
  await load()
})
</script>

<template>
  <div class="moments-page">
    <div class="header">
      <h1>🌸 TA 的朋友圈</h1>
      <p class="desc">角色每天会在这里分享TA的小日常——点赞、评论，TA 都会回应你。</p>
    </div>

    <div class="persona-tabs">
      <button
        v-for="p in personas"
        :key="p.id"
        :class="{ on: p.id === activePersonaId }"
        @click="switchPersona(p.id)"
      >{{ p.emoji }} {{ p.name }}</button>
    </div>

    <div v-if="error" class="error-bar">{{ error }}</div>

    <div class="feed">
      <div v-for="m in moments" :key="m.id" class="moment-card">
        <div
          class="m-avatar"
          :style="activePersona() ? { background: `linear-gradient(135deg, ${activePersona()!.color}, #a76bff)` } : {}"
        >{{ activePersona()?.emoji || '💘' }}</div>
        <div class="m-body">
          <div class="m-name">{{ activePersona()?.name || m.persona }}</div>
          <div class="m-content">{{ m.content }}</div>
          <div class="m-meta">
            <span>{{ fmtDate(m.date) }}</span>
            <button class="m-like" :class="{ on: m.liked, flash: likedFlash === m.id }" @click="like(m)">
              {{ m.liked ? '❤️ 已赞' : '🤍 赞' }}
            </button>
            <button class="m-cbtn" @click="commentingId = commentingId === m.id ? null : m.id">💬 评论</button>
          </div>
          <div v-if="m.comments.length" class="m-comments">
            <div v-for="c in m.comments" :key="c.id" class="m-comment">
              <b>{{ c.role === 'ai' ? activePersona()?.name || 'TA' : '我' }}</b>：{{ c.content }}
            </div>
          </div>
          <div v-if="commentingId === m.id" class="m-input-row">
            <input
              v-model="draft"
              placeholder="说点什么…（角色会回复你）"
              maxlength="200"
              @keydown.enter="sendComment(m)"
            />
            <button :disabled="!draft.trim()" @click="sendComment(m)">发送</button>
          </div>
        </div>
      </div>
      <div v-if="!loading && moments.length === 0" class="empty-tip">
        还没有动态，稍后再来看看～
      </div>
    </div>
  </div>
</template>

<style scoped>
.moments-page {
  max-width: 640px;
  margin: 0 auto;
  padding: 24px 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}
.header h1 {
  color: var(--text);
  font-size: 22px;
}
.desc {
  font-size: 13px;
  color: var(--text-4);
}
.persona-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0;
}
.persona-tabs button {
  border: 1px solid var(--border-strong);
  background: var(--bg-card);
  color: var(--text-2);
  border-radius: 999px;
  padding: 6px 14px;
  font-size: 13px;
  cursor: pointer;
}
.persona-tabs button.on {
  background: var(--accent-grad);
  border-color: transparent;
  color: #fff;
}
.error-bar {
  background: var(--danger-bg);
  color: var(--danger-text);
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 13px;
  margin-bottom: 12px;
}
.feed {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.moment-card {
  display: flex;
  gap: 12px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 16px;
}
.m-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
  background: var(--accent-grad);
}
.m-body {
  flex: 1;
  min-width: 0;
}
.m-name {
  font-weight: 600;
  color: var(--text);
  margin-bottom: 6px;
}
.m-content {
  color: var(--text-2);
  font-size: 14px;
  line-height: 1.7;
}
.m-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
  color: var(--text-5);
  font-size: 12px;
}
.m-like,
.m-cbtn {
  border: none;
  background: transparent;
  color: var(--text-4);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 6px;
}
.m-like:hover,
.m-cbtn:hover {
  color: var(--text);
  background: var(--hover);
}
.m-like.on {
  color: #ff6b9d;
}
.m-like.flash {
  animation: heart-pop 0.45s ease;
}
@keyframes heart-pop {
  40% {
    transform: scale(1.35);
  }
}
.m-comments {
  margin-top: 10px;
  background: var(--bg-soft);
  border-radius: 10px;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.m-comment {
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.6;
}
.m-comment b {
  color: var(--a2);
  font-weight: 600;
}
.m-input-row {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}
.m-input-row input {
  flex: 1;
  background: var(--bg-input);
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  color: var(--text);
  padding: 8px 12px;
  font-size: 13px;
  outline: none;
}
.m-input-row input:focus {
  border-color: var(--a2);
}
.m-input-row button {
  border: none;
  border-radius: 10px;
  background: var(--accent-grad);
  color: #fff;
  padding: 0 16px;
  font-size: 13px;
  cursor: pointer;
}
.m-input-row button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.empty-tip {
  text-align: center;
  color: var(--text-5);
  padding: 60px 0;
}
</style>
