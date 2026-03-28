# AI-QA

AI-QA 是一个面向知识库问答场景的 AI 工作台项目，支持普通对话、RAG 检索增强对话、知识库管理、文档上传、历史会话、Redis 登录态和 OpenAPI 文档上传。

## 技术栈

- 后端：Spring Boot 3、Spring AI、MyBatis-Plus、Sa-Token
- 前端：Vue 3、Vite、Element Plus
- 数据库：PostgreSQL + pgvector
- 缓存：Redis
- 部署：Docker Compose、Nginx

## GitHub 用户快速部署

服务端只需要安装好 Docker 和 Docker Compose，不需要手工安装 Java、Maven、Node。

### 1. 拉取项目

```bash
cd /opt/projects
git clone https://github.com/TomatoAiEgg/Ai-QA.git ai-qa
cd /opt/projects/ai-qa
```

### 2. 创建宿主机目录

```bash
sudo mkdir -p /opt/data/ai-qa/postgres
sudo mkdir -p /opt/data/ai-qa/redis
sudo mkdir -p /opt/data/ai-qa/kb-files
sudo mkdir -p /opt/logs/ai-qa
sudo mkdir -p /opt/logs/nginx
```

### 3. 配置环境变量

```bash
cp .env.example .env
vim .env
```

至少需要填写：

```env
QWEN_API_KEY=你的真实千问秘钥
```

说明：
- `.env.example` 可以提交到 GitHub
- `.env` 只保留在服务器本地，不要提交

### 4. 一键启动

```bash
docker compose up -d --build
```

### 5. 查看状态

```bash
docker compose ps
docker compose logs -f
```

默认端口：
- 前端入口：`80`
- 后端服务：`7000`
- PostgreSQL：`5432`
- Redis：`6379`

## 首次启动说明

- `postgres` 容器会在第一次初始化时自动执行：
  - `database/susan_ai_schema.sql`
  - `database/susan_ai_data.sql`
  - `database/vector_store_1024_v2.sql`
- 如果你已经有旧的 PostgreSQL 数据目录，初始化 SQL 不会再次自动执行

如果要重置数据库后重建：

```bash
docker compose down
sudo rm -rf /opt/data/ai-qa/postgres
sudo mkdir -p /opt/data/ai-qa/postgres
docker compose up -d --build
```

## 日志与数据目录

- 项目日志：`/opt/logs/ai-qa`
- Nginx 日志：`/opt/logs/nginx`
- PostgreSQL 数据：`/opt/data/ai-qa/postgres`
- Redis 数据：`/opt/data/ai-qa/redis`
- 知识库原始文件：`/opt/data/ai-qa/kb-files`

## 常用命令

更新代码并重建：

```bash
cd /opt/projects/ai-qa
git pull
docker compose up -d --build
```

停止服务：

```bash
docker compose down
```

查看单个服务日志：

```bash
docker compose logs -f backend
docker compose logs -f postgres
docker compose logs -f redis
docker compose logs -f nginx
```

## 项目结构

```text
AI-QA/
├─ src/                      后端源码
├─ ui/                       前端源码
├─ database/                 数据库初始化脚本
├─ deploy/nginx/             Nginx 反向代理配置
├─ Dockerfile                后端镜像构建
├─ ui/Dockerfile             前端镜像构建
├─ docker-compose.yml        一键部署编排
├─ .env.example              环境变量模板
└─ docs/                     项目文档
```

## 当前部署方式

当前仓库默认以 Docker Compose 为主：
- 前端由 Nginx 托管并反向代理后端
- 后端容器自动构建并启动
- PostgreSQL 使用 `pgvector/pgvector:pg17-trixie`
- Redis 使用 `redis:7.4`

如果你只是想从 GitHub 拉下来直接部署，按上面的“快速部署”执行即可。
