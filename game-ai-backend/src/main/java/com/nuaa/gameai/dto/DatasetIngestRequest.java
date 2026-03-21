package com.nuaa.gameai.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DatasetIngestRequest {
    private String name;
    private List<Map<String, Object>> rows;
}
