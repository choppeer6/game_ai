package com.nuaa.gameai.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
public class TrainWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 等待客户端发送 subscribe
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String action = root.path("action").asText("");
        String taskId = root.path("taskId").asText("");
        if ("subscribe".equals(action) && !taskId.isEmpty()) {
            registry.subscribe(taskId, session);
            session.sendMessage(new TextMessage("{\"type\":\"subscribed\",\"taskId\":\"" + taskId + "\"}"));
        } else if ("unsubscribe".equals(action) && !taskId.isEmpty()) {
            registry.unsubscribe(taskId, session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.removeSession(session);
    }
}
