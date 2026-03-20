<template>
  <div class="rag-container">
    <div class="rag-header">
      <h1 class="page-title">📚 RAG 知识库管理</h1>
      <el-button class="back-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回对话
      </el-button>
    </div>

    <!-- 知识库列表 -->
    <div class="section">
      <div class="section-header">
        <el-icon><Folder /></el-icon>
        <span class="section-title">知识库列表</span>
      </div>

      <div class="kb-grid">
        <!-- 创建知识库卡片 -->
        <div class="kb-card create-card" @click="showCreateDialog = true">
          <el-icon class="create-icon"><Plus /></el-icon>
          <span>创建知识库</span>
        </div>

        <!-- 知识库卡片 -->
        <div
          v-for="kb in knowledgeBases"
          :key="kb.id"
          class="kb-card"
          @click="openDocModal(kb)"
        >
          <div class="kb-card-header">
            <div class="kb-icon" :style="{ background: kb.coverColor || getRandomColor(kb.id) }">
              📚
            </div>
            <div class="kb-info">
              <div class="kb-title">{{ kb.name }}</div>
              <div class="kb-desc">{{ kb.description || '暂无描述' }}</div>
            </div>
          </div>
          <div class="kb-footer">
            <span class="doc-count">📄 {{ kb.documentCount || 0 }} 个文档</span>
            <el-button
              class="delete-btn"
              type="danger"
              :icon="Delete"
              circle
              size="small"
              @click.stop="handleDeleteKb(kb)"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 创建知识库对话框 -->
    <el-dialog
      v-model="showCreateDialog"
      title="创建知识库"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" placeholder="例如：公司制度、产品手册" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="简要描述知识库的用途..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateKb">创建</el-button>
      </template>
    </el-dialog>

    <!-- 文档管理对话框 -->
    <el-dialog
      v-model="showDocDialog"
      :title="currentKb?.name"
      width="90%"
      top="5vh"
      :close-on-click-modal="false"
      class="doc-dialog"
    >
      <div class="doc-dialog-content">
        <!-- 上传区域 -->
        <div class="upload-section" @dragover.prevent @dragleave.prevent @drop.prevent="handleDrop">
          <input
            ref="fileInputRef"
            type="file"
            style="display: none"
            accept=".txt,.md,.pdf,.docx"
            @change="handleFileSelect"
          />
          <el-button type="primary" @click="$refs.fileInputRef.click()">
            <el-icon><Upload /></el-icon>
            上传文档
          </el-button>
          <p class="upload-hint">点击按钮或拖拽文件到此处上传 · 支持 TXT, Markdown, PDF, Word 文档</p>
          <div v-if="uploadStatus" :class="['status-msg', uploadStatus.type]">
            {{ uploadStatus.message }}
          </div>
        </div>

        <!-- 文档列表 -->
        <el-table :data="documents" style="width: 100%" v-loading="loadingDocs">
          <el-table-column label="文件名" min-width="200">
            <template #default="{ row }">
              <div class="doc-file-cell">
                <div class="doc-file-icon">{{ getFileIcon(row.filename) }}</div>
                <div class="doc-file-info">
                  <div class="doc-file-name">{{ row.filename }}</div>
                  <div class="doc-file-meta">{{ getFileExt(row.filename) }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)" size="small">
                {{ getStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="切片数量" width="100" prop="chunkCount" />
          <el-table-column label="文件大小" width="100">
            <template #default="{ row }">
              {{ formatFileSize(row.fileSize) }}
            </template>
          </el-table-column>
          <el-table-column label="上传时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="showPreview(row)">
                <el-icon><View /></el-icon>
                预览
              </el-button>
              <el-button size="small" type="danger" @click="handleDeleteDoc(row)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="documents.length === 0 && !loadingDocs" class="empty-state">
          <el-empty description="暂无文档，请上传" />
        </div>
      </div>
    </el-dialog>

    <!-- 切片预览对话框 -->
    <el-dialog
      v-model="showPreviewDialog"
      :title="`文档切片预览 - ${previewDoc?.filename}`"
      width="800px"
    >
      <div v-if="previewStats" class="preview-stats">
        <el-tag type="info" size="small">共 {{ previewStats.total }} 个片段</el-tag>
        <el-tag type="info" size="small">平均长度：{{ previewStats.avgLength }} 字符</el-tag>
        <el-tag type="info" size="small">最小：{{ previewStats.minLength }} | 最大：{{ previewStats.maxLength }}</el-tag>
      </div>
      <div class="preview-chunks">
        <div v-for="chunk in previewChunks" :key="chunk.index" class="chunk-item">
          <div class="chunk-header">
            <span>片段 #{{ chunk.index + 1 }}</span>
            <span>{{ chunk.length }} 字符</span>
          </div>
          <div class="chunk-content">{{ chunk.content }}</div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '../api.js'

const router = useRouter()

// 状态
const knowledgeBases = ref([])
const documents = ref([])
const currentKb = ref(null)
const loadingDocs = ref(false)
const showCreateDialog = ref(false)
const showDocDialog = ref(false)
const showPreviewDialog = ref(false)
const fileInputRef = ref(null)

// 创建表单
const createForm = reactive({
  name: '',
  description: ''
})

// 上传状态
const uploadStatus = ref(null)

// 预览数据
const previewDoc = ref(null)
const previewChunks = ref([])
const previewStats = ref(null)

// 颜色
const colors = ['#667eea', '#f093fb', '#4facfe', '#43e97b', '#fa709a', '#fee140']

// 生命周期
onMounted(() => {
  loadKnowledgeBases()
})

// 方法
const getRandomColor = (id) => {
  let hash = 0
  for (let i = 0; i < id.length; i++) {
    hash = ((hash << 5) - hash) + id.charCodeAt(i)
    hash = hash & hash
  }
  return colors[Math.abs(hash) % colors.length]
}

const loadKnowledgeBases = async () => {
  try {
    knowledgeBases.value = await api.getKnowledgeBases()
  } catch (error) {
    ElMessage.error('加载知识库列表失败：' + error.message)
  }
}

const handleCreateKb = async () => {
  if (!createForm.name) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  try {
    const kb = await api.createKnowledgeBase(createForm.name, createForm.description)
    ElMessage.success('知识库创建成功')
    showCreateDialog.value = false
    createForm.name = ''
    createForm.description = ''
    loadKnowledgeBases()
  } catch (error) {
    ElMessage.error('创建失败：' + error.message)
  }
}

const handleDeleteKb = async (kb) => {
  try {
    await ElMessageBox.confirm(`确定要删除"${kb.name}"吗？该操作不可恢复。`, '警告', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await api.deleteKnowledgeBase(kb.id)
    ElMessage.success('已删除')
    loadKnowledgeBases()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败：' + error.message)
    }
  }
}

const openDocModal = async (kb) => {
  currentKb.value = kb
  showDocDialog.value = true
  await loadDocuments()
}

const loadDocuments = async () => {
  if (!currentKb.value) return
  loadingDocs.value = true
  try {
    documents.value = await api.getDocumentsByKbId(currentKb.value.id)
    // 更新知识库的文档数量
    const kb = knowledgeBases.value.find(k => k.id === currentKb.value.id)
    if (kb) kb.documentCount = documents.value.length
  } catch (error) {
    ElMessage.error('加载文档列表失败：' + error.message)
  } finally {
    loadingDocs.value = false
  }
}

const handleFileSelect = (e) => {
  const file = e.target.files[0]
  if (file) uploadFile(file)
  fileInputRef.value.value = ''
}

const handleDrop = (e) => {
  const file = e.dataTransfer.files[0]
  if (file) uploadFile(file)
}

const uploadFile = async (file) => {
  if (!currentKb.value) return

  uploadStatus.value = { type: 'loading', message: '正在上传处理中...' }

  try {
    // 预览切片
    const previewData = await api.previewChunks(file)
    if (previewData.error) throw new Error(previewData.error)
    showUploadPreview(file.name, previewData)

    // 正式上传
    const result = await api.uploadDocument(file, currentKb.value.id)
    uploadStatus.value = { type: 'success', message: '✅ ' + result }
    await loadDocuments()
    loadKnowledgeBases()
  } catch (error) {
    uploadStatus.value = { type: 'error', message: '❌ ' + error.message }
  }
}

const showUploadPreview = (filename, chunks) => {
  previewDoc.value = { filename }
  previewChunks.value = chunks.map((c, i) => ({
    index: i,
    content: c.content,
    length: c.length
  }))
  previewStats.value = {
    total: chunks.length,
    avgLength: Math.round(chunks.reduce((sum, c) => sum + c.length, 0) / chunks.length),
    minLength: Math.min(...chunks.map(c => c.length)),
    maxLength: Math.max(...chunks.map(c => c.length))
  }
  showPreviewDialog.value = true
}

const showPreview = async (doc) => {
  previewDoc.value = doc
  previewChunks.value = []
  previewStats.value = null
  showPreviewDialog.value = true
}

const handleDeleteDoc = async (doc) => {
  try {
    await ElMessageBox.confirm('确定要删除这个文档吗？', '警告', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await api.deleteDocument(doc.id)
    ElMessage.success('已删除')
    await loadDocuments()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败：' + error.message)
    }
  }
}

// 返回对话页面
const goBack = () => {
  router.push('/')
}

// 工具函数
const getFileExt = (filename) => {
  const ext = filename.split('.').pop()?.toLowerCase() || 'file'
  return ext.toUpperCase()
}

const getFileIcon = (filename) => {
  const ext = filename.split('.').pop()?.toLowerCase() || 'file'
  const icons = {
    pdf: '📄', doc: '📝', docx: '📝', txt: '📃', md: '📝',
    xls: '📊', xlsx: '📊', csv: '📊', ppt: '📊', pptx: '📊'
  }
  return icons[ext] || '📁'
}

const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i]
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

const getStatusType = (status) => {
  const map = { PROCESSING: 'warning', COMPLETED: 'success', FAILED: 'danger' }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = { PROCESSING: '处理中', COMPLETED: '已完成', FAILED: '失败' }
  return map[status] || status
}
</script>

<style scoped>
@import '../styles/rag.css';
</style>
