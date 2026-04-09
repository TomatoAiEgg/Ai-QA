<template>
  <div class="rag-modal-mask" @click.self="closeRagModal">
    <div class="rag-modal">
      <div class="rag-header">
        <div>
          <div class="page-eyebrow">知识库工作台</div>
          <h1 class="page-title">知识库管理</h1>
        </div>
        <el-button class="close-btn" :icon="Close" circle @click="closeRagModal" />
      </div>

      <div v-if="!showDocDialog" class="section">
        <div class="section-header">
          <el-icon><Folder /></el-icon>
          <span class="section-title">知识库列表</span>
        </div>

        <div class="kb-grid">
          <div class="kb-card create-card" @click="openCreateDialog">
            <el-icon class="create-icon"><Plus /></el-icon>
            <div class="create-title">创建知识库</div>
            <div class="create-subtitle">为检索文档创建专属资料空间</div>
          </div>

          <div
            v-for="kb in knowledgeBases"
            :key="kb.id"
            class="kb-card"
            @click="openDocModal(kb)"
          >
            <div class="kb-card-main">
              <div class="kb-icon" :style="getKbBadgeStyle(kb.id)">
                <span class="kb-icon-glow"></span>
                <span class="kb-icon-text">{{ getKbMonogram(kb.name) }}</span>
              </div>
              <div class="kb-card-title-wrap">
                <div class="kb-title">{{ kb.name }}</div>
              </div>
              <div class="kb-meta-pill compact">
                <span class="kb-meta-label">文档</span>
                <span class="kb-meta-value">{{ kb.documentCount || 0 }}</span>
              </div>
            </div>

            <div class="kb-desc kb-desc-bottom">{{ kb.description || '暂无描述' }}</div>

            <div class="kb-card-footer">
              <div class="kb-actions">
                <el-button class="action-btn edit" size="small" @click.stop="openEditDialog(kb)">
                  <el-icon><Edit /></el-icon>
                  编辑
                </el-button>
                <el-button class="action-btn danger" size="small" @click.stop="handleDeleteKb(kb)">
                  <el-icon><Delete /></el-icon>
                  删除
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="doc-page">
        <div class="doc-toolbar">
          <el-button class="back-btn" @click="closeDocDialog">
            <el-icon><Back /></el-icon>
            <span>返回知识库列表</span>
          </el-button>
        </div>

        <div class="doc-summary">
          <div class="doc-summary-main">
            <div class="kb-icon hero-icon" :style="getKbBadgeStyle(currentKb?.id || 'default')">
              <span class="kb-icon-glow"></span>
              <span class="kb-icon-text hero-icon-text">{{ getKbMonogram(currentKb?.name) }}</span>
            </div>
            <div class="doc-summary-copy">
              <div class="doc-summary-title-row">
                <h2 class="doc-summary-title">{{ currentKb?.name }}</h2>
                <div class="doc-summary-desc">{{ currentKb?.description || '暂无描述' }}</div>
              </div>
            </div>
          </div>

          <div class="doc-summary-actions">
            <el-button type="primary" class="upload-btn" @click="triggerFileSelect">
              <el-icon><Upload /></el-icon>
              上传文档
            </el-button>
            <div class="summary-stat">
              <span class="summary-stat-label">文档数量</span>
              <span class="summary-stat-value">{{ documents.length }}</span>
            </div>
            <div class="summary-stat">
              <span class="summary-stat-label">切片数量</span>
              <span class="summary-stat-value">{{ totalChunkCount }}</span>
            </div>
            <div class="summary-stat">
              <span class="summary-stat-label">总大小</span>
              <span class="summary-stat-value">{{ totalFileSize }}</span>
            </div>
          </div>
        </div>

        <input
          ref="fileInputRef"
          type="file"
          class="hidden-file-input"
          accept=".txt,.md,.pdf,.doc,.docx"
          @change="handleFileSelect"
        />

        <div v-if="uploadStatus" :class="['upload-banner', uploadStatus.type]">
          {{ uploadStatus.message }}
        </div>

        <div class="doc-table-panel">
          <div class="panel-header">
            <div>
              <div class="panel-title">文档列表</div>
              <div class="panel-subtitle">查看处理状态、预览切片，并对失败的文档重新处理。</div>
            </div>
          </div>

          <el-table v-loading="loadingDocs" :data="documents" style="width: 100%">
            <el-table-column label="文件信息" min-width="320">
              <template #default="{ row }">
                <div class="doc-file-cell">
                  <div class="doc-file-icon">{{ getFileIcon(row.filename) }}</div>
                  <div class="doc-file-info">
                    <div class="doc-file-name">{{ row.filename }}</div>
                    <div class="doc-file-meta">文件哈希：{{ row.fileHash || '-' }}</div>
                    <div v-if="row.errorMessage" class="doc-file-meta">错误信息：{{ row.errorMessage }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">
                  {{ documentStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="切片数" width="100" prop="chunkCount" />

            <el-table-column label="文件大小" width="120">
              <template #default="{ row }">
                {{ formatFileSize(row.fileSize) }}
              </template>
            </el-table-column>

            <el-table-column label="创建时间" width="180">
              <template #default="{ row }">
                {{ formatDate(row.createdAt) }}
              </template>
            </el-table-column>

            <el-table-column label="更新时间" width="180">
              <template #default="{ row }">
                {{ formatDate(row.updatedAt) }}
              </template>
            </el-table-column>

            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <div class="doc-action-group">
                  <el-button size="small" @click="showPreview(row)">
                    <el-icon><View /></el-icon>
                    预览
                  </el-button>
                  <el-button
                    v-if="row.status === 'FAILED'"
                    size="small"
                    type="warning"
                    @click="handleRetryDoc(row)"
                  >
                    重试
                  </el-button>
                  <el-button size="small" type="danger" @click="handleDeleteDoc(row)">
                    <el-icon><Delete /></el-icon>
                    删除
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="documents.length === 0 && !loadingDocs" class="empty-state">
            <el-empty description="当前还没有文档，先上传一个开始构建知识库。" />
          </div>
        </div>
      </div>

      <el-dialog
        v-model="showCreateDialog"
        :title="editingKb ? '编辑知识库' : '创建知识库'"
        width="500px"
        :close-on-click-modal="false"
      >
        <el-form :model="kbForm" label-width="100px">
          <el-form-item label="名称" required>
            <el-input v-model="kbForm.name" placeholder="例如：产品手册、员工手册、内部 FAQ" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input
              v-model="kbForm.description"
              type="textarea"
              :rows="3"
              placeholder="简单说明这个知识库准备存放哪些内容。"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showCreateDialog = false">取消</el-button>
          <el-button type="primary" @click="submitKbForm">
            {{ editingKb ? '保存' : '创建' }}
          </el-button>
        </template>
      </el-dialog>

      <el-dialog
        v-model="showPreviewDialog"
        :title="`文档切片预览 - ${previewDoc?.filename || ''}`"
        width="820px"
      >
        <div v-if="previewStats" class="preview-stats">
          <el-tag type="info" size="small">切片数：{{ previewStats.total }}</el-tag>
          <el-tag type="info" size="small">平均长度：{{ previewStats.avgLength }}</el-tag>
          <el-tag type="info" size="small">
            长度范围：{{ previewStats.minLength }} - {{ previewStats.maxLength }}
          </el-tag>
        </div>
        <div class="preview-chunks">
          <div v-for="chunk in previewChunks" :key="chunk.index" class="chunk-item">
            <div class="chunk-header">
              <span>切片 #{{ chunk.index + 1 }}</span>
              <span>{{ chunk.length }} 字符</span>
            </div>
            <div class="chunk-content">{{ chunk.content }}</div>
          </div>
        </div>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Back, Close, Delete, Edit, Folder, Plus, Upload, View } from '@element-plus/icons-vue'
import * as api from '../api.js'

const emit = defineEmits(['close'])

const ACTIVE_DOCUMENT_STATUSES = new Set(['PENDING', 'PROCESSING'])
const DOCUMENT_STATUS_META = {
  PENDING: { label: '排队中', type: 'info' },
  PROCESSING: { label: '处理中', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  FAILED: { label: '失败', type: 'danger' }
}

const gradients = [
  'linear-gradient(135deg, #5b8def 0%, #83b6ff 100%)',
  'linear-gradient(135deg, #2f855a 0%, #68d391 100%)',
  'linear-gradient(135deg, #dd6b20 0%, #f6ad55 100%)',
  'linear-gradient(135deg, #0ea5e9 0%, #67e8f9 100%)',
  'linear-gradient(135deg, #7c3aed 0%, #c084fc 100%)',
  'linear-gradient(135deg, #db2777 0%, #f9a8d4 100%)'
]

const knowledgeBases = ref([])
const documents = ref([])
const currentKb = ref(null)
const editingKb = ref(null)
const loadingDocs = ref(false)
const showCreateDialog = ref(false)
const showDocDialog = ref(false)
const showPreviewDialog = ref(false)
const fileInputRef = ref(null)
const uploadStatus = ref(null)
const previewDoc = ref(null)
const previewChunks = ref([])
const previewStats = ref(null)

const kbForm = reactive({
  name: '',
  description: ''
})

let documentPollingTimer = null

const totalChunkCount = computed(() => documents.value.reduce((sum, doc) => sum + (doc.chunkCount || 0), 0))
const totalFileSize = computed(() => formatFileSize(documents.value.reduce((sum, doc) => sum + (doc.fileSize || 0), 0)))

function getErrorMessage(error, fallback = '操作失败') {
  return error?.message || fallback
}

onMounted(() => {
  loadKnowledgeBases()
})

onUnmounted(() => {
  stopDocumentPolling()
})

function getGradientById(id) {
  const raw = String(id || 'default')
  let hash = 0
  for (let index = 0; index < raw.length; index += 1) {
    hash = ((hash << 5) - hash) + raw.charCodeAt(index)
    hash &= hash
  }
  return gradients[Math.abs(hash) % gradients.length]
}

function getKbBadgeStyle(id) {
  return { background: getGradientById(id) }
}

function getKbMonogram(name) {
  const safeName = String(name || '知识库').trim().replace(/\s+/g, '')
  if (!safeName) {
    return 'KB'
  }
  if (/[\u4e00-\u9fa5]/.test(safeName)) {
    return safeName.slice(0, 2)
  }
  return safeName.slice(0, 2).toUpperCase()
}

async function loadKnowledgeBases() {
  try {
    const items = await api.getKnowledgeBases()
    knowledgeBases.value = items
    syncCurrentKnowledgeBase()
    window.dispatchEvent(new CustomEvent('knowledge-bases-updated'))
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '加载知识库失败'))
  }
}

function syncCurrentKnowledgeBase() {
  if (!currentKb.value) {
    return
  }
  const latest = knowledgeBases.value.find(item => item.id === currentKb.value.id)
  if (latest) {
    currentKb.value = latest
  }
}

function stopDocumentPolling() {
  if (documentPollingTimer) {
    window.clearTimeout(documentPollingTimer)
    documentPollingTimer = null
  }
}

function refreshDocumentPolling() {
  stopDocumentPolling()
  if (!showDocDialog.value || !documents.value.some(doc => ACTIVE_DOCUMENT_STATUSES.has(doc.status))) {
    return
  }
  documentPollingTimer = window.setTimeout(async () => {
    const reloaded = await loadDocuments(true)
    if (reloaded) {
      await loadKnowledgeBases()
    }
  }, 3000)
}

function openCreateDialog() {
  editingKb.value = null
  kbForm.name = ''
  kbForm.description = ''
  showCreateDialog.value = true
}

function openEditDialog(kb) {
  editingKb.value = kb
  kbForm.name = kb.name || ''
  kbForm.description = kb.description || ''
  showCreateDialog.value = true
}

async function submitKbForm() {
  const name = kbForm.name.trim()
  const description = kbForm.description.trim()

  if (!name) {
    ElMessage.warning('请输入知识库名称')
    return
  }

  try {
    if (editingKb.value) {
      await api.updateKnowledgeBase(editingKb.value.id, name, description)
      ElMessage.success('知识库已更新')
    } else {
      await api.createKnowledgeBase(name, description)
      ElMessage.success('知识库创建成功')
    }
    showCreateDialog.value = false
    await loadKnowledgeBases()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, editingKb.value ? '更新知识库失败' : '创建知识库失败'))
  }
}

