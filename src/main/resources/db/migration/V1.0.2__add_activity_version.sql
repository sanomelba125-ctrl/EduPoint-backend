ALTER TABLE `activity`
    ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '报名名额并发控制版本号';
