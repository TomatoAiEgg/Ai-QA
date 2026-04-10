# AI-QA

AI-QA 是一个基于 Spring Boot + Vue 的智能问答系统，支持普通 AI 对话、RAG 知识库问答、文档异步处理、OpenAPI 上传和 MinIO 文档存储。

## 当前能力

- 普通 AI 对话
- RAG 对话
- 多知识库管理
- 文档上传、删除、重试、原文件预览
- RocketMQ 异步文档处理
- MinIO 文档存储
- OpenAPI Token 与文档上传
- Redis 登录态和缓存

## 技术栈

### 后端

- Spring Boot 3.4
- Spring AI
- MyBatis-Plus
- PostgreSQL + pgvector
- Redis
- Sa-Token
- RocketMQ
- MinIO

### 前端

- Vue 3
- Vite
- Element Plus

## 项目结构

```text
src/main/java/cn/net/tomatoegg/ai
  controller/
    auth/
    chat/
    conversation/
    document/
    knowledgebase/
    openapi/
  service/
    auth/
    chat/
    conversation/
    document/
    embedding/
    knowledgebase/
    model/
    openapi/
  mq/rocketmq/document/
  handler/
  mapper/
```

## 文档处理链路

1. 上传文档
2. 文件写入 MinIO
3. 文档记录入库
4. 发送 RocketMQ 消息
5. 消费者异步处理：
   - 读取原始文件
   - 文本切片
   - 批量向量化
   - 写入 pgvector
   - 更新文档状态

## 运行依赖

项目当前默认依赖以下服务：

- PostgreSQL
- Redis
- MinIO
- RocketMQ

其中：

- 文档存储默认走 MinIO
- 文档异步处理默认走 RocketMQ

## 配置说明

核心配置位于：

- `src/main/resources/application.yml`

### 重点配置

#### 数据库

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/Ai-QA
    username: postgres
    password: 123456
```

#### Redis

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

#### RocketMQ

```yaml
ai:
  service:
    rocketmq:
      document-process:
        enabled: true
        name-server: 127.0.0.1:9876
        topic: aiqa_document_process
```

#### MinIO

```yaml
ai:
  service:
    document:
      storage:
        type: minio
        minio:
          endpoint: http://127.0.0.1:9100
          access-key: minioadmin
          secret-key: minioadmin
          bucket: aiqa-documents
          auto-create-bucket: true
```

## 本地开发

### 1. 启动基础服务

你需要先自行准备：

- PostgreSQL
- Redis
- RocketMQ NameServer / Broker
- MinIO

### 2. 启动 MinIO

如果你已经把 `minio.exe` 放在 `D:\Javasource\minIO`，可以直接使用：

- `D:\Javasource\minIO\start-minio-local.bat`
- `D:\Javasource\minIO\stop-minio-local.bat`

默认地址：

- API: `http://127.0.0.1:9100`
- Console: `http://127.0.0.1:9101`

默认账号密码：

- `minioadmin`
- `minioadmin`

### 3. 启动 RocketMQ

如果使用本地安装版：

```bash
mqnamesrv.cmd
mqbroker.cmd -n 127.0.0.1:9876 autoCreateTopicEnable=true
```

### 4. 启动后端

```bash
.\mvnw.cmd spring-boot:run
```

默认端口：

- `7000`

### 5. 启动前端

```bash
cd ui
npm install
npm run dev
```

## Docker Compose

仓库内已提供 `docker-compose.yml`，当前包含：

- PostgreSQL
- Redis
- MinIO
- Backend
- Nginx
- Portainer（可选）

启动：

```bash
docker compose up -d --build
```

MinIO 默认端口映射：

- `9100 -> 9000`
- `9101 -> 9001`

## 预览说明

- `pdf`、`txt`、`md` 通常可以直接在浏览器预览
- `doc`、`docx` 是否内联展示取决于浏览器自身能力
- MinIO 文件预览会跳转到签名地址
- 本地文件预览会走后端文件流

## 注意事项

- 数据库初始化和历史数据导入当前按手工方式处理，不依赖仓库内自动建库脚本
- 旧的本地存储文档会兼容读取，但新上传文档默认进入 MinIO
- 如果要关闭 MQ，手动调整 `AI_DOCUMENT_PROCESS_MQ_ENABLED=false`
- 如果要切回本地存储，手动调整 `AI_DOCUMENT_STORAGE_TYPE=local`

## 已知问题

- 前端部分页面仍存在历史乱码文案，后续需要继续清理
- 前端主包较大，Vite 构建会提示 chunk size 警告
- 自动化测试覆盖率仍不足

## 仓库地址

- GitHub: `https://github.com/TomatoAiEgg/Ai-QA`
