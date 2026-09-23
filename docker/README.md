# 个人店铺电商系统 - Docker 部署指南

## 环境要求

- Docker 29.x+
- Docker Compose v5.1+
- 4GB+ 可用内存
- 10GB+ 磁盘空间

## 快速开始

```bash
# 1. 进入项目根目录
cd D:\MallSystem

# 2. 一键部署
docker compose up -d --build

# 3. 查看服务状态
docker compose ps

# 4. 查看日志
docker compose logs -f

# 5. 停止服务
docker compose down

# 6. 清理数据（慎用）
docker compose down -v
```

## 访问地址

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:3000 |
| 后端API | http://localhost:8080 |
| API文档 | http://localhost:8080/doc.html |
| MySQL | localhost:3306 |
| Redis | localhost:6379 |

## 数据库连接信息

- 主机: localhost
- 端口: 3306
- 用户名: root
- 密码: root
- 数据库: mall_db

## 服务架构

```
┌─────────────┐     ┌─────────────┐
│  Frontend    │     │  Backend     │
│  (Nginx)     │────▶│  (Spring     │
│  port: 3000  │     │   Boot)      │
│              │     │  port: 8080  │
└─────────────┘     └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
         ┌────────┐  ┌────────┐  ┌────────┐
         │ MySQL  │  │ Redis  │  │(外部)  │
         │ port:  │  │ port:  │  │        │
         │ 3306   │  │ 6379   │  │        │
         └────────┘  └────────┘  └────────┘
```

## 常用命令

```bash
# 查看所有容器日志
docker compose logs

# 查看后端日志
docker compose logs backend

# 重启后端
docker compose restart backend

# 进入容器
docker exec -it mall-mysql mysql -uroot -proot mall_db
docker exec -it mall-backend sh
docker exec -it mall-frontend sh

# 强制重建服务
docker compose up -d --build --force-recreate backend
```

## 自定义配置

### 修改数据库密码

编辑 `docker-compose.yml`:
```yaml
environment:
  MYSQL_ROOT_PASSWORD: your_password
```

同时修改 `backend/src/main/resources/application.yml` 中的密码。

### 修改端口

```yaml
# 前端端口
ports:
  - "80:80"    # 改为需要的端口

# 后端端口
ports:
  - "8081:8080" # 改为需要的端口
```

## 问题排查

### 容器启动失败
```bash
docker compose logs backend
```

### 数据库连接失败
```bash
# 检查MySQL是否就绪
docker compose ps mysql

# 进入MySQL容器检查
docker exec -it mall-mysql mysql -uroot -proot -e "SHOW DATABASES;"
```

### 前端访问不到后端API
```bash
# 检查Nginx配置
docker exec -it mall-frontend cat /etc/nginx/conf.d/default.conf
```

## 镜像缓存（无网络时）

如果无法访问 Docker Hub，可以手动构建基础镜像：

```bash
# 使用本机 Java 构建后端
cd backend
# 使用 javac 编译（需要手动处理依赖）
# 或者将目标 JAR 文件复制到 Docker context 中

# 使用本机 Node.js 构建前端
cd frontend
npm install
npm run build

# 然后使用本地镜像构建
docker build -t mall-backend:local -f backend/Dockerfile .
```
