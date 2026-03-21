package com.nuaa.gameai.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class WebSocketSessionRegistry {

    private final Map<String, CopyOnWriteArraySet<WebSocketSession>> taskSubscribers = new ConcurrentHashMap<>();

    public void subscribe(String taskId, WebSocketSession session) {
        taskSubscribers.computeIfAbsent(taskId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void unsubscribe(String taskId, WebSocketSession session) {
        Set<WebSocketSession> set = taskSubscribers.get(taskId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                taskSubscribers.remove(taskId);
            }
        }
    }

    public void removeSession(WebSocketSession session) {
        for (var e : taskSubscribers.entrySet()) {
            e.getValue().remove(session);
        }
    }

    public void broadcastTask(String taskId, String json) {
        Set<WebSocketSession> set = taskSubscribers.get(taskId);
        if (set == null || set.isEmpty()) {
            return;
        }
        TextMessage msg = new TextMessage(json.getBytes(StandardCharsets.UTF_8));
        for (WebSocketSession s : set) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(msg);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
