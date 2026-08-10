package com.tencent.wxcloudrun.controller;

import com.tencent.wxcloudrun.model.User;
import com.tencent.wxcloudrun.protocol.GameCommand;
import com.tencent.wxcloudrun.protocol.MessageDispatcher;
import com.tencent.wxcloudrun.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 原生 WebSocket Handler —— 处理 feijiu2 游戏协议
 * 
 * 消息格式：{ "data": { "cmd": 101000, "lang": "zh", "token": "...", ... } }
 * 响应格式：{ "cmd": 101000, "status": "success"/"fail", ... }
 * 
 * 处理的命令：
 *   - 101000 Heartbeat（心跳）
 *   - 100000 StickPack（粘包分片）
 *   - 201xxx 排行榜
 *   - 301xxx 活动
 *   - 302xxx 帝国战争
 *   - 104003 服务端推送邮件
 *   - 105001/105002 背包日志/财神分享
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(GameWebSocketHandler.class);

    /** 已连接的 session 集合 */
    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Autowired
    private AuthService authService;

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
            default:
                // 未实现的命令，返回提示
                String resp = MessageDispatcher.fail(cmd, "命令 " + cmd.getCode() + " 暂未实现，请使用 HTTP 接口");
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

        Map<String, Object> extra = new java.util.LinkedHashMap<>();
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

    // ==================== 工具方法 ====================

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
