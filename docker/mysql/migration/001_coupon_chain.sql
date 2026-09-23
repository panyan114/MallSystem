-- ============================================================================
-- 001_coupon_chain.sql —— 优惠券全链路所需的表结构变更
--
-- 用途：给【已存在】的数据库补上 init.sql 里新增的列和索引。
--       全新环境不需要执行本文件——init.sql 已经包含这些定义。
--
-- 执行：
--   mysql -h127.0.0.1 -P3307 -uroot -proot mall_db < docker/mysql/migration/001_coupon_chain.sql
--   （端口取 .env 里的 MYSQL_PORT，默认 3307；密码取 MYSQL_ROOT_PASSWORD）
--
-- 幂等：每条语句执行前先查 INFORMATION_SCHEMA，已存在则跳过，可重复执行。
--
-- 为什么不用存储过程 / DELIMITER：
--   DELIMITER 是 mysql 命令行客户端专有指令，不是 SQL 语法。任何走 JDBC
--   （比如 IDE 的数据源、程序化迁移工具）的执行路径都会在它上面报语法错误。
--   这里用 PREPARE/EXECUTE 拼动态 SQL，纯 SQL 语法，两条路径都能跑。
--
-- ⚠ 本文件不在测试覆盖范围内：pom.xml 只把 docker/mysql/init/init.sql 挂到
--   测试 classpath，集成测试消费的是 init.sql。所以执行完请务必手动校验：
--   SHOW CREATE TABLE t_order\G
--   SHOW CREATE TABLE t_user_coupon\G
-- ============================================================================

-- ---- t_order.coupon_id：优惠券ID(t_coupon.id) ----
SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE t_order ADD COLUMN coupon_id BIGINT DEFAULT NULL COMMENT ''优惠券ID(t_coupon.id)''',
        'DO 0')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'coupon_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- t_order.user_coupon_id：取消订单时据此精确退回 ----
SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE t_order ADD COLUMN user_coupon_id BIGINT DEFAULT NULL COMMENT ''用户优惠券ID(t_user_coupon.id)，取消订单时据此退回''',
        'DO 0')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'user_coupon_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- t_order.discount_amount：优惠金额，未使用为 0.00 ----
-- 存量行补 0 之后，再把下面那笔历史订单的 20 元缺口补上（见 init.sql 里的同名注释）。
SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE t_order ADD COLUMN discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT ''优惠金额，未使用为 0.00''',
        'DO 0')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'discount_amount'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- t_user_coupon：每人限领一张 ----
SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE t_user_coupon ADD UNIQUE INDEX uk_user_coupon (user_id, coupon_id)',
        'DO 0')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user_coupon' AND INDEX_NAME = 'uk_user_coupon'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 可选：idx_user_id 已被 uk_user_coupon 的前导列完全覆盖 ----
SET @ddl := (
    SELECT IF(COUNT(*) > 0,
        'ALTER TABLE t_user_coupon DROP INDEX idx_user_id',
        'DO 0')
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user_coupon' AND INDEX_NAME = 'idx_user_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 修正历史订单 202609190002 的 20 元折扣缺口 ----
-- 只在该列刚加出来、且这行还没被修正过时生效，可重复执行。
UPDATE t_order SET discount_amount = 20.00
WHERE order_no = '202609190002' AND total_amount = 159.00 AND real_amount = 139.00
  AND discount_amount = 0.00;

-- ---- 一致性核对（默认只读，不修改数据）----
-- 如果这个查询返回非空，说明 t_coupon.received_count 与 t_user_coupon 的实际行数不一致。
-- 本项目的领券逻辑保证二者同步（同一事务内先占额度再插入），出现偏差通常是历史遗留，
-- 可手工执行下面注释里的语句重建计数。
SELECT c.id, c.name, c.received_count, COUNT(uc.id) AS actual
FROM t_coupon c
LEFT JOIN t_user_coupon uc ON uc.coupon_id = c.id
GROUP BY c.id, c.name, c.received_count
HAVING c.received_count <> COUNT(uc.id);

-- UPDATE t_coupon c SET c.received_count =
--     (SELECT COUNT(*) FROM t_user_coupon uc WHERE uc.coupon_id = c.id);
