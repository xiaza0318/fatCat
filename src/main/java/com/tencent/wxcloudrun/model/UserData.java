package com.tencent.wxcloudrun.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户游戏数据实体（key-value 存档） —— 对应 user_data 表
 */
@Data
public class UserData {
    private Integer id;
    private String uid;
    private String regionId;
    private String dataKey;
    private String dataVal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
