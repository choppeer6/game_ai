CREATE DATABASE IF NOT EXISTS `game_ai_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `game_ai_db`;

-- 用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(64) NOT NULL COMMENT '登录名',
  `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt 密码',
  `display_name` VARCHAR(100) DEFAULT NULL,
  `role` VARCHAR(32) NOT NULL DEFAULT 'ENGINEER' COMMENT 'ADMIN, ENGINEER, COMMANDER',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

-- 场景配置（扩展 JSON）
CREATE TABLE IF NOT EXISTS `scene_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `scene_name` VARCHAR(100) NOT NULL,
  `map_width` INT NOT NULL,
  `map_height` INT NOT NULL,
  `red_drone_count` INT NOT NULL,
  `blue_drone_count` INT NOT NULL,
  `scene_json` JSON DEFAULT NULL COMMENT '障碍物、初始坐标、性能参数、胜负条件等',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='博弈对抗场景';

-- 算法配置模板
CREATE TABLE IF NOT EXISTS `algorithm_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `template_name` VARCHAR(100) NOT NULL,
  `algo_name` VARCHAR(50) NOT NULL COMMENT 'MAPPO, MADDPG, IPPO 等',
  `hyper_parameters` JSON NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='算法超参模板';

-- 训练任务
CREATE TABLE IF NOT EXISTS `training_task` (
  `task_id` VARCHAR(64) NOT NULL,
  `scene_id` BIGINT NOT NULL,
  `algo_template_id` BIGINT DEFAULT NULL,
  `algo_name` VARCHAR(50) NOT NULL,
  `hyper_parameters` JSON NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0待启动 1排队 2训练中 3暂停 4已完成 5异常 6已终止',
  `queue_order` INT DEFAULT 0,
  `current_epoch` INT DEFAULT 0,
  `error_message` VARCHAR(2000) DEFAULT NULL,
  `checkpoint_path` VARCHAR(1000) DEFAULT NULL COMMENT '策略权重目录',
  `start_time` DATETIME DEFAULT NULL,
  `end_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`task_id`),
  KEY `idx_scene` (`scene_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='训练任务';

-- 训练指标日志（epoch 级）
CREATE TABLE IF NOT EXISTS `training_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` VARCHAR(64) NOT NULL,
  `epoch` INT NOT NULL,
  `red_win_rate` DOUBLE DEFAULT NULL,
  `cumulative_reward` DOUBLE DEFAULT NULL,
  `loss_value` DOUBLE DEFAULT NULL,
  `value_estimate` DOUBLE DEFAULT NULL COMMENT '态势价值评估摘要',
  `raw_metrics` JSON DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_epoch` (`task_id`, `epoch`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='训练过程指标';

-- 数据接入：导入批次
CREATE TABLE IF NOT EXISTS `dataset_import` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(200) NOT NULL,
  `source_type` VARCHAR(32) NOT NULL COMMENT 'FILE_API, UPLOAD',
  `original_filename` VARCHAR(500) DEFAULT NULL,
  `storage_path` VARCHAR(1000) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, OK, FAILED',
  `row_count` INT DEFAULT 0,
  `error_message` VARCHAR(2000) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部数据导入批次';

-- 标准化后的价值数据行
CREATE TABLE IF NOT EXISTS `dataset_value_row` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `import_id` BIGINT NOT NULL,
  `ts` BIGINT NOT NULL COMMENT '时间戳或步序号',
  `agent_id` VARCHAR(64) DEFAULT NULL,
  `action` VARCHAR(255) DEFAULT NULL,
  `value_score` DOUBLE DEFAULT NULL,
  `extra_json` JSON DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_import` (`import_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='清洗后的动作价值数据';

-- 默认管理员由应用启动时写入（用户名 admin / 密码 admin123）