async function handleDeleteKb(kb) {
  try {
    await ElMessageBox.confirm(
      `确定删除“${kb.name}”吗？此操作不可恢复。`,
      '警告',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await api.deleteKnowledgeBase(kb.id)
    ElMessage.success('知识库已删除')
    await loadKnowledgeBases()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(getErrorMessage(error, '删除知识库失败'))
    }
  }
}

async function openDocModal(kb) {
  currentKb.value = kb
  documents.value = []
  uploadStatus.value = null
  showDocDialog.value = true
  await loadDocuments()
}

function closeDocDialog() {
  stopDocumentPolling()
  documents.value = []
  currentKb.value = null
  uploadStatus.value = null
  showDocDialog.value = false
}

async function loadDocuments(silent = false) {
  if (!currentKb.value) {
    return false
  }

  if (!silent) {
    loadingDocs.value = true
  }

  try {
    documents.value = await api.getDocumentsByKbId(currentKb.value.id)
    updateDocumentCount(currentKb.value.id, documents.value.length)
    refreshDocumentPolling()
    return true
  } catch (error) {
    stopDocumentPolling()
    if (!silent) {
      ElMessage.error(getErrorMessage(error, '加载文档失败'))
    }
    return false
  } finally {
    if (!silent) {
      loadingDocs.value = false
    }
  }
}

