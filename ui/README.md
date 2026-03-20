# Susan AI 知识库管理前端

基于 Vue 3 + Vite + Element Plus 的 RAG 知识库管理界面。

## 技术栈

- Vue 3 - 渐进式 JavaScript 框架
- Vite - 下一代前端构建工具
- Element Plus - 基于 Vue 3 的组件库
- Axios - HTTP 客户端

## 快速开始

### 1. 安装依赖

```bash
cd ui
npm install
```

### 2. 启动开发服务器

确保后端服务运行在 `http://localhost:7000`

```bash
npm run dev
```

访问 `http://localhost:3000`

### 3. 构建生产版本

```bash
npm run build
```

构建产物输出到 `dist/` 目录，可复制到后端的 `src/main/resources/static` 目录。

## 项目结构

```
ui/
├── src/
│   ├── api.js          # API 接口
│   ├── App.vue         # 主应用组件
│   ├── main.js         # 入口文件
│   └── styles/
│       └── rag.css     # 样式文件
├── index.html
├── package.json
└── vite.config.js
```

## API 接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/knowledge-bases` | GET | 获取知识库列表 |
| `/api/knowledge-bases` | POST | 创建知识库 |
| `/api/knowledge-bases/:id` | DELETE | 删除知识库 |
| `/api/knowledge-bases/:id/documents` | GET | 获取知识库文档列表 |
| `/ai/upload` | POST | 上传文档 |
| `/ai/preview-chunks` | POST | 预览文档切片 |
| `/ai/documents/:id` | DELETE | 删除文档 |

## 开发说明

### 组件拆分

- `App.vue` - 主页面，包含知识库列表和文档管理对话框
- `api.js` - 统一的 API 调用封装
- `styles/rag.css` - 独立的样式文件

### 样式规范

- 使用 CSS 变量（Element Plus 主题色）
- 响应式布局
- 模块化拆分
