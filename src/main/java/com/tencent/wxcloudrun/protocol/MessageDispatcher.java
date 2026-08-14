package com.tencent.wxcloudrun.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 消息分发器 —— 根据 cmd 字段将消息路由到对应的 Handler
 * 
 * 消息格式（兼容两种，前端 WmSocketJSF.send 会剥掉 data 包装直接发送内层对象）：
 *   1. { "data": { "cmd": 101001, "lang": "zh", "token": "...", ... } }
 *   2. { "cmd": 101001, "lang": "zh", "token": "...", ... }
 * 
 * 响应格式：
 *   { "cmd": 101001, "status": "success", ... }
 */
public class MessageDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(MessageDispatcher.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从原始 JSON 字符串中提取 cmd 并解析为枚举
     */
    @SuppressWarnings("unchecked")
    public static ParsedMessage parse(String rawJson) {
        try {
            Map<String, Object> outer = objectMapper.readValue(rawJson, Map.class);
            Map<String, Object> data;
            Object dataObj = outer.get("data");
            if (dataObj instanceof Map) {
                // 格式1：带 data 包装
                data = (Map<String, Object>) dataObj;
            } else if (outer.containsKey("cmd")) {
                // 格式2：无 data 包装，消息体本身就是业务数据
                data = outer;
            } else {
                logger.warn("消息缺少 data 字段: {}", rawJson);
                return new ParsedMessage(GameCommand.UNKNOWN, null, rawJson);
            }
            int cmdCode = data.containsKey("cmd") ? ((Number) data.get("cmd")).intValue() : -1;
            GameCommand cmd = GameCommand.fromCode(cmdCode);
            return new ParsedMessage(cmd, data, rawJson);
        } catch (Exception e) {
            logger.error("消息解析失败: {}", rawJson, e);
            return new ParsedMessage(GameCommand.UNKNOWN, null, rawJson);
        }
    }

    /**
     * 生成成功响应
     */
    public static String ok(GameCommand cmd, Map<String, Object> extra) {
        try {
            Map<String, Object> resp = new java.util.LinkedHashMap<>();
            resp.put("cmd", cmd.getCode());
            resp.put("status", "success");
            if (extra != null) {
                resp.putAll(extra);
            }
            return objectMapper.writeValueAsString(resp);
        } catch (Exception e) {
            logger.error("响应序列化失败 cmd={}", cmd, e);
            return "{\"status\":\"fail\"}";
        }
    }

    /**
     * 生成失败响应
     */
    public static String fail(GameCommand cmd, String msg) {
        try {
            Map<String, Object> resp = new java.util.LinkedHashMap<>();
            resp.put("cmd", cmd.getCode());
            resp.put("status", "fail");
            resp.put("msg", msg);
            return objectMapper.writeValueAsString(resp);
        } catch (Exception e) {
            logger.error("响应序列化失败 cmd={}", cmd, e);
            return "{\"status\":\"fail\"}";
        }
    }

    /**
     * 已解析的消息体
     */
    public static class ParsedMessage {
        public final GameCommand cmd;
        public final Map<String, Object> data;
        public final String rawJson;

        public ParsedMessage(GameCommand cmd, Map<String, Object> data, String rawJson) {
            this.cmd = cmd;
            this.data = data;
            this.rawJson = rawJson;
        }

        public String getString(String key) {
            if (data == null) return null;
            Object val = data.get(key);
            return val != null ? val.toString() : null;
        }

        public int getInt(String key) {
            if (data == null) return 0;
            Object val = data.get(key);
            return val instanceof Number ? ((Number) val).intValue() : 0;
        }

        public String getToken() {
            return getString("token");
        }
    }
}