function updateDocumentCount(kbId, count) {
  const kb = knowledgeBases.value.find(item => item.id === kbId)
  if (kb) {
    kb.documentCount = count
  }
  if (currentKb.value?.id === kbId) {
    currentKb.value = {
      ...currentKb.value,
      documentCount: count
    }
  }
}

function triggerFileSelect() {
  fileInputRef.value?.click()
}

function handleFileSelect(event) {
  const file = event.target.files?.[0]
  if (file) {
    uploadFile(file)
  }
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

async function uploadFile(file) {
  if (!currentKb.value) {
    return
  }

  uploadStatus.value = { type: 'loading', message: `正在上传 ${file.name}...` }
  try {
    const result = await api.uploadDocument(file, currentKb.value.id)
    uploadStatus.value = { type: 'success', message: result }
    await loadDocuments()
    await loadKnowledgeBases()
  } catch (error) {
    uploadStatus.value = { type: 'error', message: getErrorMessage(error, '上传失败') }
  }
}

async function handleRetryDoc(doc) {
  try {
    uploadStatus.value = { type: 'loading', message: `正在重试 ${doc.filename}...` }
    const result = await api.retryDocument(doc.id)
    uploadStatus.value = { type: 'success', message: result }
    await loadDocuments()
    await loadKnowledgeBases()
  } catch (error) {
    uploadStatus.value = { type: 'error', message: getErrorMessage(error, '重试失败') }
  }
}

async function showPreview(doc) {
  try {
    const previewUrl = api.getDocumentPreviewUrl(doc.id)
    const link = document.createElement('a')
    link.href = previewUrl
    link.target = '_blank'
    link.rel = 'noopener noreferrer'
    link.click()
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '预览文档失败'))
  }
}

