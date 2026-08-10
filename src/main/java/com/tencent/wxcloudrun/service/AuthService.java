package com.tencent.wxcloudrun.service;

import com.tencent.wxcloudrun.dao.UserMapper;
import com.tencent.wxcloudrun.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 用户认证服务 —— 处理登录、注册、token 管理
 */
@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 账号密码注册
     * @return 注册成功返回 User（含 token），失败返回 null
     */
    public User register(String account, String password, String nickName) {
        User exist = userMapper.findByAccount(account);
        if (exist != null) {
            return null; // 账号已存在
        }

        User user = new User();
        user.setUid(UUID.randomUUID().toString());
        user.setAccount(account);
        user.setPassword(password);
        user.setNickName(nickName != null ? nickName : "");
        user.setAccountWay(0); // 0 = 账号密码
        user.setToken(UUID.randomUUID().toString().replace("-", ""));
        user.setPlatform("H5");
        user.setIsBlocked(false);

        userMapper.insert(user);
        return user;
    }

    /**
     * 账号密码登录
     * @return 登录成功返回 User（含新 token），失败返回 null
     */
    public User login(String account, String password) {
        User user = userMapper.findByAccount(account);
        if (user == null || !user.getPassword().equals(password)) {
            return null;
        }
        if (Boolean.TRUE.equals(user.getIsBlocked())) {
            return null; // 被封禁
        }

        // 刷新 token
        user.setToken(UUID.randomUUID().toString().replace("-", ""));
        userMapper.updateToken(user);
        return user;
    }

    /**
     * 游客/第三方统一登录（UniqueLogin）
     * 如果 account 已存在则直接登录，不存在则自动注册
     */
    public User uniqueLogin(String account, int accountWay, String platform, String nickName) {
        User user = userMapper.findByAccount(account);
        if (user == null) {
            // 自动注册
            user = new User();
            user.setUid(UUID.randomUUID().toString());
            user.setAccount(account);
            user.setPassword("autoLogin123");
            user.setAccountWay(accountWay);
            user.setNickName(nickName != null ? nickName : "");
            user.setAvatar("");
            user.setToken(UUID.randomUUID().toString().replace("-", ""));
            user.setPlatform(platform);
            user.setIsBlocked(false);
            userMapper.insert(user);
        } else {
            if (Boolean.TRUE.equals(user.getIsBlocked())) {
                return null;
            }
            // 刷新 token
            user.setToken(UUID.randomUUID().toString().replace("-", ""));
            userMapper.updateToken(user);
        }
        return user;
    }

    /**
     * 微信登录：通过 openid 查找或创建用户
     */
    public User wxLogin(String openid, String nickName) {
        return uniqueLogin("wx_" + openid, 3, "WX", nickName);
    }

    /**
     * 通过 token 验证用户身份
     */
    public User validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        return userMapper.findByToken(token);
    }
}
