-- ============================================================
-- mall 业务库（第三版：28 张表，覆盖电商核心链路 + 经典面试场景）
-- 命名规则：表名前缀 = 数据语义域（表装哪一域的数据），多数域与模块同名，见表名即知所属服务
--   member_*          → 会员域（mall-member）：member / member_address / member_point_log / member_favorite
--   admin_*           → 后台管理域（管理员账号 + RBAC 五表）：admin_user / admin_role / admin_menu / admin_user_role / admin_role_menu，由 mall-auth 认证权限服务持有（认证是动作无表，账号权限数据按语义域命名）
--   product_*         → 商品域（mall-product）：category / brand / spu / sku / stock_log / comment / supplier / purchase / purchase_item（后三张为进销存域，与库存同域）
--   orders/order_item → mall-order 订单服务（order 是 MySQL 保留字，订单表命名 orders；含 order_status_log 状态流水）
--   payment_*         → mall-payment 支付服务（payment / payment_refund）
--   coupon_*          → mall-coupon 优惠券服务（coupon / coupon_user）
--   seckill_*         → mall-seckill 秒杀服务（seckill_session / seckill_product）
--   tx_message        → 公共域：本地消息表（mall-common 事务消息组件，非业务模块专属）
-- 无表模块：
--   mall-cart   购物车：纯 Redis（hash 结构，key=cart:{memberId}）
--   mall-search 搜索：ES 索引（spu/sku 文档），MySQL 不建表
--   mall-gateway / mall-admin / mall-portal：聚合层，不建表
-- 用法（PowerShell）：Get-Content .\sql\mall.sql -Raw -Encoding UTF8 | docker exec -i mall-mysql mysql -uroot -p123456
-- 用法（bash）：docker exec -i mall-mysql mysql -uroot -p123456 < sql/mall.sql
-- 说明：root/123456 为作者本地账密，请按各自环境修改
-- ============================================================

CREATE DATABASE IF NOT EXISTS `mall` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `mall`;

-- ------------------------------------------------------------
-- 会员域（mall-member）
-- ------------------------------------------------------------
CREATE TABLE `member` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会员ID',
  `username`    VARCHAR(64)  NOT NULL COMMENT '登录账号',
  `password`    VARCHAR(128) NOT NULL COMMENT '密码（BCrypt 加密）',
  `nickname`    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
  `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号（唯一键；无手机号统一存 NULL，勿存空串）',
  `email`       VARCHAR(64)  DEFAULT NULL COMMENT '邮箱',
  `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
  `gender`      TINYINT      NOT NULL DEFAULT 0 COMMENT '性别：0未知 1男 2女',
  `birthday`    DATE         DEFAULT NULL COMMENT '生日',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
  `level`       TINYINT      NOT NULL DEFAULT 0 COMMENT '会员等级：0普通 1白银 2黄金 3钻石（权益划分，非 RBAC）',
  `points`      INT          NOT NULL DEFAULT 0 COMMENT '积分余额（变动流水见 member_point_log）',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员表';

CREATE TABLE `member_address` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '地址ID',
  `member_id`      BIGINT       NOT NULL COMMENT '会员ID',
  `receiver_name`  VARCHAR(32)  NOT NULL COMMENT '收货人姓名',
  `receiver_phone` VARCHAR(20)  NOT NULL COMMENT '收货人电话',
  `province`       VARCHAR(32)  DEFAULT NULL COMMENT '省',
  `city`           VARCHAR(32)  DEFAULT NULL COMMENT '市',
  `district`       VARCHAR(32)  DEFAULT NULL COMMENT '区/县',
  `detail_address` VARCHAR(255) NOT NULL COMMENT '详细地址',
  `default_flag`   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认地址：1是 0否',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_member_id` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员收货地址表';

