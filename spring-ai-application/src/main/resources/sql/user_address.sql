-- 用户地址表
CREATE TABLE IF NOT EXISTS `user_address` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户地址ID',
    `user_id`         BIGINT       NOT NULL COMMENT '用户ID',
    `receiver_name`   VARCHAR(64)  NOT NULL COMMENT '收货人姓名',
    `receiver_phone`  VARCHAR(20)  NOT NULL COMMENT '收货人电话',
    `country`         VARCHAR(64)  NULL COMMENT '国家',
    `province`        VARCHAR(64)  NULL COMMENT '省份/直辖市',
    `city`            VARCHAR(64)  NULL COMMENT '城市',
    `district`        VARCHAR(64)  NULL COMMENT '区县',
    `detail_address`  VARCHAR(255) NULL COMMENT '详细地址',
    `postal_code`     VARCHAR(20)  NULL COMMENT '邮政编码',
    `is_default`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否为默认地址(0-否, 1-是)',
    `address_tag`     VARCHAR(32)  NULL COMMENT '地址标签',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标志(0-未删除, 1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户地址表';
