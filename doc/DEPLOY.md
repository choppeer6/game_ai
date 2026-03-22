# Docker 部署说明

## 1. 前置条件

- 已安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)（Windows/macOS）或 Docker Engine + Compose（Linux）。
- 首次构建会下载基础镜像并编译前后端，耗时约数分钟；算法镜像需安装 PyTorch，体积较大。

## 2. 端口规划（宿主机）

为避免与本机已运行的 MySQL/Redis 冲突，对外映射如下：

| 用途 | 宿主机端口 | 容器内端口 | 说明 |
|------|------------|------------|------|
| **Web 入口（推荐）** | **3000** | 80 | Nginx 静态资源 + 反代 `/api`、`/ws` |
| **后端 API（直连调试）** | **8080** | 8080 | 可跳过 Nginx 用 Postman 调 REST |
| **MySQL** | **3307** | 3306 | 本机若已有 3306，用 3307 连接库 |
| **Redis** | **6380** | 6379 | 本机若已有 6379，用 6380 连接缓存 |
| **算法引擎** | 不映射 | 8000 | 仅 Docker 网络内由 `backend` 访问 |

容器之间仍使用 **mysql:3306、redis:6379、engine:8000、backend:8080** 通信。

## 3. 启动与停止

在项目根目录（含 `docker-compose.yml`）执行：

```bash
docker compose up -d --build
```

- 浏览器访问：**http://localhost:3000**
- 默认账号：`admin` / `admin123`

查看日志：

```bash
docker compose logs -f backend
docker compose logs -f engine
```

停止并删除容器（保留数据卷）：

```bash
docker compose down
```

停止并删除数据卷（**清空数据库与上传、检查点**）：

```bash
docker compose down -v
```

## 4. 环境变量（可选）

可在项目根目录创建 `.env`（与 `docker-compose.yml` 同级）：

```env
MYSQL_ROOT_PASSWORD=root
```

修改后需与 `SPRING_DATASOURCE_PASSWORD` 一致（Compose 已用同一变量注入后端）。

生产环境请修改 `JWT_SECRET`，并在 `docker-compose.yml` 的 `backend.environment` 中增加：

```yaml
JWT_SECRET: <至少32字符的随机串>
```

## 5. 数据持久化

| 卷名 | 内容 |
|------|------|
| `mysql_data` | MySQL 数据文件 |
| `engine_checkpoints` | 训练生成的策略权重 |
| `backend_uploads` | 数据集上传目录 |

## 6. 常见问题

1. **首次启动后端报错连接数据库失败**  
   等待 MySQL `healthy` 后再试；可执行 `docker compose ps` 查看状态，或 `docker compose restart backend`。

2. **3000 端口被占用**  
   修改 `docker-compose.yml` 中 `web.ports` 为 `"3100:80"` 等。

3. **仅开发前端、仍连本机后端**  
   继续使用 `npm run dev`（5173），无需 Docker；此时 `vite.config.ts` 里代理仍指向 `127.0.0.1:8080`。

4. **算法镜像过大**  
   当前为 CPU 版 PyTorch；若仅需演示推理，可后续改为精简依赖或分阶段构建。

5. **Apple Silicon（M 系列）构建失败**  
   可在 `docker compose build` 前对 `engine` 服务指定平台，或在 `docker-compose.yml` 的 `engine` 下增加：`platform: linux/amd64`（运行速度可能较慢）。
