package com.nuaa.gameai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuaa.gameai.entity.DatasetImport;
import com.nuaa.gameai.entity.DatasetValueRow;
import com.nuaa.gameai.mapper.DatasetImportMapper;
import com.nuaa.gameai.mapper.DatasetValueRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DatasetImportService {

    private final DatasetImportMapper datasetImportMapper;
    private final DatasetValueRowMapper datasetValueRowMapper;
    private final ObjectMapper objectMapper;

    @Value("${gameai.upload-dir:uploads}")
    private String uploadDir;

    public Page<DatasetImport> page(int current, int size) {
        return datasetImportMapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<DatasetImport>()
                .orderByDesc(DatasetImport::getCreateTime));
    }

    public List<DatasetValueRow> rows(Long importId, int limit) {
        return datasetValueRowMapper.selectList(new LambdaQueryWrapper<DatasetValueRow>()
                .eq(DatasetValueRow::getImportId, importId)
                .last("LIMIT " + Math.min(limit, 5000)));
    }

    @Transactional
    public DatasetImport upload(String name, MultipartFile file) throws IOException {
        if (!StringUtils.hasText(name)) {
            name = file.getOriginalFilename();
        }
        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        String fn = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path dest = dir.resolve(fn);
        file.transferTo(dest.toFile());

        DatasetImport imp = new DatasetImport();
        imp.setName(name);
        imp.setSourceType("UPLOAD");
        imp.setOriginalFilename(file.getOriginalFilename());
        imp.setStoragePath(dest.toAbsolutePath().toString());
        imp.setStatus("PENDING");
        imp.setCreateTime(LocalDateTime.now());
        datasetImportMapper.insert(imp);

        parseAndStore(imp.getId(), dest, file.getOriginalFilename());
        return datasetImportMapper.selectById(imp.getId());
    }

    @Transactional
    public DatasetImport ingestFromApi(String name, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("rows 不能为空");
        }
        DatasetImport imp = new DatasetImport();
        imp.setName(StringUtils.hasText(name) ? name : "REST_API");
        imp.setSourceType("API");
        imp.setOriginalFilename(null);
        imp.setStoragePath(null);
        imp.setStatus("PENDING");
        imp.setCreateTime(LocalDateTime.now());
        datasetImportMapper.insert(imp);

        int ok = 0;
        int skip = 0;
        for (Map<String, Object> row : rows) {
            try {
                DatasetValueRow vr = rowFromMap(imp.getId(), row);
                datasetValueRowMapper.insert(vr);
                ok++;
            } catch (Exception e) {
                skip++;
            }
        }
        DatasetImport upd = new DatasetImport();
        upd.setId(imp.getId());
        upd.setStatus("OK");
        upd.setRowCount(ok);
        upd.setErrorMessage(skip > 0 ? "清洗跳过 " + skip + " 条无效/残缺记录" : null);
        datasetImportMapper.updateById(upd);
        return datasetImportMapper.selectById(imp.getId());
    }

    private DatasetValueRow rowFromMap(Long importId, Map<String, Object> m) {
        Object ts = m.get("ts");
        if (ts == null) {
            ts = m.get("t");
        }
        long tsVal;
        if (ts instanceof Number) {
            tsVal = ((Number) ts).longValue();
        } else {
            tsVal = Long.parseLong(String.valueOf(ts));
        }
        DatasetValueRow r = new DatasetValueRow();
        r.setImportId(importId);
        r.setTs(tsVal);
        r.setAgentId(m.get("agent_id") != null ? String.valueOf(m.get("agent_id")) : null);
        r.setAction(m.get("action") != null ? String.valueOf(m.get("action")) : null);
        Object vs = m.get("value_score");
        if (vs == null) {
            vs = m.get("value");
        }
        if (vs != null) {
            if (vs instanceof Number) {
                r.setValueScore(((Number) vs).doubleValue());
            } else {
                r.setValueScore(Double.parseDouble(String.valueOf(vs)));
            }
        }
        if (m.get("extra") != null) {
            try {
                r.setExtraJson(objectMapper.writeValueAsString(m.get("extra")));
            } catch (Exception e) {
                r.setExtraJson(String.valueOf(m.get("extra")));
            }
        }
        return r;
    }

    private void parseAndStore(Long importId, Path path, String originalName) {
        DatasetImport upd = new DatasetImport();
        upd.setId(importId);
        try {
            String content = Files.readString(path);
            int rows = 0;
            if (originalName != null && originalName.toLowerCase().endsWith(".json")) {
                rows = parseJson(importId, content);
            } else {
                rows = parseCsv(importId, content);
            }
            upd.setStatus("OK");
            upd.setRowCount(rows);
            upd.setErrorMessage(null);
        } catch (Exception e) {
            upd.setStatus("FAILED");
            upd.setErrorMessage(e.getMessage());
        }
        datasetImportMapper.updateById(upd);
    }

    private int parseJson(Long importId, String content) throws IOException {
        JsonNode root = objectMapper.readTree(content);
        List<DatasetValueRow> batch = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode n : root) {
                batch.add(rowFromJson(importId, n));
            }
        } else {
            batch.add(rowFromJson(importId, root));
        }
        for (DatasetValueRow r : batch) {
            datasetValueRowMapper.insert(r);
        }
        return batch.size();
    }

    private DatasetValueRow rowFromJson(Long importId, JsonNode n) {
        DatasetValueRow r = new DatasetValueRow();
        r.setImportId(importId);
        r.setTs(n.has("ts") ? n.get("ts").asLong() : n.path("t").asLong(0L));
        r.setAgentId(n.has("agent_id") ? n.get("agent_id").asText() : null);
        r.setAction(n.has("action") ? n.get("action").asText() : null);
        r.setValueScore(n.has("value_score") ? n.get("value_score").asDouble() : n.path("value").asDouble());
        if (n.has("extra")) {
            r.setExtraJson(n.get("extra").toString());
        }
        return r;
    }

    private int parseCsv(Long importId, String content) {
        String[] lines = content.split("\r?\n");
        if (lines.length == 0) {
            return 0;
        }
        int count = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] p = line.split(",");
            DatasetValueRow r = new DatasetValueRow();
            r.setImportId(importId);
            r.setTs(Long.parseLong(p[0].trim()));
            if (p.length > 1) {
                r.setAgentId(p[1].trim());
            }
            if (p.length > 2) {
                r.setAction(p[2].trim());
            }
            if (p.length > 3) {
                r.setValueScore(Double.parseDouble(p[3].trim()));
            }
            datasetValueRowMapper.insert(r);
            count++;
        }
        return count;
    }
}
