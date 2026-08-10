package com.tencent.wxcloudrun.config;

import com.tencent.wxcloudrun.controller.GameWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置
 * 
 * 同时支持两种协议：
 *   1. STOMP over WebSocket（/ws）  —— 原有的 Counter 示例
 *   2. 原生 WebSocket（/game-ws）   —— feijiu2 游戏协议
 */
@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer, WebSocketConfigurer {

    @Autowired
    private GameWebSocketHandler gameWebSocketHandler;

    // ==================== STOMP 配置（Counter 示例） ====================

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    // ==================== 原生 WebSocket 配置（游戏协议） ====================

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 游戏 WebSocket 端点，feijiu2 前端连接此端点
        registry.addHandler(gameWebSocketHandler, "/game-ws")
                .setAllowedOriginPatterns("*");
    }
}
