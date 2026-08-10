package com.tencent.wxcloudrun.controller;

import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.model.User;
import com.tencent.wxcloudrun.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户认证 HTTP 接口
 * 
 * 响应格式兼容 feijiu2 前端：{ "cmd": NNN, "status": "success"/"fail", ... }
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthService authService;

    /**
     * 账号密码注册
     * POST /api/auth/register
     * Body: { "account": "xxx", "password": "xxx", "nickName": "xxx" }
     */
    @PostMapping("/register")
    public ApiResponse register(@RequestBody Map<String, String> body) {
        String account = body.get("account");
        String password = body.get("password");
        String nickName = body.getOrDefault("nickName", "");

        if (account == null || account.isEmpty() || password == null || password.isEmpty()) {
            return ApiResponse.error("账号和密码不能为空");
        }

        User user = authService.register(account, password, nickName);
        if (user == null) {
            return ApiResponse.error("账号已存在");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("account", user.getAccount());
        data.put("uid", user.getUid());
        data.put("token", user.getToken());
        data.put("nickName", user.getNickName());
        data.put("accountWay", user.getAccountWay());
        return ApiResponse.ok(data);
    }

    /**
     * 账号密码登录
     * POST /api/auth/login
     * Body: { "account": "xxx", "password": "xxx" }
     */
    @PostMapping("/login")
    public ApiResponse login(@RequestBody Map<String, String> body) {
        String account = body.get("account");
        String password = body.get("password");

        if (account == null || account.isEmpty() || password == null || password.isEmpty()) {
            return ApiResponse.error("账号和密码不能为空");
        }

        User user = authService.login(account, password);
        if (user == null) {
            return ApiResponse.error("账号或密码错误");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("account", user.getAccount());
        data.put("uid", user.getUid());
        data.put("token", user.getToken());
        data.put("nickName", user.getNickName());
        data.put("accountWay", user.getAccountWay());
        return ApiResponse.ok(data);
    }

    /**
     * 游客/第三方统一登录（注册即登录）
     * POST /api/auth/unique-login
     * Body: { "account": "xxx", "accountWay": 0, "platform": "Google", "nickName": "xxx" }
     */
    @PostMapping("/unique-login")
    public ApiResponse uniqueLogin(@RequestBody Map<String, Object> body) {
        String account = body.get("account") != null ? body.get("account").toString() : null;
        int accountWay = body.containsKey("accountWay") ? ((Number) body.get("accountWay")).intValue() : 0;
        Object platformObj = body.getOrDefault("platform", "H5");
        String platform = platformObj != null ? platformObj.toString() : "H5";
        String nickName = body.getOrDefault("nickName", "").toString();

        if (account == null || account.isEmpty()) {
            return ApiResponse.error("账号不能为空");
        }

        User user = authService.uniqueLogin(account, accountWay, platform, nickName);
        if (user == null) {
            return ApiResponse.error("账号已被封禁");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("account", user.getAccount());
        data.put("accountWay", user.getAccountWay());
        data.put("uid", user.getUid());
        data.put("token", user.getToken());
        data.put("nickName", user.getNickName());
        return ApiResponse.ok(data);
    }

    /**
     * 微信登录
     * POST /api/auth/wx-login
     * Body: { "code": "wx_login_code" }
     */
    @PostMapping("/wx-login")
    public ApiResponse wxLogin(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isEmpty()) {
            return ApiResponse.error("微信 code 不能为空");
        }

        // TODO: 调用微信接口用 code 换取 openid
        // 目前直接以 code 为标识创建/查找用户
        User user = authService.wxLogin(code, "");
        if (user == null) {
            return ApiResponse.error("登录失败");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("account", user.getAccount());
        data.put("uid", user.getUid());
        data.put("token", user.getToken());
        data.put("nickName", user.getNickName());
        data.put("accountWay", user.getAccountWay());
        return ApiResponse.ok(data);
    }

    /**
     * Token 验证
     * GET /api/auth/verify?token=xxx
     */
    @GetMapping("/verify")
    public ApiResponse verify(@RequestParam String token) {
        User user = authService.validateToken(token);
        if (user == null) {
            return ApiResponse.error("token 无效或已过期");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.getUid());
        data.put("account", user.getAccount());
        data.put("nickName", user.getNickName());
        return ApiResponse.ok(data);
    }
}
