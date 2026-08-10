CREATE TABLE `Counters` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `count` int(11) NOT NULL DEFAULT '1',
  `createdAt` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updatedAt` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8;

-- ==================== 游戏业务表 ====================

-- 用户表
CREATE TABLE `users` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `uid` varchar(64) NOT NULL COMMENT '用户唯一标识（UUID）',
  `account` varchar(128) NOT NULL COMMENT '登录账号',
  `password` varchar(128) NOT NULL COMMENT '密码',
  `account_way` tinyint(4) DEFAULT 0 COMMENT '账号类型: 0=游客, 1=Facebook, 2=Google, 3=微信, 4=手机号',
  `nick_name` varchar(128) DEFAULT '' COMMENT '昵称',
  `avatar` varchar(512) DEFAULT '' COMMENT '头像 URL',
  `token` varchar(128) DEFAULT '' COMMENT '当前登录 token',
  `platform` varchar(32) DEFAULT '' COMMENT '平台: Google/H5/WX',
  `is_blocked` tinyint(1) DEFAULT 0 COMMENT '是否封禁',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uid` (`uid`),
  UNIQUE KEY `uk_account` (`account`),
  KEY `idx_token` (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 用户游戏数据表（key-value 存档）
CREATE TABLE `user_data` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `uid` varchar(64) NOT NULL COMMENT '用户 UID',
  `region_id` varchar(32) NOT NULL DEFAULT '1' COMMENT '分区 ID',
  `data_key` varchar(128) NOT NULL COMMENT '数据键名（如 JSF_playerModel）',
  `data_val` mediumtext COMMENT '数据值（JSON 字符串）',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uid_region_key` (`uid`, `region_id`, `data_key`),
  KEY `idx_uid_region` (`uid`, `region_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;