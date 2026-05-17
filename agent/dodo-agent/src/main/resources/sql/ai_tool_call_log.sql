SET NAMES utf8mb4;

DROP TABLE IF EXISTS `ai_tool_call_log`;
CREATE TABLE `ai_tool_call_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '会话ID',
  `session_record_id` bigint NULL DEFAULT NULL COMMENT '会话记录ID',
  `agent_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '智能体类型',
  `tool_call_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工具调用ID',
  `tool_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工具名称',
  `arguments` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '调用参数JSON',
  `success` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否调用成功',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '失败原因',
  `result_size` int NULL DEFAULT NULL COMMENT '工具结果长度',
  `duration_ms` bigint NULL DEFAULT NULL COMMENT '调用耗时',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_conversation_id` (`conversation_id` ASC) USING BTREE,
  INDEX `idx_session_record_id` (`session_record_id` ASC) USING BTREE,
  INDEX `idx_tool_name` (`tool_name` ASC) USING BTREE,
  INDEX `idx_success` (`success` ASC) USING BTREE,
  INDEX `idx_create_time` (`create_time` ASC) USING BTREE
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工具调用日志表' ROW_FORMAT = DYNAMIC;
