package com.tencent.wxcloudrun.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体 —— 对应 users 表
 */
@Data
public class User {
    private Integer id;
    private String uid;
    private String account;
    private String password;
    private Integer accountWay;
    private String nickName;
    private String avatar;
    private String token;
    private String platform;
    private Boolean isBlocked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
