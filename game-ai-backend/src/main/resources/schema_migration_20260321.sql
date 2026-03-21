-- 已有库升级：在 MySQL 中执行一次（若列已存在会报错，可忽略）
USE `game_ai_db`;
ALTER TABLE `training_task` ADD COLUMN `checkpoint_path` VARCHAR(1000) DEFAULT NULL COMMENT '策略权重目录' AFTER `error_message`;
