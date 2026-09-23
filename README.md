# 个人店铺电商系统

面向**单个店主**的电商系统：店主管理商品/分类/订单/优惠券并查看经营数据，消费者浏览、加购、下单。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.1.5 / Java 17 / MyBatis-Plus 3.5.3 |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis 7（用于 JWT 登出黑名单） |
| 鉴权 | 自研 JWT（HS256）+ BCrypt 密码哈希 |
| 前端 | Vue 3.4 + TypeScript 5.3 + Element Plus 2.5 + Pinia + Vite 5 |
| 接口文档 | SpringDoc / OpenAPI（`/doc.html`） |
| 部署 | Docker Compose（MySQL + Redis + backend + frontend） |

---

## 快速开始

### 方式一：Docker Compose（推荐）

```bash
# 1. 配置 JWT 密钥（必填，见下方「环境变量」）
cp .env.example .env
# 编辑 .env，把 JWT_SECRET 换成随机值

# 2. 一键启动
docker-compose up -d

# 3. 查看状态
docker-compose ps
```

MySQL 容器首次启动时会自动执行 `docker/mysql/init/init.sql` 建库并灌入测试数据。

### 方式二：本地开发

```bash
# 1. 建库（全项目唯一权威建库脚本）
mysql -u root -p < docker/mysql/init/init.sql

# 2. 启动 MySQL 与 Redis，然后配置环境变量
export JWT_SECRET="$(openssl rand -base64 48)"

# 3. 启动后端
cd backend
mvn clean install
mvn spring-boot:run

# 4. 启动前端（另开一个终端）
cd frontend
npm install
npm run dev
```

### 访问地址

| 入口 | 地址 |
|------|------|
| 前端 | http://localhost:3000 |
| 后端 API | http://localhost:8080 |
| API 文档 | http://localhost:8080/doc.html |
| MySQL（Docker 映射） | localhost:3307 |

**测试账号**（由建库脚本灌入，密码均为 `123456`）：

| 账号 | 角色 |
|------|------|
| `店主` | 店主，可进 `/admin` 管理后台 |
| `测试用户` | 消费者 |

### 环境变量

| 变量 | 必填 | 说明 |
|------|------|------|
| `JWT_SECRET` | **是** | JWT 签名密钥，**至少 32 字符**。未设置或过短时应用启动即失败（fail fast） |
| `SPRING_DATASOURCE_URL` | 否 | 默认 `jdbc:mysql://localhost:3306/mall_db` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | 否 | 默认 `root` / `root` |
| `SPRING_REDIS_HOST` / `SPRING_REDIS_PORT` | 否 | 默认 `localhost` / `6379` |
| `SERVER_PORT` | 否 | 默认 `8080` |

生成一个合格的密钥：

```bash
openssl rand -base64 48
```

> **为什么必须外置**：密钥硬编码在 `application.yml` 里等于随源码公开分发，任何人都能离线签发一个 `role=1` 的 token 直接拿到店主权限。

---

## 功能模块

### 已实现

- **用户**：注册、登录、JWT 鉴权、登出（token 吊销）、查看/修改资料、改密码
- **商品**：列表（分类/关键词/状态筛选，含子分类递归）、详情、增删改、上下架、定价
- **分类**：树形分类的增删改查
- **购物车**：加购、改数量、删除、清空，含库存校验与规格校验
- **订单**：下单（含库存原子扣减、订单行合并）、取消、支付、发货、确认收货、订单列表/详情
- **收藏**：收藏、取消收藏、收藏列表
- **数据统计**：销售概览、近 7 天趋势、订单状态分布、热销商品排行
- **管理后台**：商品管理、分类管理、订单管理、优惠券管理、数据统计

### 未实现（表结构/前端入口已预留，业务逻辑为空壳）

> 以下功能在早期版本中被误标为「已完成」，实际并未落地。

- **SKU 管理**：`t_sku` 表、`Sku` 实体、`SkuMapper` 已就绪，商品详情 / 购物车 / 下单链路也**已完整支持按 SKU 下单**；但**缺少 SKU 的写入接口**（新增/编辑商品时无法维护规格），目前只能手工执行 SQL 插入。`ProductDTO.skus` 字段被 `ProductController.applyProduct` 忽略。
- **优惠券**：`t_coupon` / `t_user_coupon` 表已建，管理后台可创建优惠券；但**领券、我的优惠券、下单用券核销全部未实现**（`/api/coupon/{id}/receive` 与 `/api/coupon/my` 是空实现，`OrderDTO.couponId` 未被 `OrderServiceImpl` 使用）。
- **物流**：发货时前端会提交物流公司与单号，但订单表无对应字段，后端 `OrderController.ship` 不接收该数据。
- **退款**：`/api/order/{id}/refund` 是无副作用的占位接口。

### 明确不做（设计阶段即排除）

- 支付对接（微信/支付宝）
- 物流接口对接
- 消息通知（短信/邮件）
- 搜索（Elasticsearch）
- CDN 静态资源加速

---

## 安全说明

- 密码使用 BCrypt 存储，登录失败不区分「用户不存在」与「密码错误」。
- **JWT 密钥必须通过 `JWT_SECRET` 环境变量注入**，应用启动时校验长度，不合格直接拒绝启动。
- **登出即失效**：token 携带 `jti`，登出时写入 Redis 黑名单（TTL 为 token 剩余有效期），后续请求经 `AuthInterceptor` 校验拦截。
  - 已知取舍：Redis 不可用时黑名单校验**失败开放**（放行并记录 ERROR 日志），优先保证可用性。若要改为失败关闭，见 `TokenBlacklistService` 类注释。
- 权限模型：拦截器统一鉴权 + `@RequiresAdmin` 注解声明店主专属接口。
- 数据隔离：消费者只能访问自己的订单、购物车、收藏。

---

## 项目结构

```
MallSystem/
├── backend/                        # Spring Boot 后端
│   └── src/main/java/com/mall/
│       ├── auth/                   # JWT、拦截器、AuthUser、@RequiresAdmin
│       ├── config/                 # MyBatis-Plus、WebMvc、字段自动填充
│       ├── controller/             # 控制器层
│       ├── service/ + impl/        # 业务层
│       ├── mapper/                 # 数据访问层
│       ├── entity/ dto/ vo/        # 实体、入参、出参
│       └── exception/              # 错误码与全局异常处理
├── frontend/                       # Vue 3 前端
│   └── src/{api,views,stores,router,components,utils}
├── docker/mysql/init/init.sql      # 唯一的建库脚本
├── docker/scripts/                 # 部署脚本
└── docs/设计文档.md                 # 详细设计文档
```

## 部署

见 [DOCKER_DEPLOY.md](DOCKER_DEPLOY.md)。
