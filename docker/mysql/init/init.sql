-- ============================================================
-- 个人店铺电商系统 — 建库脚本（全项目唯一权威版本）
--
-- 用法一（Docker）：docker-compose up -d 时由 mysql 容器自动执行。
-- 用法二（本地）：  mysql -u root -p < docker/mysql/init/init.sql
--
-- 注意：不要在其他位置维护第二份副本。历史上本项目存在三份分叉的
-- 建库脚本，其中两份缺少 deleted 列且种子密码 hash 无效，导致店主
-- 账号无法登录。如需变更表结构，只改本文件。
-- ============================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS mall_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE mall_db;

DROP TABLE IF EXISTS t_user;
DROP TABLE IF EXISTS t_category;
DROP TABLE IF EXISTS t_product;
DROP TABLE IF EXISTS t_sku;
DROP TABLE IF EXISTS t_order;
DROP TABLE IF EXISTS t_order_item;
DROP TABLE IF EXISTS t_cart;
DROP TABLE IF EXISTS t_collect;
DROP TABLE IF EXISTS t_coupon;
DROP TABLE IF EXISTS t_user_coupon;

CREATE TABLE t_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '加密密码',
    phone VARCHAR(20) UNIQUE COMMENT '手机号',
    avatar VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    role TINYINT DEFAULT 0 COMMENT '角色：0=消费者，1=店主',
    status TINYINT DEFAULT 1 COMMENT '状态：0=禁用，1=启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0=未删，1=已删',
    INDEX idx_username (username),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE t_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL COMMENT '分类名称',
    parent_id BIGINT DEFAULT 0 COMMENT '父分类ID（0=一级分类）',
    sort INT DEFAULT 0 COMMENT '排序',
    icon VARCHAR(255) DEFAULT NULL COMMENT '图标',
    status TINYINT DEFAULT 1 COMMENT '状态：0=禁用，1=启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类表';

