<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const isPrivacy = computed(() => route.path === '/privacy')
const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
</script>

<template>
  <div class="doc-page">
    <header class="doc-head">
      <button class="back" @click="router.back()">← 返回</button>
    </header>

    <article class="doc">
      <h1>{{ isPrivacy ? '隐私政策' : '用户服务协议' }}</h1>
      <p class="updated">更新日期：{{ today }}</p>

      <template v-if="!isPrivacy">
        <h2>一、服务性质与 AI 内容声明</h2>
        <p>
          「AI 恋爱大师」（以下简称"本服务"）是一款由人工智能驱动的情感咨询与陪伴类应用。你必须理解并同意：
        </p>
        <ul>
          <li>本服务中的所有角色均为<strong>虚拟 AI 角色，不是真人</strong>；</li>
          <li>所有回复内容由 AI 自动生成，仅作参考与陪伴之用，<strong>不能替代真实的人际关系、专业心理咨询或医疗建议</strong>；</li>
          <li>AI 生成内容可能存在错误、片面或不恰当之处，涉及重大情感、财产、法律或健康决策时，请咨询专业人士；</li>
          <li>当你出现情绪困扰或心理危机时，请及时联系家人朋友或拨打当地心理援助热线，本服务不是心理危机干预渠道。</li>
        </ul>

        <h2>二、账号注册与安全</h2>
        <ul>
          <li>注册时请提供真实有效的信息，并妥善保管账号密码；因保管不善造成的损失由你自行承担；</li>
          <li>不得将账号转借、出售给他人使用；</li>
          <li>如发现账号被冒用，请立即与我们联系。</li>
        </ul>

        <h2>三、用户行为规范</h2>
        <p>使用本服务时，你承诺不利用 AI 对话从事下列行为：</p>
        <ul>
          <li>制作、传播法律法规禁止的内容（含危害国家安全、暴力恐怖、色情低俗等）；</li>
          <li>生成针对特定真实个人的侮辱、诽谤、骚扰或欺凌内容；</li>
          <li>诱导 AI 生成露骨性内容、自残教唆或其他有害信息；</li>
          <li>对本服务进行自动化刷量、恶意攻击或干扰正常运行的任何行为。</li>
          <li>违反上述规范的，我们有权视情况暂停或终止向你提供服务。</li>
        </ul>

        <h2>四、健康使用提示</h2>
        <ul>
          <li>连续使用 2 小时后应用会提醒你适当休息；请合理安排使用时间，避免过度依赖；</li>
          <li>AI 陪伴是现实生活的补充而非替代，请把更多时间留给真实世界中关心你的人。</li>
        </ul>

        <h2>五、免责声明</h2>
        <ul>
          <li>因不可抗力、网络故障、第三方服务（如模型服务）中断导致的服务暂停，我们不承担责任；</li>
          <li>对于你依据 AI 生成内容做出的任何决定及其后果，我们不承担责任；</li>
          <li>在法律允许的最大范围内，本服务按"现状"提供。</li>
        </ul>

        <h2>六、协议的变更与终止</h2>
        <p>
          我们可能随产品迭代更新本协议，更新后将在应用内公布。你可以随时停止使用并注销账号；注销后你的个人信息与对话数据将被删除。
        </p>
      </template>

      <template v-else>
        <h2>一、我们收集的信息</h2>
        <p>为提供本服务，我们会收集和处理以下信息：</p>
        <ul>
          <li><strong>账号信息</strong>：注册时提供的用户名、昵称（密码经单向加密存储，任何人不可见原文）；</li>
          <li><strong>对话内容</strong>：你与 AI 的聊天记录，用于多轮对话上下文、历史查看与导出；</li>
          <li><strong>长期记忆</strong>：AI 从对话中提炼的关键信息（如称呼、喜好、纪念日），用于让回复更懂你，你可以在"设置"中随时查看、删除或关闭该功能；</li>
          <li><strong>心情与互动数据</strong>：心情打卡记录、成就进度、朋友圈互动等；</li>
          <li><strong>基本日志</strong>：服务运行产生的技术日志（如访问时间、错误信息），用于排查故障与保障安全。</li>
        </ul>

        <h2>二、信息的使用</h2>
        <ul>
          <li>上述信息仅用于提供和改进本服务，例如维持会话、个性化回复、生成朋友圈动态与主动关怀；</li>
          <li>我们不会将你的个人信息用于广告投放，<strong>不会向任何第三方出售你的数据</strong>。</li>
        </ul>

        <h2>三、第三方服务</h2>
        <p>
          为实现 AI 对话与语音能力，你的对话内容会通过加密接口传输至模型服务供应商（阿里云通义千问/DashScope）进行处理；
          该处理受其隐私与安全政策约束，仅用于生成回复，不用于训练你的可识别画像。除此之外，我们不会向其他第三方提供你的个人信息，
          但法律法规要求的除外。
        </p>

        <h2>四、数据存储与保护</h2>
        <ul>
          <li>数据存储于具备安全资质的云数据库中，传输过程使用 HTTPS 加密；</li>
          <li>我们遵循最小化原则收集数据，并采取访问控制、密钥管理等措施防止泄露、篡改或丢失；</li>
          <li>如发生可能影响你权益的数据安全事件，我们将依法及时告知你。</li>
        </ul>

        <h2>五、你的权利</h2>
        <ul>
          <li><strong>查看与更正</strong>：你可以在应用内随时查看和修改昵称等信息；</li>
          <li><strong>删除</strong>：你可以删除单条记忆、清空全部记忆、删除会话与心情记录；</li>
          <li><strong>关闭个性化</strong>：你可以在设置中一键关闭长期记忆功能；</li>
          <li><strong>注销</strong>：你可以在设置中注销账号，注销后你的个人信息、对话记录与记忆数据将被删除。</li>
        </ul>

        <h2>六、未成年人保护</h2>
        <p>
          本服务面向成年人设计。若你是未满 18 周岁的未成年人，请在监护人陪同下使用，并注意：
          AI 角色不是真人，请勿与 AI 角色建立情感依赖，遇到困惑请优先与家人、老师或专业机构沟通。
        </p>

        <h2>七、政策更新</h2>
        <p>我们可能适时更新本政策，更新后将在应用内公布。继续使用即视为你知悉并同意更新后的内容。</p>
      </template>

      <p class="contact">如对本页面内容有任何疑问，可通过应用内反馈渠道或项目仓库 Issue 与我们联系。</p>
    </article>
  </div>
