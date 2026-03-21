package com.nuaa.gameai.pythonclient;

import com.nuaa.gameai.pythonclient.dto.TrainStartPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class PythonEngineClient {

    private final RestTemplate restTemplate;

    @Value("${gameai.python.base-url}")
    private String baseUrl;

    public PythonEngineClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void startTrain(TrainStartPayload payload) {
        String url = baseUrl + "/api/train/start";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TrainStartPayload> entity = new HttpEntity<>(payload, headers);
        restTemplate.postForEntity(url, entity, Map.class);
    }

    public void pauseTrain(String taskId) {
        postJson("/api/train/pause", Map.of("task_id", taskId));
    }

    public void resumeTrain(String taskId) {
        postJson("/api/train/resume", Map.of("task_id", taskId));
    }

    public void stopTrain(String taskId) {
        postJson("/api/train/stop", Map.of("task_id", taskId));
    }

    private void postJson(String path, Map<String, Object> body) {
        String url = baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
    }
}
