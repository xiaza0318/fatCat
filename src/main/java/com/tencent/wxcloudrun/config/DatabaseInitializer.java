package com.tencent.wxcloudrun.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 数据库初始化 —— 应用启动时自动建表（幂等，IF NOT EXISTS）
 */
@Component
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(String... args) {
        String[] sqls = {
            // 用户表
            "CREATE TABLE IF NOT EXISTS `users` (" +
            "`id` int(11) NOT NULL AUTO_INCREMENT," +
            "`uid` varchar(64) NOT NULL COMMENT '用户唯一标识'," +
            "`account` varchar(128) NOT NULL COMMENT '登录账号'," +
            "`password` varchar(128) NOT NULL COMMENT '密码'," +
            "`account_way` tinyint(4) DEFAULT 0 COMMENT '账号类型'," +
            "`nick_name` varchar(128) DEFAULT '' COMMENT '昵称'," +
            "`avatar` varchar(512) DEFAULT '' COMMENT '头像URL'," +
            "`token` varchar(128) DEFAULT '' COMMENT '登录token'," +
            "`platform` varchar(32) DEFAULT '' COMMENT '平台'," +
            "`is_blocked` tinyint(1) DEFAULT 0 COMMENT '是否封禁'," +
            "`created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "`updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "PRIMARY KEY (`id`)," +
            "UNIQUE KEY `uk_uid` (`uid`)," +
            "UNIQUE KEY `uk_account` (`account`)," +
            "KEY `idx_token` (`token`)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8;",

            // 用户游戏数据表
            "CREATE TABLE IF NOT EXISTS `user_data` (" +
            "`id` int(11) NOT NULL AUTO_INCREMENT," +
            "`uid` varchar(64) NOT NULL COMMENT '用户UID'," +
            "`region_id` varchar(32) NOT NULL DEFAULT '1' COMMENT '分区ID'," +
            "`data_key` varchar(128) NOT NULL COMMENT '数据键名'," +
            "`data_val` mediumtext COMMENT '数据值'," +
            "`created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "`updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "PRIMARY KEY (`id`)," +
            "UNIQUE KEY `uk_uid_region_key` (`uid`, `region_id`, `data_key`)," +
            "KEY `idx_uid_region` (`uid`, `region_id`)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8;"
        };

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : sqls) {
                stmt.execute(sql);
            }
            logger.info("数据库表初始化完成 (users, user_data)");
        } catch (Exception e) {
            logger.error("数据库表初始化失败", e);
        }
    }
}