-- ------------------------------------------------------------
-- 商品域（mall-product）
-- ------------------------------------------------------------
CREATE TABLE `product_category` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父分类ID，0为顶级',
  `name`        VARCHAR(64)  NOT NULL COMMENT '分类名称',
  `level`       TINYINT      NOT NULL DEFAULT 1 COMMENT '层级：1一级 2二级 3三级',
  `icon`        VARCHAR(255) DEFAULT NULL COMMENT '分类图标',
  `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序（越小越靠前）',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

CREATE TABLE `product_brand` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '品牌ID',
  `name`        VARCHAR(64)  NOT NULL COMMENT '品牌名称',
  `logo`        VARCHAR(255) DEFAULT NULL COMMENT '品牌LOGO',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '品牌简介',
  `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌表';

CREATE TABLE `product_spu` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'SPU ID',
  `spu_code`    VARCHAR(64)  NOT NULL COMMENT 'SPU 编码（商品编码，与 SKU 编码对称）',
  `category_id` BIGINT       NOT NULL COMMENT '分类ID',
  `brand_id`    BIGINT       DEFAULT NULL COMMENT '品牌ID',
  `name`        VARCHAR(128) NOT NULL COMMENT '商品名称',
  `subtitle`    VARCHAR(255) DEFAULT NULL COMMENT '副标题/卖点',
  `main_pic`    VARCHAR(255) DEFAULT NULL COMMENT '主图',
  `pics`        JSON         DEFAULT NULL COMMENT '轮播图集合（JSON数组）',
  `unit`        VARCHAR(16)  DEFAULT NULL COMMENT '计量单位',
  `detail`      TEXT         COMMENT '商品详情（富文本HTML）',
  `sales`       INT          NOT NULL DEFAULT 0 COMMENT '累计销量',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0下架 1上架',
  `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_spu_code` (`spu_code`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_brand_id` (`brand_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SPU表';

CREATE TABLE `product_sku` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
  `spu_id`         BIGINT        NOT NULL COMMENT 'SPU ID',
  `sku_code`       VARCHAR(64)   NOT NULL COMMENT 'SKU 编码',
  `spec`           JSON          DEFAULT NULL COMMENT '规格属性（JSON，如 {"颜色":"黑","内存":"256G"}）',
  `price`          DECIMAL(10,2) NOT NULL COMMENT '售价',
  `original_price` DECIMAL(10,2) DEFAULT NULL COMMENT '原价/划线价',
  `stock`          INT           NOT NULL DEFAULT 0 COMMENT '库存',
  `low_stock`      INT           DEFAULT NULL COMMENT '库存预警阈值（低于此值触发预警；NULL 取全局默认阈值）',
  `pic`            VARCHAR(255)  DEFAULT NULL COMMENT 'SKU 图片',
  `sale_count`     INT           NOT NULL DEFAULT 0 COMMENT '销量',
  `version`        INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（防超卖）',
  `status`         TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_code` (`sku_code`),
  KEY `idx_spu_id` (`spu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU表（库存随下单扣减，秒杀场景由 Redis 预扣）';

-- ------------------------------------------------------------
-- 订单域（mall-order）
-- ------------------------------------------------------------
CREATE TABLE `orders` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_sn`        VARCHAR(64)   NOT NULL COMMENT '订单号',
  `request_id`      VARCHAR(64)   NOT NULL COMMENT '幂等键（客户端生成，防重复提交，下单必传）',
  `member_id`       BIGINT        NOT NULL COMMENT '会员ID',
  `order_type`      TINYINT       NOT NULL DEFAULT 1 COMMENT '订单类型：1普通 2秒杀',
  `member_name`     VARCHAR(64)   DEFAULT NULL COMMENT '会员名（下单快照）',
  `total_amount`    DECIMAL(10,2) NOT NULL COMMENT '商品总额',
  `freight_amount`  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '运费',
  `coupon_amount`   DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠券抵扣',
  `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '其他优惠（秒杀/满减等）',
  `pay_amount`      DECIMAL(10,2) NOT NULL COMMENT '应付金额（实付）',
  `pay_type`        TINYINT       DEFAULT NULL COMMENT '支付方式：1支付宝 2微信',
  `status`          TINYINT       NOT NULL DEFAULT 0 COMMENT '状态：0待付款 1待发货 2待收货 3已完成 4已取消 5已退款（5=整单全额退款）',
  `receiver_name`   VARCHAR(32)   DEFAULT NULL COMMENT '收货人（下单快照）',
  `receiver_phone`  VARCHAR(20)   DEFAULT NULL COMMENT '收货电话',
  `receiver_address` VARCHAR(255) DEFAULT NULL COMMENT '收货地址（省市区+详细，拼接快照）',
  `remark`          VARCHAR(255)  DEFAULT NULL COMMENT '买家备注',
  `pay_time`        DATETIME      DEFAULT NULL COMMENT '支付时间',
  `delivery_company` VARCHAR(32)  DEFAULT NULL COMMENT '发货物流公司（后台发货填写）',
  `delivery_sn`     VARCHAR(64)   DEFAULT NULL COMMENT '发货物流单号',
  `delivery_time`   DATETIME      DEFAULT NULL COMMENT '发货时间',
  `receive_time`    DATETIME      DEFAULT NULL COMMENT '收货时间',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_sn` (`order_sn`),
  UNIQUE KEY `uk_request_id` (`request_id`),
  KEY `idx_member_id` (`member_id`),
  KEY `idx_status_create_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

CREATE TABLE `order_item` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单项ID',
  `order_id`     BIGINT        NOT NULL COMMENT '订单ID',
  `order_sn`     VARCHAR(64)   NOT NULL COMMENT '订单号（冗余，便于查询）',
  `spu_id`       BIGINT        NOT NULL COMMENT 'SPU ID',
  `spu_name`     VARCHAR(128)  NOT NULL COMMENT '商品名称（下单快照）',
  `sku_id`       BIGINT        NOT NULL COMMENT 'SKU ID',
  `sku_code`     VARCHAR(64)   DEFAULT NULL COMMENT 'SKU 编码',
  `spec`         VARCHAR(255)  DEFAULT NULL COMMENT '规格描述（下单快照）',
  `pic`          VARCHAR(255)  DEFAULT NULL COMMENT '商品图片（下单快照）',
  `price`        DECIMAL(10,2) NOT NULL COMMENT '成交单价（下单快照）',
  `quantity`     INT           NOT NULL DEFAULT 1 COMMENT '购买数量',
  `subtotal`     DECIMAL(10,2) NOT NULL COMMENT '小计（price*quantity）',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_sn` (`order_sn`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单项表';

-- ------------------------------------------------------------
-- 支付域（mall-payment）
-- ------------------------------------------------------------
CREATE TABLE `payment` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '支付流水ID',
  `payment_sn`  VARCHAR(64)   NOT NULL COMMENT '支付流水号',
  `order_id`    BIGINT        NOT NULL COMMENT '订单ID',
  `order_sn`    VARCHAR(64)   NOT NULL COMMENT '订单号',
  `member_id`   BIGINT        NOT NULL COMMENT '会员ID',
  `pay_amount`  DECIMAL(10,2) NOT NULL COMMENT '支付金额',
  `pay_type`    TINYINT       NOT NULL COMMENT '支付方式：1支付宝 2微信',
  `trade_no`    VARCHAR(64)   DEFAULT NULL COMMENT '第三方交易流水号',
  `status`      TINYINT       NOT NULL DEFAULT 0 COMMENT '状态：0待支付 1支付成功 2支付失败 3已退款（3=整单全额）',
  `notify_time` DATETIME      DEFAULT NULL COMMENT '回调通知时间',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_sn` (`payment_sn`),
  UNIQUE KEY `uk_trade_no` (`trade_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_member_id` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付流水表';

-- ------------------------------------------------------------
-- 营销域：优惠券（mall-coupon）
-- ------------------------------------------------------------
CREATE TABLE `coupon` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '优惠券ID',
  `name`            VARCHAR(64)   NOT NULL COMMENT '优惠券名称',
  `type`            TINYINT       NOT NULL COMMENT '类型：1满减券 2折扣券',
  `amount`          DECIMAL(10,2) NOT NULL COMMENT '抵扣金额（满减券）/ 折扣率（折扣券，如8.5折存0.85）',
  `threshold`       DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '使用门槛（满X元可用，0为无门槛）',
  `total_count`     INT           NOT NULL COMMENT '发行总量',
  `per_limit`       INT           NOT NULL DEFAULT 1 COMMENT '每人限领数量',
  `received_count`  INT           NOT NULL DEFAULT 0 COMMENT '已领取数量',
  `use_start_time`  DATETIME      NOT NULL COMMENT '生效时间',
  `use_end_time`    DATETIME      NOT NULL COMMENT '失效时间',
  `status`          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1进行中 0已结束',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券表';

CREATE TABLE `coupon_user` (
  `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '领取记录ID',
  `coupon_id`    BIGINT   NOT NULL COMMENT '优惠券ID',
  `member_id`    BIGINT   NOT NULL COMMENT '会员ID',
  `order_id`     BIGINT   DEFAULT NULL COMMENT '核销订单ID',
  `status`       TINYINT  NOT NULL DEFAULT 0 COMMENT '状态：0未使用 1已锁定（下单占用） 2已使用 3已过期（取消/超时关单由1回退到0，退款退回由2回退到0；退回时校验券有效期，已过期则置3）',
  `receive_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  `lock_time`    DATETIME DEFAULT NULL COMMENT '锁定时间（下单占用）',
  `use_time`     DATETIME DEFAULT NULL COMMENT '使用时间',
  PRIMARY KEY (`id`),
  KEY `idx_member_id_status` (`member_id`, `status`),
  KEY `idx_coupon_id` (`coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券领取/使用记录表';

-- ------------------------------------------------------------
-- 营销域：秒杀（mall-seckill）
-- 说明：场次与商品配置落库；秒杀库存热点在 Redis 预扣，成交后异步落库扣减 product_sku.stock
-- ------------------------------------------------------------
CREATE TABLE `seckill_session` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '场次ID',
  `name`        VARCHAR(64) NOT NULL COMMENT '场次名称（如：8点场）',
  `start_time`  DATETIME    NOT NULL COMMENT '开始时间',
  `end_time`    DATETIME    NOT NULL COMMENT '结束时间',
  `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀场次表';

CREATE TABLE `seckill_product` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '秒杀商品ID',
  `session_id`     BIGINT        NOT NULL COMMENT '场次ID',
  `spu_id`         BIGINT        NOT NULL COMMENT 'SPU ID',
  `sku_id`         BIGINT        NOT NULL COMMENT 'SKU ID',
  `seckill_price`  DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
  `seckill_stock`  INT           NOT NULL COMMENT '秒杀库存（预热时同步到 Redis）',
  `limit_per_user` INT           NOT NULL DEFAULT 1 COMMENT '每人限购数量',
  `status`         TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_sku` (`session_id`, `sku_id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品表';

-- ------------------------------------------------------------
-- 商品域补充（mall-product）
-- ------------------------------------------------------------
CREATE TABLE `product_stock_log` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `sku_id`       BIGINT      NOT NULL COMMENT 'SKU ID',
  `biz_sn`      VARCHAR(64) DEFAULT NULL COMMENT '业务单号（订单号 / 采购单号 / 退款单号）',
  `change_type`  TINYINT     NOT NULL COMMENT '变动类型：1下单扣减 2取消回补 3退款回补 4秒杀扣减 5采购入库 6退货入库 7盘点调整 8人工调整 9秒杀回补',
  `change_count` INT         NOT NULL COMMENT '变动数量（正数增加、负数减少：入库为正、扣减为负，业务方向看 change_type）',
  `stock_before` INT         NOT NULL COMMENT '变动前库存',
  `stock_after`  INT         NOT NULL COMMENT '变动后库存',
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_sku_id` (`sku_id`),
  KEY `idx_biz_sn` (`biz_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水表（扣减/回补/采购入库/退货入库/盘点对账，防超卖审计）';

CREATE TABLE `product_comment` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `order_item_id` BIGINT       NOT NULL COMMENT '订单项ID（唯一键，防重复评价）',
  `order_sn`      VARCHAR(64)  NOT NULL COMMENT '订单号',
  `member_id`     BIGINT       NOT NULL COMMENT '会员ID',
  `spu_id`        BIGINT       NOT NULL COMMENT 'SPU ID',
  `sku_id`        BIGINT       NOT NULL COMMENT 'SKU ID',
  `rating`        TINYINT      NOT NULL DEFAULT 5 COMMENT '评分：1~5',
  `content`       VARCHAR(500) DEFAULT NULL COMMENT '评价内容',
  `pics`          JSON         DEFAULT NULL COMMENT '晒图（JSON数组）',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常 0隐藏',
  `reply`         VARCHAR(500) DEFAULT NULL COMMENT '商家回复内容（后台评价管理回复）',
  `reply_time`    DATETIME     DEFAULT NULL COMMENT '商家回复时间',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_item_id` (`order_item_id`),
  KEY `idx_spu_id` (`spu_id`),
  KEY `idx_member_id` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价表';

-- ------------------------------------------------------------
-- 订单域补充（mall-order）
-- ------------------------------------------------------------
CREATE TABLE `order_status_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `order_id`    BIGINT       NOT NULL COMMENT '订单ID',
  `order_sn`    VARCHAR(64)  NOT NULL COMMENT '订单号',
  `from_status` TINYINT      DEFAULT NULL COMMENT '变更前状态',
  `to_status`   TINYINT      NOT NULL COMMENT '变更后状态',
  `operator`    VARCHAR(64)  DEFAULT NULL COMMENT '操作者：买家/系统/管理员',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态流转日志表（状态机审计，防乱改状态）';

-- ------------------------------------------------------------
-- 支付域补充：退款（mall-payment，表名 payment_refund 取支付域前缀）
-- ------------------------------------------------------------
CREATE TABLE `payment_refund` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '退款单ID',
  `refund_sn`     VARCHAR(64)   NOT NULL COMMENT '退款单号',
  `order_id`      BIGINT        NOT NULL COMMENT '订单ID',
  `order_sn`      VARCHAR(64)   NOT NULL COMMENT '订单号',
  `payment_sn`    VARCHAR(64)   DEFAULT NULL COMMENT '支付流水号',
  `member_id`     BIGINT        NOT NULL COMMENT '会员ID',
  `refund_amount` DECIMAL(10,2) NOT NULL COMMENT '退款金额（整单退款，等于订单实付）',
  `reason`        VARCHAR(255)  DEFAULT NULL COMMENT '退款原因',
  `refund_type`   TINYINT       NOT NULL DEFAULT 1 COMMENT '退款类型：1仅退款 2退货退款（整单退款）',
  `return_company` VARCHAR(32)  DEFAULT NULL COMMENT '退货物流公司（退货退款用）',
  `return_sn`     VARCHAR(64)   DEFAULT NULL COMMENT '退货物流单号（退货退款用）',
  `status`        TINYINT       NOT NULL DEFAULT 0 COMMENT '状态：0申请中 1审核通过 2退货中 3退款中 4已退款 5已拒绝（仅退款跳过 2；第三方退款失败停留 3，重试/人工介入）',
  `audit_by`      VARCHAR(64)   DEFAULT NULL COMMENT '审核人（后台审核退款申请）',
  `audit_time`    DATETIME      DEFAULT NULL COMMENT '审核时间',
  `apply_time`    DATETIME      DEFAULT NULL COMMENT '申请时间',
  `refund_time`   DATETIME      DEFAULT NULL COMMENT '退款到账时间',
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_sn` (`refund_sn`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_member_id` (`member_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单表（整单退款状态机；退款成功后回补库存、退回优惠券——退券校验有效期，过期置已过期）';

-- ------------------------------------------------------------
-- 会员域补充（mall-member）
-- ------------------------------------------------------------
CREATE TABLE `member_point_log` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '积分流水ID',
  `member_id`    BIGINT      NOT NULL COMMENT '会员ID',
  `change_type`  TINYINT     NOT NULL COMMENT '变动类型：1注册赠送 2购物返积分 3兑换消耗 4退款扣回',
  `change_point` INT         NOT NULL COMMENT '变动积分（正数增加，负数扣减）',
  `point_after`  INT         NOT NULL COMMENT '变动后积分余额',
  `order_sn`     VARCHAR(64) DEFAULT NULL COMMENT '关联订单号',
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_member_id` (`member_id`),
  KEY `idx_order_sn` (`order_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水表';

CREATE TABLE `member_favorite` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `member_id`   BIGINT   NOT NULL COMMENT '会员ID',
  `spu_id`      BIGINT   NOT NULL COMMENT 'SPU ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_spu` (`member_id`,`spu_id`),
  KEY `idx_spu_id` (`spu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员收藏表';

-- ------------------------------------------------------------
-- 公共域：本地消息表（mall-common 事务消息组件使用，非业务模块专属）
-- ------------------------------------------------------------
CREATE TABLE `tx_message` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `biz_id`       VARCHAR(64) NOT NULL COMMENT '业务唯一标识（幂等键）',
  `topic`        VARCHAR(64) NOT NULL COMMENT 'MQ Topic',
  `tag`          VARCHAR(64) DEFAULT NULL COMMENT 'MQ Tag',
  `message_body` TEXT        NOT NULL COMMENT '消息体（JSON）',
  `status`       TINYINT     NOT NULL DEFAULT 0 COMMENT '状态：0待发送 1已发送 2发送失败',
  `retry_count`  INT         NOT NULL DEFAULT 0 COMMENT '重试次数',
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_id` (`biz_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表（事务消息/最终一致性）';

-- ------------------------------------------------------------
-- 后台管理域：管理员账号体系（归属 mall-auth 认证权限服务）——RBAC 五表
-- 设计说明：买家用 member（状态 + 等级权益模型），后台运营用 admin_user（RBAC 权限模型），
-- 两套账号分离（人员属性/密码策略/登录入口不同）；角色权限不公用，语义不同不强行合并
-- ------------------------------------------------------------
CREATE TABLE `admin_user` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '管理员ID',
  `username`        VARCHAR(64)  NOT NULL COMMENT '登录账号',
  `password`        VARCHAR(128) NOT NULL COMMENT '密码（BCrypt 加密）',
  `nickname`        VARCHAR(64)  DEFAULT NULL COMMENT '姓名/昵称',
  `avatar`          VARCHAR(255) DEFAULT NULL COMMENT '头像',
  `phone`           VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  `email`           VARCHAR(64)  DEFAULT NULL COMMENT '邮箱',
  `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `last_login_time` DATETIME     DEFAULT NULL COMMENT '最后登录时间',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台管理员表（后台管理域，与买家 member 分离的运营账号体系）';

CREATE TABLE `admin_role` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `name`        VARCHAR(64)  NOT NULL COMMENT '角色名称（如：超级管理员）',
  `code`        VARCHAR(64)  NOT NULL COMMENT '角色编码（如 SUPER_ADMIN；权限校验用 admin_menu.perms 权限标识，不用角色编码）',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台角色表（RBAC）';

CREATE TABLE `admin_menu` (
  `id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜单/权限ID',
  `parent_id` BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID，0为顶级',
  `name`      VARCHAR(64)  NOT NULL COMMENT '菜单/权限名称',
  `type`      TINYINT      NOT NULL DEFAULT 1 COMMENT '类型：1目录 2菜单 3按钮',
  `path`      VARCHAR(128) DEFAULT NULL COMMENT '前端路由路径',
  `perms`     VARCHAR(128) DEFAULT NULL COMMENT '权限标识（如 product:add，@PreAuthorize 校验用）',
  `icon`      VARCHAR(64)  DEFAULT NULL COMMENT '图标',
  `sort`      INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `status`    TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `create_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台菜单/权限表（RBAC 权限树）';

CREATE TABLE `admin_user_role` (
  `id`      BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `user_id` BIGINT NOT NULL COMMENT '管理员ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员-角色关联表';

CREATE TABLE `admin_role_menu` (
  `id`      BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `menu_id` BIGINT NOT NULL COMMENT '菜单/权限ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-菜单权限关联表';

-- ------------------------------------------------------------
-- 初始化数据（首次建库自动生效）：前后台统一默认账号 admin / admin123（方便记忆）
-- 权限标识与后端 @PreAuthorize("@ss.hasPerm('xxx')") 一一对应
-- ------------------------------------------------------------
INSERT INTO `admin_user` (`username`, `password`, `nickname`, `status`) VALUES
('admin', '$2a$10$bV250hozGfx2/QYobhIUZ.fXi34C7AcIIvEkEE3G/3P106FO14PPm', '超级管理员', 1);

-- 前台商城演示买家（密码与后台超管同一个 BCrypt 密文，即 admin123）
INSERT INTO `member` (`username`, `password`, `nickname`, `phone`, `status`) VALUES
('admin', '$2a$10$bV250hozGfx2/QYobhIUZ.fXi34C7AcIIvEkEE3G/3P106FO14PPm', '演示买家', '13800000000', 1);

INSERT INTO `admin_role` (`name`, `code`, `description`, `status`) VALUES
('超级管理员', 'SUPER_ADMIN', '拥有全部权限（登录后权限标识为 *）', 1);

INSERT INTO `admin_user_role` (`user_id`, `role_id`) VALUES (1, 1);

INSERT INTO `admin_menu` (`parent_id`, `name`, `type`, `path`, `perms`, `icon`, `sort`, `status`) VALUES
(0, '系统管理', 1, '/system', NULL, 'setting', 1, 1),
(1, '用户管理', 2, '/system/user', 'system:user:list', 'user', 1, 1),
(1, '角色管理', 2, '/system/role', 'system:role:list', 'role', 2, 1),
(1, '菜单管理', 2, '/system/menu', 'system:menu:list', 'menu', 3, 1),
(2, '用户查询', 3, NULL, 'system:user:list', NULL, 1, 1),
(2, '用户新增', 3, NULL, 'system:user:add', NULL, 2, 1),
(2, '用户修改', 3, NULL, 'system:user:update', NULL, 3, 1),
(2, '用户删除', 3, NULL, 'system:user:delete', NULL, 4, 1),
(2, '重置密码', 3, NULL, 'system:user:resetPwd', NULL, 5, 1),
(2, '分配角色', 3, NULL, 'system:user:assign', NULL, 6, 1),
(3, '角色查询', 3, NULL, 'system:role:list', NULL, 1, 1),
(3, '角色新增', 3, NULL, 'system:role:add', NULL, 2, 1),
(3, '角色修改', 3, NULL, 'system:role:update', NULL, 3, 1),
(3, '角色删除', 3, NULL, 'system:role:delete', NULL, 4, 1),
(3, '分配菜单', 3, NULL, 'system:role:assign', NULL, 5, 1),
(4, '菜单查询', 3, NULL, 'system:menu:list', NULL, 1, 1),
(4, '菜单新增', 3, NULL, 'system:menu:add', NULL, 2, 1),
(4, '菜单修改', 3, NULL, 'system:menu:update', NULL, 3, 1),
(4, '菜单删除', 3, NULL, 'system:menu:delete', NULL, 4, 1);

-- 阶段 3：商品管理菜单（目录+6 菜单+按钮权限；parent_id 沿用顺序插入的自增 ID，勿调整顺序）
INSERT INTO `admin_menu` (`parent_id`, `name`, `type`, `path`, `perms`, `icon`, `sort`, `status`) VALUES
(0, '商品管理', 1, '/product', NULL, 'product', 2, 1),
(20, '分类管理', 2, '/category', 'product:category:list', NULL, 1, 1),
(20, '品牌管理', 2, '/brand', 'product:brand:list', NULL, 2, 1),
(20, '商品管理', 2, '/product', 'product:spu:list', NULL, 3, 1),
(20, '供应商管理', 2, '/supplier', 'product:supplier:list', NULL, 4, 1),
(20, '采购管理', 2, '/purchase', 'product:purchase:list', NULL, 5, 1),
(20, '库存管理', 2, '/stock', 'product:stock:list', NULL, 6, 1),
(21, '分类新增', 3, NULL, 'product:category:add', NULL, 1, 1),
(21, '分类修改', 3, NULL, 'product:category:update', NULL, 2, 1),
(21, '分类删除', 3, NULL, 'product:category:delete', NULL, 3, 1),
(21, '分类启停', 3, NULL, 'product:category:status', NULL, 4, 1),
(22, '品牌新增', 3, NULL, 'product:brand:add', NULL, 1, 1),
(22, '品牌修改', 3, NULL, 'product:brand:update', NULL, 2, 1),
(22, '品牌删除', 3, NULL, 'product:brand:delete', NULL, 3, 1),
(22, '品牌启停', 3, NULL, 'product:brand:status', NULL, 4, 1),
(23, '商品新增', 3, NULL, 'product:spu:add', NULL, 1, 1),
(23, '商品修改', 3, NULL, 'product:spu:update', NULL, 2, 1),
(23, '商品删除', 3, NULL, 'product:spu:delete', NULL, 3, 1),
(23, '商品上下架', 3, NULL, 'product:spu:status', NULL, 4, 1),
(23, '缓存预热', 3, NULL, 'product:spu:preload', NULL, 5, 1),
(24, '供应商新增', 3, NULL, 'product:supplier:add', NULL, 1, 1),
(24, '供应商修改', 3, NULL, 'product:supplier:update', NULL, 2, 1),
(24, '供应商删除', 3, NULL, 'product:supplier:delete', NULL, 3, 1),
(24, '供应商启停', 3, NULL, 'product:supplier:status', NULL, 4, 1),
(25, '采购单创建', 3, NULL, 'product:purchase:add', NULL, 1, 1),
(25, '采购审核', 3, NULL, 'product:purchase:audit', NULL, 2, 1),
(25, '采购取消', 3, NULL, 'product:purchase:cancel', NULL, 3, 1),
(25, '分批入库', 3, NULL, 'product:purchase:receive', NULL, 4, 1),
(26, '库存盘点', 3, NULL, 'product:stock:check', NULL, 1, 1);

-- 超级管理员绑定全部菜单权限（关联表自增 ID 不指定，靠 SELECT 防手滑写错）
INSERT INTO `admin_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id` FROM `admin_menu`;

-- ==========================================
-- 进销存域（归 mall-product，与库存同域：进货 → 入库 → 上架销售 → 退货）
-- ==========================================

CREATE TABLE `product_supplier` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '供应商ID',
  `name`        VARCHAR(64)  NOT NULL COMMENT '供应商名称',
  `contact`     VARCHAR(32)  DEFAULT NULL COMMENT '联系人',
  `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
  `address`     VARCHAR(128) DEFAULT NULL COMMENT '地址',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1合作中 0停用',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商表（进销存-进）';

CREATE TABLE `product_purchase` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '采购单ID',
  `purchase_sn`  VARCHAR(64)   NOT NULL COMMENT '采购单号（雪花ID，幂等）',
  `supplier_id`  BIGINT        NOT NULL COMMENT '供应商ID',
  `total_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '采购总金额（采购价 x 数量）',
  `status`       TINYINT       NOT NULL DEFAULT 0 COMMENT '状态：0待审核 1待收货 2部分入库 3已完成 4已取消',
  `audit_by`     VARCHAR(64)  DEFAULT NULL COMMENT '审核人',
  `audit_time`   DATETIME     DEFAULT NULL COMMENT '审核时间',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_purchase_sn` (`purchase_sn`),
  KEY `idx_supplier_status` (`supplier_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购单表（进销存-进）';

CREATE TABLE `product_purchase_item` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '采购明细ID',
  `purchase_id`       BIGINT        NOT NULL COMMENT '采购单ID',
  `sku_id`            BIGINT        NOT NULL COMMENT 'SKU ID',
  `quantity`          INT           NOT NULL COMMENT '采购数量',
  `received_quantity` INT           NOT NULL DEFAULT 0 COMMENT '已入库数量（分批收货累计）',
  `purchase_price`    DECIMAL(12,2) NOT NULL COMMENT '采购单价',
  `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_purchase_sku` (`purchase_id`, `sku_id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购单明细表（进销存-进）';
