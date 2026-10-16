package com.freshveggie.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WebSocketServer {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketServer(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendToAllClient(String message) {
        //服务器向客户端发送消息
        log.info("推送消息 {}", message);
        messagingTemplate.convertAndSend("/topic/public", message);
        log.info("消息推送成功");
    }
}