function buildPreviewStats(chunks) {
  if (!chunks.length) {
    return { total: 0, avgLength: 0, minLength: 0, maxLength: 0 }
  }

  const lengths = chunks.map(chunk => chunk.length)
  const totalLength = lengths.reduce((sum, length) => sum + length, 0)
  return {
    total: chunks.length,
    avgLength: Math.round(totalLength / chunks.length),
    minLength: Math.min(...lengths),
    maxLength: Math.max(...lengths)
  }
}

async function handleDeleteDoc(doc) {
  try {
    await ElMessageBox.confirm(
      `确定删除文档“${doc.filename}”吗？此操作不可恢复。`,
      '警告',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await api.deleteDocument(doc.id)
    ElMessage.success('文档已删除')
    await loadDocuments()
    await loadKnowledgeBases()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(getErrorMessage(error, '删除文档失败'))
    }
  }
}

function closeRagModal() {
  stopDocumentPolling()
  emit('close')
}

function getFileIcon(filename) {
  const ext = filename?.split('.').pop()?.toLowerCase() || 'file'
  const icons = {
    pdf: 'PDF',
    doc: 'DOC',
    docx: 'DOC',
    txt: 'TXT',
    md: 'MD',
    xls: 'XLS',
    xlsx: 'XLS',
    csv: 'CSV',
    ppt: 'PPT',
    pptx: 'PPT'
  }
  return icons[ext] || 'FILE'
}

function formatFileSize(bytes) {
  if (!bytes || bytes === 0) {
    return '0 B'
  }
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  const value = bytes / Math.pow(1024, index)
  return `${Math.round(value * 100) / 100} ${units[index]}`
}