CREATE TABLE t_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL COMMENT '分类ID',
    name VARCHAR(200) NOT NULL COMMENT '商品名称',
    subtitle VARCHAR(500) DEFAULT NULL COMMENT '副标题',
    description TEXT COMMENT '商品详情',
    images VARCHAR(1000) DEFAULT NULL COMMENT '图片JSON数组',
    price DECIMAL(10,2) NOT NULL COMMENT '原价',
    sale_price DECIMAL(10,2) DEFAULT NULL COMMENT '促销价',
    stock INT DEFAULT 0 COMMENT '总库存',
    status TINYINT DEFAULT 0 COMMENT '状态：0=下架，1=上架，2=草稿',
    sort INT DEFAULT 0 COMMENT '排序',
    sales INT DEFAULT 0 COMMENT '销量',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0=未删，1=已删',
    INDEX idx_category_id (category_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

CREATE TABLE t_sku (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT '商品ID',
    spec_key VARCHAR(200) DEFAULT NULL COMMENT '规格键(JSON)',
    spec_desc VARCHAR(200) DEFAULT NULL COMMENT '规格描述',
    price DECIMAL(10,2) NOT NULL COMMENT 'SKU价格',
    stock INT DEFAULT 0 COMMENT 'SKU库存',
    image VARCHAR(255) DEFAULT NULL COMMENT 'SKU图片',
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SKU表';

CREATE TABLE t_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL UNIQUE COMMENT '订单编号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    total_amount DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    real_amount DECIMAL(10,2) NOT NULL COMMENT '实付金额',
    status TINYINT DEFAULT 0 COMMENT '状态：0=待付款，1=待发货，2=待收货，3=已完成，4=已取消',
    address VARCHAR(500) NOT NULL COMMENT '收货地址',
    receiver VARCHAR(50) NOT NULL COMMENT '收货人',
    phone VARCHAR(20) NOT NULL COMMENT '收货电话',
    remark VARCHAR(500) DEFAULT NULL COMMENT '买家备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    pay_time DATETIME DEFAULT NULL COMMENT '支付时间',
    ship_time DATETIME DEFAULT NULL COMMENT '发货时间',
    confirm_time DATETIME DEFAULT NULL COMMENT '确认收货时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0=未删，1=已删',
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_order_no (order_no),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

CREATE TABLE t_order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '订单ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    sku_id BIGINT DEFAULT NULL COMMENT 'SKU ID',
    product_name VARCHAR(200) NOT NULL COMMENT '商品名称',
    product_image VARCHAR(255) DEFAULT NULL COMMENT '商品图片',
    sku_desc VARCHAR(200) DEFAULT NULL COMMENT '规格描述',
    price DECIMAL(10,2) NOT NULL COMMENT '单价',
    quantity INT NOT NULL COMMENT '数量',
    total_price DECIMAL(10,2) NOT NULL COMMENT '小计',
    INDEX idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

CREATE TABLE t_cart (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    sku_id BIGINT DEFAULT NULL COMMENT 'SKU ID',
    quantity INT DEFAULT 1 COMMENT '数量',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_user_product (user_id, product_id, sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

CREATE TABLE t_collect (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE INDEX uk_user_product (user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';

CREATE TABLE t_coupon (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL COMMENT '优惠券名称',
    type TINYINT DEFAULT 0 COMMENT '类型：0=满减，1=折扣',
    min_amount DECIMAL(10,2) DEFAULT NULL COMMENT '最小使用金额',
    discount_value DECIMAL(10,2) DEFAULT NULL COMMENT '优惠金额或折扣比例',
    total_count INT DEFAULT 0 COMMENT '总发放数量',
    received_count INT DEFAULT 0 COMMENT '已领取数量',
    status TINYINT DEFAULT 1 COMMENT '状态：0=禁用，1=启用',
    start_time DATETIME DEFAULT NULL COMMENT '开始时间',
    end_time DATETIME DEFAULT NULL COMMENT '结束时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券表';

CREATE TABLE t_user_coupon (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    coupon_id BIGINT NOT NULL COMMENT '优惠券ID',
    status TINYINT DEFAULT 0 COMMENT '状态：0=未使用，1=已使用，2=已过期',
    receive_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
    use_time DATETIME DEFAULT NULL COMMENT '使用时间',
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券表';

-- ============ 初始化测试数据 ============

-- 用户：店主(role=1) / 测试用户(role=0)，密码均为 123456
INSERT INTO t_user (username, password, phone, role) VALUES ('店主', '$2b$10$8lRFD1/SB0O6zIIjQUpxKuBLV39kJLXJ932y4WpUTJWKZP3PcLVWC', '13800000001', 1);
INSERT INTO t_user (username, password, phone, role) VALUES ('测试用户', '$2b$10$8lRFD1/SB0O6zIIjQUpxKuBLV39kJLXJ932y4WpUTJWKZP3PcLVWC', '13800000002', 0);

-- 分类
INSERT INTO t_category (name, parent_id, sort) VALUES ('电子产品', 0, 1);
INSERT INTO t_category (name, parent_id, sort) VALUES ('服装鞋包', 0, 2);
INSERT INTO t_category (name, parent_id, sort) VALUES ('食品饮料', 0, 3);
INSERT INTO t_category (name, parent_id, sort) VALUES ('手机配件', 1, 1);
INSERT INTO t_category (name, parent_id, sort) VALUES ('电脑配件', 1, 2);

-- 商品
INSERT INTO t_product (category_id, name, subtitle, description, images, price, sale_price, stock, status, sort, sales) VALUES
(4, 'iPhone 15 手机壳', '轻薄防摔，透明不黄', '高品质 TPU 材质，全包边设计。', '["/images/p1.svg"]', 39.00, 29.00, 500, 1, 1, 120),
(4, 'Type-C 快充数据线', '支持 100W 快充', '尼龙编织，耐用不缠绕。', '["/images/p2.svg"]', 29.00, 19.90, 800, 1, 2, 300),
(5, '无线蓝牙键盘', '轻薄便携，静音办公', '兼容 Windows/Mac，续航 3 个月。', '["/images/p3.svg"]', 199.00, 159.00, 200, 1, 3, 88),
(5, 'USB-C 扩展坞', '7 合 1 多接口', 'HDMI 4K、PD 100W、USB3.0。', '["/images/p4.svg"]', 259.00, 219.00, 150, 1, 4, 45),
(2, '纯棉短袖 T 恤', '100% 新疆棉，透气舒适', '男女同款，多色可选。', '["/images/p5.svg"]', 99.00, 59.00, 1000, 1, 5, 500),
(3, '手冲咖啡豆', '精选阿拉比卡', '中度烘焙，风味均衡。', '["/images/p6.svg"]', 128.00, 98.00, 300, 1, 6, 66);

-- SKU
INSERT INTO t_sku (product_id, spec_key, spec_desc, price, stock) VALUES
(1, '{"color":"透明"}', '透明', 29.00, 500),
(2, '{"length":"1米"}', '1米', 19.90, 400),
(2, '{"length":"2米"}', '2米', 24.90, 400),
(3, '{"color":"黑色"}', '黑色', 159.00, 200),
(4, '{"color":"银色"}', '银色', 219.00, 150),
(5, '{"size":"M","color":"白色"}', 'M/白色', 59.00, 500),
(5, '{"size":"L","color":"白色"}', 'L/白色', 59.00, 500),
(6, '{"weight":"250g"}', '250g', 98.00, 300);

-- 优惠券
INSERT INTO t_coupon (name, type, min_amount, discount_value, total_count, received_count, status, start_time, end_time) VALUES
('新人立减券', 0, 50.00, 10.00, 1000, 0, 1, '2026-01-01 00:00:00', '2027-12-31 23:59:59'),
('满200减30', 0, 200.00, 30.00, 500, 0, 1, '2026-01-01 00:00:00', '2027-12-31 23:59:59'),
('全场9折券', 1, 0.00, 0.90, 300, 0, 1, '2026-01-01 00:00:00', '2027-12-31 23:59:59');

-- 订单（user_id=2 为测试用户）
INSERT INTO t_order (order_no, user_id, total_amount, real_amount, status, address, receiver, phone, create_time, pay_time) VALUES
('202609180001', 2, 77.90, 77.90, 1, '北京市朝阳区建国路88号', '张三', '13800000002', '2026-09-18 10:30:00', '2026-09-18 10:31:00'),
('202609190002', 2, 159.00, 139.00, 2, '上海市浦东新区世纪大道100号', '李四', '13800000002', '2026-09-19 14:20:00', '2026-09-19 14:21:00'),
('202609200003', 2, 219.00, 219.00, 3, '广州市天河区体育西路50号', '王五', '13800000002', '2026-09-20 09:15:00', '2026-09-20 09:16:00');

INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, sku_desc, price, quantity, total_price) VALUES
(1, 1, 1, 'iPhone 15 手机壳', '', '透明', 29.00, 2, 58.00),
(1, 2, 2, 'Type-C 快充数据线', '', '1米', 19.90, 1, 19.90),
(2, 3, 4, '无线蓝牙键盘', '', '黑色', 159.00, 1, 159.00),
(3, 4, 5, 'USB-C 扩展坞', '', '银色', 219.00, 1, 219.00);

-- 收藏
INSERT INTO t_collect (user_id, product_id) VALUES (2, 1), (2, 3), (2, 5);

SELECT '数据库初始化完成';
