# 个人店铺电商系统 - 后端

## 技术栈
- Spring Boot 3.x
- MyBatis-Plus
- MySQL 8.0
- Redis 7
- Java 17

## 快速开始

### 环境要求
- Java 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 7+

### 构建运行
```bash
# 1. 创建数据库（全项目唯一建库脚本，位于仓库根目录的 docker 下）
mysql -u root -p < ../docker/mysql/init/init.sql

# 2. 配置 JWT 密钥（必填，至少 32 字符，未设置会拒绝启动）
export JWT_SECRET="$(openssl rand -base64 48)"

# 3. 导入依赖并启动
mvn clean install
mvn spring-boot:run
```

### 测试
```bash
mvn test
```

### API 文档
启动后访问：http://localhost:8080/doc.html

## 项目结构
```
src/main/java/com/mall/
├── config/          # 配置类
├── controller/      # 控制器层
├── service/         # 业务层
├── mapper/          # 数据访问层
├── entity/          # 实体类
├── dto/             # 数据传输对象
├── vo/              # 视图对象
└── exception/       # 异常处理
```

## API 接口列表
- 用户模块：/api/auth/*
- 商品模块：/api/product/*
- 分类模块：/api/category/*
- 购物车：/api/cart/*
- 订单模块：/api/order/*
- 收藏模块：/api/collect/*
- 优惠券：/api/coupon/*
- 数据统计：/api/stat/*