function formatDate(dateStr) {
  if (!dateStr) {
    return '-'
  }
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function getStatusType(status) {
  return DOCUMENT_STATUS_META[status]?.type || 'info'
}

function documentStatusLabel(status) {
  return DOCUMENT_STATUS_META[status]?.label || status
}
</script>

<style scoped>
.rag-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.34);
  backdrop-filter: blur(8px);
}

.rag-modal {
  width: min(1240px, 100%);
  height: min(88vh, 920px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--line-soft);
  border-radius: 32px;
  background: rgba(255, 255, 255, 0.84);
  backdrop-filter: blur(24px);
  box-shadow: 0 32px 60px -32px rgba(15, 23, 42, 0.35);
}

.rag-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 28px 20px;
  border-bottom: 1px solid var(--line-soft);
  background:
    radial-gradient(circle at left top, color-mix(in srgb, var(--accent) 12%, transparent), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(255, 255, 255, 0.72));
}

.page-eyebrow {
  margin-bottom: 6px;
  color: var(--text-muted);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.page-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--text-primary);
}

.close-btn,
.back-btn,
.action-btn {
  border-radius: 14px;
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.72);
  color: var(--text-secondary);
}

.section,
.doc-page {
  flex: 1;
  overflow-y: auto;
  padding: 28px;
  background:
    radial-gradient(circle at top right, color-mix(in srgb, var(--accent) 8%, transparent), transparent 26%),
    transparent;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 22px;
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 700;
}

.section-header .el-icon {
  color: var(--accent);
  font-size: 20px;
}

.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 18px;
}

.kb-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 220px;
  padding: 20px;
  border: 1px solid var(--line-soft);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(16px);
  cursor: pointer;
  opacity: 0;
  transform: translateY(10px);
  animation: fadeCard 0.42s ease forwards;
  transition: transform 0.24s ease, border-color 0.24s ease, box-shadow 0.24s ease, opacity 0.24s ease;
}

.kb-card:nth-child(2) { animation-delay: 0.04s; }
.kb-card:nth-child(3) { animation-delay: 0.08s; }
.kb-card:nth-child(4) { animation-delay: 0.12s; }
.kb-card:nth-child(5) { animation-delay: 0.16s; }
.kb-card:nth-child(6) { animation-delay: 0.2s; }

.kb-card:hover {
  transform: translateY(-3px);
  border-color: color-mix(in srgb, var(--accent) 24%, transparent);
  box-shadow: 0 24px 42px -30px var(--accent-shadow);
}

.kb-card.create-card {
  align-items: center;
  justify-content: center;
  text-align: center;
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 70%, white));
  color: white;
  border-color: transparent;
}

.create-icon {
  font-size: 40px;
}

.create-title {
  font-size: 18px;
  font-weight: 700;
}

.create-subtitle {
  font-size: 13px;
  opacity: 0.9;
}

.kb-card-main {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.kb-card-title-wrap {
  flex: 1;
  min-width: 0;
}

.kb-icon {
  position: relative;
  overflow: hidden;
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: 0 20px 34px -24px rgba(15, 23, 42, 0.38);
}

.kb-icon::before {
  content: "";
  position: absolute;
  inset: 1px;
  border-radius: 17px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.24), rgba(255, 255, 255, 0.04));
}

.kb-icon-glow {
  position: absolute;
  width: 58%;
  height: 58%;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.26);
  filter: blur(10px);
  transform: translateY(-14px);
}

.kb-icon-text {
  position: relative;
  z-index: 1;
  font-size: 16px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.kb-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.kb-desc {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

.kb-desc-bottom {
  margin-top: auto;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.kb-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.kb-card-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: auto;
}

.action-btn {
  height: 32px;
  padding: 0 12px;
}

.action-btn.edit {
  color: #2563eb;
  border-color: rgba(37, 99, 235, 0.16);
  background: rgba(37, 99, 235, 0.08);
}

.action-btn.edit:hover {
  color: #1d4ed8;
  border-color: rgba(37, 99, 235, 0.28);
  background: rgba(37, 99, 235, 0.14);
}

.action-btn.danger {
  color: #dc2626;
  border-color: rgba(220, 38, 38, 0.16);
  background: rgba(220, 38, 38, 0.08);
}

.action-btn.danger:hover {
  color: #b91c1c;
  border-color: rgba(220, 38, 38, 0.28);
  background: rgba(220, 38, 38, 0.14);
}

.kb-meta-pill {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 12px;
  border: 1px solid var(--line-soft);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.52);
}

.kb-meta-pill.compact {
  width: 58px;
  min-width: 58px;
  min-height: 56px;
  align-items: center;
  justify-content: center;
  padding: 6px 8px;
  gap: 2px;
  border-radius: 18px;
}

.kb-meta-label {
  color: var(--text-muted);
  font-size: 12px;
}

.kb-meta-value {
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 600;
}

.kb-meta-pill.compact .kb-meta-label {
  font-size: 10px;
}

.kb-meta-pill.compact .kb-meta-value {
  font-size: 14px;
  font-weight: 700;
}

.doc-toolbar {
  margin-bottom: 14px;
}

.doc-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 20px 22px;
  border: 1px solid var(--line-soft);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.78);
}

