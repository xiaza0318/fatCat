package com.tencent.wxcloudrun.controller;

import com.tencent.wxcloudrun.model.User;
import com.tencent.wxcloudrun.protocol.GameCommand;
import com.tencent.wxcloudrun.protocol.MessageDispatcher;
import com.tencent.wxcloudrun.service.AuthService;
import com.tencent.wxcloudrun.service.DataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 原生 WebSocket Handler —— 处理 feijiu2 游戏协议
 * 
 * 消息格式：{ "data": { "cmd": 101000, "lang": "zh", "token": "...", ... } }
 * 响应格式：{ "cmd": 101000, "status": "success"/"fail", ... }
 * 
 * 已实现命令：
 *   - 100000 StickPack（粘包分片）
 *   - 101000 Heartbeat（心跳 + 时间同步）
 *   - 101001/101002 账号密码登录 / 注册
 *   - 101005 游客/第三方统一登录
 *   - 103001 微信 code 登录
 *   - 102005 获取分区列表
 *   - 102004 获取分区全量存档
 *   - 102009 按 key 读取存档
 *   - 102003 / 102010 / 102007 单 key / 批量 / 全量存档
 *   - 102008 上传头像（简单响应）
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(GameWebSocketHandler.class);

    /** 已连接的 session 集合 */
    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Autowired
    private AuthService authService;

    @Autowired
    private DataService dataService;

    /** 游戏资源版本号（对应前端 Const.version） */
    @Value("${game.version:1.0.9}")
    private String gameVersion;

    /** 热更新资源版本号（默认空 = 不走热更新版本校验） */
    @Value("${game.prod-url-version:}")
    private String prodUrlVersion;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        logger.info("游戏 WS 连接建立: sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        logger.debug("收到游戏 WS 消息: {}", payload);

        MessageDispatcher.ParsedMessage parsed = MessageDispatcher.parse(payload);
        GameCommand cmd = parsed.cmd;

        switch (cmd) {
            case Heartbeat:
                handleHeartbeat(session, parsed);
                break;
            case StickPack:
                handleStickPack(session, parsed);
                break;
            // ==================== 登录 / 注册 ====================
            case UserRegister:
                handleUserRegister(session, parsed);
                break;
            case UserLogin:
                handleUserLogin(session, parsed);
                break;
            case UniqueLogin:
                handleUniqueLogin(session, parsed);
                break;
            case ReqWxSession:
                handleWxLogin(session, parsed);
                break;
            // ==================== 游戏数据存取 ====================
            case GetRegionList:
                handleGetRegionList(session, parsed);
                break;
            case GetRegionData:
                handleGetRegionData(session, parsed);
                break;
            case GetUserDataByKey:
                handleGetUserDataByKey(session, parsed);
                break;
            case SaveUserRecord:
                handleSaveUserRecord(session, parsed);
                break;
            case SaveUserRecordMulti:
                handleSaveUserRecordMulti(session, parsed);
                break;
            case SaveUserRecordAll:
                handleSaveUserRecordAll(session, parsed);
                break;
            case UploadAvatar:
                handleUploadAvatar(session, parsed);
                break;
            default:
                // 未实现的命令，返回提示
                String resp = MessageDispatcher.fail(cmd, "命令 " + cmd.getCode() + " 暂未实现");
                sendMessage(session, resp);
                logger.info("未实现的 WS 命令: {}", cmd.getCode());
                break;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        logger.info("游戏 WS 连接关闭: sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
        logger.error("游戏 WS 传输错误: sessionId={}", session.getId(), exception);
    }

    // ==================== 命令处理 ====================

    /**
     * 心跳处理（101000）
     * 客户端发送：{ "data": { "cmd": 101000, "token": "xxx" } }
     * 服务端响应：{ "cmd": 101000, "status": "success", "time": <服务器时间秒> }
     */
    private void handleHeartbeat(WebSocketSession session, MessageDispatcher.ParsedMessage parsed) {
        // 验证 token（轻量检查，不阻断心跳）
        String token = parsed.getToken();
        User user = authService.validateToken(token);
        if (user == null) {
            String resp = MessageDispatcher.fail(GameCommand.Heartbeat, "token 无效");
            sendMessage(session, resp);
            return;
        }

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("time", System.currentTimeMillis() / 1000);
        String resp = MessageDispatcher.ok(GameCommand.Heartbeat, extra);
        sendMessage(session, resp);
    }

    /**
     * 粘包分片处理（100000）
     * 目前简单回应成功，大数据传输主要走 HTTP
     */
    private void handleStickPack(WebSocketSession session, MessageDispatcher.ParsedMessage parsed) {
        String isEnd = parsed.getString("isEnd");
        if ("true".equals(isEnd) || "1".equals(isEnd)) {
            logger.debug("粘包传输完成: stickId={}", parsed.getString("stick_id"));
        }
        String resp = MessageDispatcher.ok(GameCommand.StickPack, null);
        sendMessage(session, resp);
    }

    /**
     * 账号密码注册（101002）
     * 客户端发送：{ "data": { "cmd": 101002, "account": "xxx", "password": "xxx", "nickName": "xxx" } }
     */
    private void handleUserRegister(WebSocketSession session, MessageDispatcher.ParsedMessage parsed) {
        String account = parsed.getString("account");
        String password = parsed.getString("password");
        String nickName = parsed.getString("nickName");

        if (account == null || account.isEmpty() || password == null || password.isEmpty()) {
            sendMessage(session, MessageDispatcher.fail(GameCommand.UserRegister, "账号和密码不能为空"));
            return;
        }

        User user = authService.register(account, password, nickName);
        if (user == null) {
            sendMessage(session, MessageDispatcher.fail(GameCommand.UserRegister, "账号已存在"));
            return;
        }

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("account", user.getAccount());
        extra.put("uid", user.getUid());
        extra.put("token", user.getToken());
        extra.put("nickName", user.getNickName());
        extra.put("accountWay", user.getAccountWay());
        sendMessage(session, MessageDispatcher.ok(GameCommand.UserRegister, extra));
    }

    /**
     * 账号密码登录（101001）
     * 客户端发送：{ "data": { "cmd": 101001, "account": "xxx", "password": "xxx" } }
     */
    private void handleUserLogin(WebSocketSession session, MessageDispatcher.ParsedMessage parsed) {
        String account = parsed.getString("account");
        String password = parsed.getString("password");

        if (account == null || account.isEmpty() || password == null || password.isEmpty()) {
            sendMessage(session, MessageDispatcher.fail(GameCommand.UserLogin, "账号和密码不能为空"));
            return;
        }

        User user = authService.login(account, password);
        if (user == null) {
            sendMessage(session, MessageDispatcher.fail(GameCommand.UserLogin, "账号或密码错误"));
            return;
        }

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("account", user.getAccount());
        extra.put("uid", user.getUid());
        extra.put("token", user.getToken());
        extra.put("nickName", user.getNickName());
        extra.put("accountWay", user.getAccountWay());
        sendMessage(session, MessageDispatcher.ok(GameCommand.UserLogin, extra));
    }

    /**
     * 游客/第三方统一登录（101005，注册即登录）
     * 客户端发送：{ "data": { "cmd": 101005, "account": "xxx", "accountWay": 0, "platform": "H5", "nickName": "xxx" } }
     */
    private void handleUniqueLogin(WebSocketSession session, MessageDispatcher.ParsedMessage parsed) {
        String account = parsed.getString("account");
        int accountWay = parsed.getInt("accountWay");
        String platform = parsed.getString("platform");
        String nickName = parsed.getString("nickName");

        if (account == null || account.isEmpty()) {
            sendMessage(session, MessageDispatcher.fail(GameCommand.UniqueLogin, "账号不能为空"));
            return;
        }
        if (platform == null || platform.isEmpty()) {
            platform = "H5";
        }

        User user = authService.uniqueLogin(account, accountWay, platform, nickName);
        if (user == null) {
            sendMessage(session, MessageDispatcher.fail(GameCommand.UniqueLogin, "账号已被封禁"));
            return;
        }

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("account", user.getAccount());
        extra.put("accountWay", user.getAccountWay());
        extra.put("uid", user.getUid());
        extra.put("token", user.getToken());
        extra.put("nickName", user.getNickName());
        sendMessage(session, MessageDispatcher.ok(GameCommand.UniqueLogin, extra));
    }

    /**
     * 微信登录（103001）
     * 客户端发送：{ "data": { "cmd": 103001, "jscode": "wx_login_code", "nickName": "xxx" } }
     * 注：目前以 jscode 作为 openid 标识创建/查找用户（与 AuthController 一致）
     */
    private void handleWxLogin(WebSocketSession session, MessageDispatcher.ParsedMessage parsed) {
        String jscode = parsed.getString("jscode");
        String nickName = parsed.getString("nickName");

        if (jscode == null || jscode.isEmpty()) {
            sendMessage(session, MessageDispatcher.fail(GameCommand.ReqWxSession, "微信 code 不能为空"));
            return;
        }

        User user = authService.wxLogin(jscode, nickName);
        if (user == null) {
            sendMessage(session, MessageDispatcher.fail(GameCommand.ReqWxSession, "登录失败"));
            return;
        }

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("account", user.getAccount());
        extra.put("accountWay", user.getAccountWay());
        extra.put("uid", user.getUid());
        extra.put("token", user.getToken());
        extra.put("nickName", user.getNickName());
        sendMessage(session, MessageDispatcher.ok(GameCommand.ReqWxSession, extra));
    }

    /**
     * 获取分区列表（102005）
     * 返回：{ "regions": {"1": "{...}"}, "servers": {"1": "{...}"} }
     */
    private void handleGetRegionList(WebSocketSession session, MessageDispatcher.ParsedMessage parsed) {
        Map<String, Object> extra = new LinkedHashMap<>();

        Map<String, String> regions = new LinkedHashMap<>();
        regions.put("1", "{\"id\":\"1\",\"name\":\"先行服\"}");

        Map<String, String> servers = new LinkedHashMap<>();
        servers.put("1", "{\"id\":\"1\",\"name\":\"先行服1\",\"region\":\"1\",\"status\":\"normal\",\"years\":\"2024-01-01 08:00:00\"}");

        extra.put("regions", regions);
        extra.put("servers", servers);
        extra.put("isUpdated", "0");
        sendMessage(session, MessageDispatcher.ok(GameCommand.GetRegionList, extra));
    }

    /**
     * 获取分区全量存档（102004）
     * 客户端发送：{ "data": { "cmd": 102004, "token": "xxx", "regionId": "1" } }
     * 服务端响应：{ "cmd": 102004, "status": "success", "token": "...", "uid": "...",
     *              "regionId": "1", "jsonData": "{\"JSF_key\":\"val\",...}",
     *              "version": "1.0.9", "prodUrlVersion": "", "nickName": "..." }
     */
    private void handleGetRegionData(WebSocketSession session, MessageDispatcher.ParsedMessage parsed) {
        User user = requireUser(session, parsed, GameCommand.GetRegionData);
        if (user == null) {
            return;
        }

        String regionId = parsed.getString("regionId");
        if (regionId == null || regionId.isEmpty()) {
            regionId = "1";
        }

        String jsonData = dataService.getRegionDataJson(user.getUid(), regionId);

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("token", user.getToken());
        extra.put("uid", user.getUid());
        extra.put("regionId", regionId);
        extra.put("jsonData", jsonData);
        extra.put("version", gameVersion);
        extra.put("prodUrlVersion", prodUrlVersion);
        extra.put("nickName", user.getNickName());
        sendMessage(session, MessageDispatcher.ok(GameCommand.GetRegionData, extra));
    }

    /**
     * 按 key 读取存档（102009）
     * 客户端发送：{ "data": { "cmd": 102009, "token": "xxx", "key": "JSF_xxx" } }
     * 服务端响应：{ "cmd": 102009, "status": "success", "key": "JSF_xxx", "val": "..." }
     */
    private void handleGetUserDataByKey(WebSocketSession session, MessageDispatcher.ParsedMessage parsed) {
        User user = requireUser(session, parsed, GameCommand.GetUserDataByKey);
        if (user == null) {
            return;
        }

        String key = parsed.getString("key");
        if (key == null || key.isEmpty()) {
            sendMessage(session, MessageDispatcher.fail(GameCommand.GetUserDataByKey, "key 不能为空"));
            return;
        }

        String regionId = parsed.getString("regionId");
        if (regionId == null || regionId.isEmpty()) {
            regionId = "1";
        }

        String val = dataService.getByKey(user.getUid(), regionId, key);

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("key", key);
        extra.put("val", val != null ? val : "");
        sendMessage(session, MessageDispatcher.ok(GameCommand.GetUserDataByKey, extra));
    }

    /**
     * 单 key 存档（102003）
     * 客户端发送：{ "data": { "cmd": 102003, "token": "xxx", "key": "JSF_xxx", "val": "...", "dml": "add upd"/"del" } }
     */
    private void handleSaveUserRecord(WebSocketSession session, MessageDispatcher.ParsedMessage parsed) {
        User user = requireUser(session, parsed, GameCommand.SaveUserRecord);
        if (user == null) {
            return;
        }

        String key = parsed.getString("key");
        if (key == null || key.isEmpty()) {
            sendMessage(session, MessageDispatcher.fail(GameCommand.SaveUserRecord, "key 不能为空"));
            return;
        }

        String regionId = parsed.getString("regionId");
        if (regionId == null || regionId.isEmpty()) {
            regionId = "1";
        }
        String val = parsed.getString("val");
        String dml = parsed.getString("dml");
        if (dml == null || dml.isEmpty()) {
            dml = "add";
        }

        dataService.saveRecord(user.getUid(), regionId, key, val != null ? val : "", dml);
        sendMessage(session, MessageDispatcher.ok(GameCommand.SaveUserRecord, null));
    }

    /**
     * 批量多 key 存档（102010）
     * 客户端发送：{ "data": { "cmd": 102010, "token": "xxx", "multiData": "{\"key1\":\"val1\",...}", "ops": "upd"/"del" } }
     */
    private void handleSaveUserRecordMulti(WebSocketSession session, MessageDispatcher.ParsedMessage parsed) {
        User user = requireUser(session, parsed, GameCommand.SaveUserRecordMulti);
        if (user == null) {
            return;
        }

        String multiData = parsed.getString("multiData");
        if (multiData == null || multiData.isEmpty()) {
            sendMessage(session, MessageDispatcher.fail(GameCommand.SaveUserRecordMulti, "multiData 不能为空"));
            return;
        }

        String ops = parsed.getString("ops");
        if (ops == null || ops.isEmpty()) {
            ops = "upd";
        }
        String regionId = parsed.getString("regionId");
        if (regionId == null || regionId.isEmpty()) {
            regionId = "1";
        }

        try {
            dataService.saveMultiRecord(user.getUid(), regionId, multiData, ops);
            sendMessage(session, MessageDispatcher.ok(GameCommand.SaveUserRecordMulti, null));
        } catch (Exception e) {
            logger.error("批量存档失败", e);
            sendMessage(session, MessageDispatcher.fail(GameCommand.SaveUserRecordMulti, "存档失败: " + e.getMessage()));
        }
    }

    /**
     * 全量存档（102007）
     * 客户端发送：{ "data": { "cmd": 102007, "token": "xxx", "allData": "{\"key1\":val1,...}" } }
     */
    private void handleSaveUserRecordAll(WebSocketSession session, MessageDispatcher.ParsedMessage parsed) {
        User user = requireUser(session, parsed, GameCommand.SaveUserRecordAll);
        if (user == null) {
            return;
        }

        String allData = parsed.getString("allData");
        if (allData == null || allData.isEmpty()) {
            allData = "{}";
        }
        String regionId = parsed.getString("regionId");
        if (regionId == null || regionId.isEmpty()) {
            regionId = "1";
        }

        try {
            dataService.saveAll(user.getUid(), regionId, allData);
            sendMessage(session, MessageDispatcher.ok(GameCommand.SaveUserRecordAll, null));
        } catch (Exception e) {
            logger.error("全量存档失败", e);
            sendMessage(session, MessageDispatcher.fail(GameCommand.SaveUserRecordAll, "存档失败: " + e.getMessage()));
        }
    }

    /**
     * 上传头像（102008）
     * 客户端发送：{ "data": { "cmd": 102008, "token": "xxx", "avatar": "url" } }
     * TODO: 头像地址持久化（当前仅响应成功）
     */
    private void handleUploadAvatar(WebSocketSession session, MessageDispatcher.ParsedMessage parsed) {
        User user = requireUser(session, parsed, GameCommand.UploadAvatar);
        if (user == null) {
            return;
        }
        sendMessage(session, MessageDispatcher.ok(GameCommand.UploadAvatar, null));
    }

    // ==================== 工具方法 ====================

    /**
     * 校验 token，失败时自动回复 fail 并返回 null
     */
    private User requireUser(WebSocketSession session, MessageDispatcher.ParsedMessage parsed, GameCommand cmd) {
        String token = parsed.getToken();
        User user = authService.validateToken(token);
        if (user == null) {
            sendMessage(session, MessageDispatcher.fail(cmd, "token 无效"));
            return null;
        }
        return user;
    }

    private void sendMessage(WebSocketSession session, String text) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(text));
            }
        } catch (IOException e) {
            logger.error("发送 WS 消息失败 sessionId={}", session.getId(), e);
        }
    }

    /**
     * 向所有连接的客户端广播消息（用于服务端推送：邮件、活动结算等）
     */
    public void broadcast(GameCommand cmd, Map<String, Object> extra) {
        String resp = MessageDispatcher.ok(cmd, extra);
        for (WebSocketSession session : sessions.values()) {
            sendMessage(session, resp);
        }
    }

    /**
     * 向指定用户推送消息
     */
    public void sendToUser(String uid, GameCommand cmd, Map<String, Object> extra) {
        String resp = MessageDispatcher.ok(cmd, extra);
        for (WebSocketSession session : sessions.values()) {
            // TODO: 需要在连接时记录 uid -> session 映射
            sendMessage(session, resp);
        }
    }
}
