
CREATE DATABASE IF NOT EXISTS `know_engine` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `know_engine`;


-- 知识文档表
CREATE TABLE `knowledge_document` (
    `doc_id`        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '文档ID',
    `doc_title`     VARCHAR(1024) NOT NULL COMMENT '文档标题',
    `upload_user`   VARCHAR(255)  NULL     COMMENT '上传用户',
    `doc_url`       VARCHAR(2048) NULL     COMMENT '文档URL',
    `converted_doc_url` VARCHAR(2048) NULL  COMMENT '转换后的文档URL',
    `expire_date`   DATE          NULL     COMMENT '文档失效日期',
    `status`        VARCHAR(32)   NOT NULL COMMENT '状态：INIT, UPLOADED, CONVERTING, CONVERTED, CHUNKED, VECTOR_STORED',
    `accessible_by` VARCHAR(1024) NULL     COMMENT '可见范围',
    `description`   VARCHAR(512)  NULL     COMMENT '文档描述',
    `knowledge_base_type` VARCHAR(32) NULL COMMENT '知识库类型：DOCUMENT_SEARCH, DATA_QUERY',
    `extension`     TEXT          NULL     COMMENT '扩展字段，保存JSON字符串',
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `lock_version` INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`doc_id`),
    -- 为状态字段添加索引，优化定时任务扫表性能
    INDEX `idx_status` (`status`),
    -- 复合索引：状态+文档ID，优化分页查询性能
    INDEX `idx_status_doc_id` (`status`, `doc_id`),
    -- 创建时间索引，优化按时间排序查询
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT = '知识文档表';

-- 知识片段表
CREATE TABLE `knowledge_segment` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '片段ID',
    `text`        LONGTEXT     NOT NULL COMMENT '文本内容',
    `chunk_id`    VARCHAR(255) NULL     COMMENT '分片ID',
    `metadata`    VARCHAR(2048) NULL     COMMENT '元数据',
    `document_id` BIGINT       NOT NULL COMMENT '所属文档ID',
    `chunk_order` INT       NOT NULL COMMENT '顺序',
    `embedding_id` VARCHAR(255) NULL     COMMENT '嵌入ID',
    `status` VARCHAR(255) NULL     COMMENT '状态：STORED, VECTOR_STORED',
    `skip_embedding` INT NULL     COMMENT '是否跳过嵌入生成',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `lock_version`  INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    -- 文档ID索引
    INDEX `idx_document_id` (`document_id`),
    -- 复合索引：文档ID+顺序，优化按文档查询并排序
    INDEX `idx_document_id_chunk_order` (`document_id`, `chunk_order`),
    -- 复合索引：文档ID+状态+跳过嵌入，优化向量化补偿任务查询
    INDEX `idx_document_status_skip` (`document_id`, `status`, `skip_embedding`),
    -- 状态索引，优化按状态查询
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT = '知识片段表';

-- 表元数据表（存储动态创建的表的元数据信息）
CREATE TABLE `table_meta` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `table_name`   VARCHAR(128) NOT NULL COMMENT '表名',
    `description`  VARCHAR(512) NULL     COMMENT '表描述',
    `create_sql`   TEXT         NULL     COMMENT '建表语句',
    `columns_info` TEXT         NULL     COMMENT '字段信息（JSON格式）',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `lock_version` INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    -- 表名唯一索引
    UNIQUE INDEX `uk_table_name` (`table_name`),
    -- 创建时间索引
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT = '表元数据表';

-- AI对话会话表
CREATE TABLE `chat_conversation` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `conversation_id` VARCHAR(64) NOT NULL COMMENT '会话唯一标识',
    `user_id`         VARCHAR(64) NOT NULL COMMENT '用户ID',
    `title`           VARCHAR(512) NULL    COMMENT '会话标题',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `lock_version` INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `status`          VARCHAR(32) NOT NULL DEFAULT 'active' COMMENT '状态',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT = 'AI对话会话表';

-- AI对话消息表
CREATE TABLE `chat_message` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `message_id`       VARCHAR(64)  NOT NULL COMMENT '消息唯一标识',
    `conversation_id`  VARCHAR(64)  NOT NULL COMMENT '所属会话ID',
    `type`             VARCHAR(32)  NOT NULL COMMENT '角色：USER/ASSISTANT',
    `content`          LONGTEXT         NULL COMMENT '消息内容',
    `transform_content` LONGTEXT    NULL     COMMENT '改写后的内容',
    `token_count`      INT          NULL     COMMENT 'Token数量',
    `model_name`       VARCHAR(128) NULL     COMMENT '使用的模型名称',
    `rag_references`   JSON         NULL     COMMENT 'RAG引用内容JSON数组，包含document_id、document_title、chunk_id、chunk_content、similarity_score、retrieval_source等字段',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `lock_version` INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `metadata`         JSON         NULL     COMMENT '扩展元数据JSON格式',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_id` (`message_id`),
    INDEX `idx_conversation_id` (`conversation_id`),
    INDEX `idx_create_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT = 'AI对话消息表';
