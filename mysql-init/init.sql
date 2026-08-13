-- =============================================
-- E-Commerce Demo System - Database Init Script
-- MySQL 8.0+
-- Run: mysql -u root -p < sql/init.sql
-- =============================================

CREATE DATABASE IF NOT EXISTS ecommerce_demo DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ecommerce_demo;

-- -----------------------------------------------
-- 1. user (TRD-01)
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
  `id`               BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT     COMMENT '用户ID',
  `phone`            VARCHAR(11)      NOT NULL                    COMMENT '手机号',
  `username`         VARCHAR(50)      DEFAULT NULL                COMMENT '用户名',
  `password`         VARCHAR(100)     NOT NULL                    COMMENT 'BCrypt密码',
  `password_version` INT              NOT NULL DEFAULT 1          COMMENT '密码版本号',
  `role`             VARCHAR(10)      NOT NULL DEFAULT 'USER'     COMMENT 'USER/ADMIN',
  `status`           TINYINT          NOT NULL DEFAULT 1          COMMENT '1正常 0禁用',
  `created_at`       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- -----------------------------------------------
-- 2. user_address (TRD-01)
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `user_address` (
  `id`             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT UNSIGNED  NOT NULL,
  `receiver_name`  VARCHAR(50)      NOT NULL,
  `receiver_phone` VARCHAR(11)      NOT NULL,
  `province`       VARCHAR(30)      NOT NULL,
  `city`           VARCHAR(30)      NOT NULL,
  `district`       VARCHAR(30)      NOT NULL,
  `detail`         VARCHAR(200)     NOT NULL,
  `is_default`     TINYINT(1)       NOT NULL DEFAULT 0,
  `created_at`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收货地址';

-- -----------------------------------------------
-- 3. category (TRD-02)
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `category` (
  `id`         BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(50)      NOT NULL,
  `parent_id`  BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '0=一级分类',
  `sort`       INT              NOT NULL DEFAULT 0,
  `status`     TINYINT          NOT NULL DEFAULT 1,
  `created_at` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类';

-- -----------------------------------------------
-- 4. product (TRD-02)
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `product` (
  `id`          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `category_id` BIGINT UNSIGNED  NOT NULL,
  `name`        VARCHAR(200)     NOT NULL,
  `description` TEXT             DEFAULT NULL,
  `price`       DECIMAL(10,2)    NOT NULL,
  `status`      VARCHAR(10)      NOT NULL DEFAULT 'OFF_SALE' COMMENT 'ON_SALE/OFF_SALE/DELETED',
  `created_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`),
  KEY `idx_category_status_created` (`category_id`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- -----------------------------------------------
-- 5. product_image (TRD-02)
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `product_image` (
  `id`         BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `product_id` BIGINT UNSIGNED  NOT NULL,
  `url`        VARCHAR(500)     NOT NULL,
  `type`       VARCHAR(10)      NOT NULL DEFAULT 'DETAIL' COMMENT 'MAIN/DETAIL',
  `sort`       INT              NOT NULL DEFAULT 0,
  `created_at` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品图片';

-- -----------------------------------------------
-- 6. inventory (TRD-03)
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `inventory` (
  `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `product_id`      BIGINT UNSIGNED  NOT NULL,
  `total_stock`     INT              NOT NULL DEFAULT 0,
  `available_stock` INT              NOT NULL DEFAULT 0,
  `frozen_stock`    INT              NOT NULL DEFAULT 0 COMMENT '预留字段本期固定0',
  `version`         INT              NOT NULL DEFAULT 0 COMMENT '乐观锁备用',
  `created_at`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品库存';

-- -----------------------------------------------
-- 7. inventory_log (TRD-03)
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `inventory_log` (
  `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `product_id`      BIGINT UNSIGNED  NOT NULL,
  `change_type`     VARCHAR(20)      NOT NULL COMMENT 'RESTOCK/DEDUCT/ROLLBACK',
  `change_quantity` INT              NOT NULL,
  `before_stock`    INT              NOT NULL,
  `after_stock`     INT              NOT NULL,
  `order_no`        VARCHAR(24)      DEFAULT NULL,
  `remark`          VARCHAR(200)     DEFAULT NULL,
  `created_at`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存变更流水';

-- -----------------------------------------------
-- 8. orders (TRD-05)  NOTE: "order" is MySQL reserved word
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `orders` (
  `id`               BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `order_no`         VARCHAR(24)      NOT NULL COMMENT '24位订单号',
  `user_id`          BIGINT UNSIGNED  NOT NULL,
  `total_amount`     DECIMAL(10,2)    NOT NULL,
  `status`           VARCHAR(20)      NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT 'PENDING_PAYMENT/PAID/SHIPPED/COMPLETED/CANCELLED/REFUNDING/REFUNDED',
  `address_snapshot` JSON             NOT NULL COMMENT '地址快照JSON',
  `cancel_reason`    VARCHAR(200)     DEFAULT NULL,
  `paid_at`          DATETIME         DEFAULT NULL,
  `shipped_at`       DATETIME         DEFAULT NULL,
  `completed_at`     DATETIME         DEFAULT NULL,
  `created_at`       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_user_status_created` (`user_id`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

-- -----------------------------------------------
-- 9. order_item (TRD-05)
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `order_item` (
  `id`           BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `order_id`     BIGINT UNSIGNED  NOT NULL,
  `product_id`   BIGINT UNSIGNED  NOT NULL,
  `product_name` VARCHAR(200)     NOT NULL COMMENT '商品名称快照',
  `price`        DECIMAL(10,2)    NOT NULL COMMENT '单价快照',
  `quantity`     INT              NOT NULL,
  `subtotal`     DECIMAL(10,2)    NOT NULL,
  `created_at`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细';

-- -----------------------------------------------
-- 10. order_status_log (TRD-05)
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `order_status_log` (
  `id`          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `order_id`    BIGINT UNSIGNED  NOT NULL,
  `from_status` VARCHAR(20)      DEFAULT NULL,
  `to_status`   VARCHAR(20)      NOT NULL,
  `operator`    VARCHAR(50)      NOT NULL COMMENT 'userId字符串/ADMIN:<id>/system',
  `remark`      VARCHAR(200)     DEFAULT NULL,
  `created_at`  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_id_created` (`order_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态变更日志';

-- -----------------------------------------------
-- 11. refund (TRD-06)
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `refund` (
  `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `refund_no`     VARCHAR(20)      NOT NULL COMMENT 'RF+yyyyMMddHHmmss+4位随机数',
  `order_id`      BIGINT UNSIGNED  NOT NULL,
  `user_id`       BIGINT UNSIGNED  NOT NULL,
  `refund_amount` DECIMAL(10,2)    NOT NULL,
  `status`        VARCHAR(20)      NOT NULL DEFAULT 'APPLYING' COMMENT 'APPLYING/COMPLETED/REJECTED',
  `reason`        VARCHAR(500)     NOT NULL,
  `reject_reason` VARCHAR(200)     DEFAULT NULL,
  `processed_at`  DATETIME         DEFAULT NULL,
  `created_at`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  KEY `idx_order_status` (`order_id`, `status`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单';

-- -----------------------------------------------
-- 12. refund_item (TRD-06)
-- -----------------------------------------------
CREATE TABLE IF NOT EXISTS `refund_item` (
  `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  `refund_id`     BIGINT UNSIGNED  NOT NULL,
  `order_item_id` BIGINT UNSIGNED  NOT NULL,
  `product_id`    BIGINT UNSIGNED  NOT NULL,
  `product_name`  VARCHAR(200)     NOT NULL,
  `price`         DECIMAL(10,2)    NOT NULL,
  `quantity`      INT              NOT NULL,
  `created_at`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_refund_id` (`refund_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款明细';

-- -----------------------------------------------
-- Seed data: admin user (password: Admin1234)
-- BCrypt hash of "Admin1234" with cost=10
-- -----------------------------------------------
INSERT IGNORE INTO `user` (phone, username, password, password_version, role, status)
VALUES ('13800000000', 'admin',
        '$2a$10$IXC3KKcAyCch2cvVM/Kli.RR/FPgthA/totSFr2gNOEy14VCiCSKK',
        1, 'ADMIN', 1);

-- Seed: categories
INSERT IGNORE INTO `category` (id, name, parent_id, sort, status) VALUES
  (1, '电子产品', 0, 0, 1),
  (2, '服装', 0, 1, 1),
  (3, '手机', 1, 0, 1),
  (4, '电脑', 1, 1, 1),
  (5, '男装', 2, 0, 1);