.doc-summary-main {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.hero-icon {
  width: 68px;
  height: 68px;
  border-radius: 22px;
}

.hero-icon-text {
  font-size: 20px;
}

.doc-summary-title {
  margin: 0;
  color: var(--text-primary);
  font-size: 26px;
  font-weight: 700;
}

.doc-summary-desc {
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.doc-summary-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.upload-btn {
  height: 44px;
  border-radius: 16px;
}

.summary-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 100px;
  padding: 10px 12px;
  border: 1px solid var(--line-soft);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.58);
}

.summary-stat-label {
  color: var(--text-muted);
  font-size: 12px;
}

.summary-stat-value {
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 700;
}

.hidden-file-input { display: none; }

.upload-banner {
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid var(--line-soft);
  border-radius: 16px;
  font-size: 14px;
  font-weight: 600;
}

.upload-banner.loading { color: var(--accent); background: var(--accent-soft); }
.upload-banner.success { color: #15803d; background: rgba(34, 197, 94, 0.12); }
.upload-banner.error { color: #dc2626; background: rgba(239, 68, 68, 0.12); }

.doc-table-panel {
  margin-top: 16px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 18px;
  border: 1px solid var(--line-soft);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.78);
}

.panel-header {
  margin-bottom: 14px;
}

.panel-title {
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 700;
}

.panel-subtitle {
  margin-top: 6px;
  color: var(--text-secondary);
  font-size: 13px;
}

.doc-file-cell {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.doc-file-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: var(--accent-soft);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--accent);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  flex-shrink: 0;
}

.doc-file-name {
  color: var(--text-primary);
  font-weight: 600;
  word-break: break-word;
}

.doc-file-meta {
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
  word-break: break-all;
}

.doc-action-group {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.empty-state {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

.preview-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.preview-chunks {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: 500px;
  overflow-y: auto;
}

.chunk-item {
  overflow: hidden;
  border: 1px solid var(--line-soft);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
}

.chunk-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.74);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.chunk-content {
  max-height: 220px;
  overflow-y: auto;
  padding: 16px;
  color: var(--text-primary);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
}

@keyframes fadeCard {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

:deep(.el-dialog) {
  border-radius: 28px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 30px 50px -28px rgba(15, 23, 42, 0.3);
}

:deep(.el-dialog__header) {
  margin: 0;
  padding: 18px 22px;
  border-bottom: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.82);
}

:deep(.el-dialog__title) {
  color: var(--text-primary);
  font-weight: 700;
}

:deep(.el-dialog__body) {
  padding: 0;
  background: rgba(255, 255, 255, 0.78);
}

:deep(.el-dialog__footer) {
  padding: 14px 22px;
  border-top: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.82);
}

:deep(.el-table) {
  --el-table-border-color: var(--line-soft);
  --el-table-header-bg-color: rgba(255, 255, 255, 0.72);
  --el-table-row-hover-bg-color: color-mix(in srgb, var(--accent) 6%, white);
  border-radius: 22px;
  overflow: hidden;
}

:deep(.el-input__wrapper),
:deep(.el-textarea__inner),
:deep(.el-select__wrapper) {
  border-radius: 14px;
}

@media (max-width: 1100px) {
  .doc-summary {
    flex-direction: column;
    align-items: stretch;
  }

  .doc-summary-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 900px) {
  .rag-modal-mask { padding: 12px; }
  .rag-modal { height: 92vh; border-radius: 24px; }
  .section, .doc-page { padding: 18px; }
  .page-title { font-size: 22px; }
}

@media (max-width: 640px) {
  .kb-grid { grid-template-columns: 1fr; }
  .doc-summary-main { flex-direction: column; align-items: flex-start; }
}
</style>