</template>

<style scoped>
.doc-page {
  min-height: 100vh;
  background: var(--bg);
  color: var(--text-2);
}
.doc-head {
  max-width: 720px;
  margin: 0 auto;
  padding: 18px 20px 0;
}
.back {
  border: 1px solid var(--border);
  background: var(--bg-card);
  color: var(--text-2);
  border-radius: 10px;
  padding: 7px 14px;
  font-size: 13px;
  cursor: pointer;
}
.back:hover {
  background: var(--hover);
}
.doc {
  max-width: 720px;
  margin: 0 auto;
  padding: 14px 20px 60px;
}
.doc h1 {
  color: var(--text);
  font-size: 24px;
  margin: 10px 0 4px;
}
.updated {
  color: var(--text-4);
  font-size: 12px;
  margin: 0 0 22px;
}
.doc h2 {
  color: var(--text);
  font-size: 16px;
  margin: 26px 0 10px;
}
.doc p,
.doc li {
  font-size: 14px;
  line-height: 1.8;
  margin: 6px 0;
}
.doc ul {
  padding-left: 20px;
  margin: 8px 0;
}
.contact {
  margin-top: 30px;
  color: var(--text-4);
  font-size: 12px;
}
@media (max-width: 500px) {
  .doc-head,
  .doc {
    padding-left: 16px;
    padding-right: 16px;
  }
}
</style>
