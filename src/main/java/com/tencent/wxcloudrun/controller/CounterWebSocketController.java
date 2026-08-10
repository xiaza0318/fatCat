package com.tencent.wxcloudrun.controller;

import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.model.Counter;
import com.tencent.wxcloudrun.service.CounterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.Optional;

/**
 * WebSocket 计数器控制器
 * 通过 STOMP 消息处理计数请求，并主动推送计数更新给所有订阅客户端
 */
@Controller
public class CounterWebSocketController {

    final CounterService counterService;
    final SimpMessagingTemplate messagingTemplate;
    final Logger logger;

    public CounterWebSocketController(@Autowired CounterService counterService,
                                       @Autowired SimpMessagingTemplate messagingTemplate) {
        this.counterService = counterService;
        this.messagingTemplate = messagingTemplate;
        this.logger = LoggerFactory.getLogger(CounterWebSocketController.class);
    }

    /**
     * 处理客户端发送的计数操作（inc / clear） 
     * 客户端发送消息到 /app/counter 
     * 处理完后主动推送到 /topic/counter 
     */
    @MessageMapping("/counter")
    public void handleCounter(Map<String, String> payload) {
        String action = payload.get("action");
        logger.info("WebSocket /app/counter, action: {}", action);

        Optional<Counter> curCounter = counterService.getCounter(1);
        Integer count;

        if ("inc".equals(action)) {
            count = 1;
            if (curCounter.isPresent()) {
                count += curCounter.get().getCount();
            }
            Counter counter = new Counter();
            counter.setId(1);
            counter.setCount(count);
            counterService.upsertCount(counter);
        } else if ("clear".equals(action)) {
            if (curCounter.isPresent()) {
                counterService.clearCount(1);
            }
            count = 0;
        } else {
            logger.warn("WebSocket 收到未知 action: {}", action);
            return;
        }

        // 主动推送最新计数到所有订阅 /topic/counter 的客户端 
        messagingTemplate.convertAndSend("/topic/counter", ApiResponse.ok(count));
    }

    /**
     * 客户端订阅 /topic/counter 时，获取当前计数并返回 
     * 客户端发送消息到 /app/counter/init 
     */
    @MessageMapping("/counter/init")
    @SendTo("/topic/counter")
    public ApiResponse initCounter() {
        logger.info("WebSocket /app/counter/init");
        Optional<Counter> counter = counterService.getCounter(1);
        Integer count = 0;
        if (counter.isPresent()) {
            count = counter.get().getCount();
        }
        return ApiResponse.ok(count);
    }
}
