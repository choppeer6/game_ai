package com.nuaa.gameai.dto;

import lombok.Data;

@Data
public class TrainingCreateRequest {
    private Long sceneId;
    private Long algoTemplateId;
    private String algoName;
    /** JSON 字符串 */
    private String hyperParameters;
    private boolean startImmediately = true;
}
