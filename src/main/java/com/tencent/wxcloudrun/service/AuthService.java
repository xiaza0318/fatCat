package com.tencent.wxcloudrun.service;

import com.tencent.wxcloudrun.dao.UserMapper;
import com.tencent.wxcloudrun.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * 用户认证服务 —— 处理登录、注册、token 管理
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private UserMapper userMapper;

    /** 小游戏 AppID（微信公众平台获取），用于 code2Session 换取 openid */
    @Value("${wx.appid:}")
    private String wxAppId;

    /** 小游戏 AppSecret（微信公众平台获取，与 AppID 配套） */
    @Value("${wx.secret:}")
    private String wxSecret;

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
     * 微信登录：用 wx.login 的临时 code 换取稳定 openid，再以 openid 查找/创建用户。
     * 注意：wx.login 的 code 是一次性、每次登录都不同的，不能直接当 openid 用，
     * 否则每次登录都会生成新账号、存档永远丢失。
     */
    public User wxLogin(String code, String nickName) {
        String openid = exchangeCodeForOpenid(code);
        if (openid == null || openid.isEmpty()) {
            return null;
        }
        return uniqueLogin("wx_" + openid, 3, "WX", nickName);
    }

    /**
     * 调用微信 code2Session 接口，用临时 code 换取稳定 openid。
     * 需要在小游戏后台配置 appid/appsecret（application.yml 的 wx.appid / wx.secret）。
     */
    private String exchangeCodeForOpenid(String code) {
        if (wxAppId == null || wxAppId.isEmpty() || wxSecret == null || wxSecret.isEmpty()) {
            logger.error("未配置 wx.appid / wx.secret，无法换取 openid（微信登录将失败）");
            return null;
        }
        String url = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + wxAppId
                + "&secret=" + wxSecret
                + "&js_code=" + code
                + "&grant_type=authorization_code";
        try {
            RestTemplate restTemplate = new RestTemplate();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> resp = restTemplate.getForObject(url, java.util.Map.class);
            if (resp != null && resp.get("openid") != null) {
                logger.info("code2Session 成功，openid={}", resp.get("openid"));
                return resp.get("openid").toString();
            }
            logger.error("code2Session 失败: {}", resp);
        } catch (Exception e) {
            logger.error("code2Session 请求异常", e);
        }
        return null;
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
