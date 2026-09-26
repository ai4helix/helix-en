package com.helix.console.batch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.helix.console.batch.entity.EngineTask;
import com.helix.console.batch.mapper.EngineTaskMapper;
import com.helix.console.batch.service.EngineTaskService;
import com.helix.console.batch.support.CsvDownloadUtil;
import com.helix.console.batch.support.CsvUtil;
import com.helix.console.common.BizException;
import com.helix.console.common.PageResult;
import com.helix.console.common.ResultCode;
import com.helix.console.engine.entity.Engine;
import com.helix.console.engine.mapper.EngineMapper;
import com.helix.console.system.security.TenantScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Engine task definition service implementation (engine data source).
 *
 * <p>Data flow: CSV upload -> header/row-by-row validation (task code format,
 * engine exists and is visible, user key column enum) -> upsert into the engine
 * DB by {@code (organ_id, task_code)} -> return import statistics and per-row
 * errors. Write ownership is decided by {@link TenantScope#writeOrgan}: admin
 * users land on platform common (0), SaaS users are forced to their own
 * organization.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EngineTaskServiceImpl implements EngineTaskService {

    /** Max rows per import */
    private static final int MAX_ROWS = 5000;

    /** Task code: starts with letter/underscore; letters/digits/underscore/hyphen; max 64 */
    private static final Pattern TASK_CODE_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_\\-]{0,63}$");

    /** Allowed user key columns */
    private static final List<String> KEY_FIELDS = Arrays.asList("uid", "pid");

    private static final List<String> REQUIRED_COLS =
            Arrays.asList("task_code", "task_name", "engine_code");
    private static final List<String> OPTIONAL_COLS =
            Arrays.asList("key_field", "description");

    private final EngineTaskMapper taskMapper;
    private final EngineMapper engineMapper;

    // ===== 1. Batch import =====

    @Override
    public Map<String, Object> importTasks(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Please upload a CSV file");
        }
        List<String[]> rows;
        try {
            rows = CsvUtil.parse(new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Failed to read file: " + e.getMessage());
        }
        if (rows.size() < 2) {
            throw BizException.of(ResultCode.PARAM_INVALID, "CSV must contain a header and at least one data row");
        }
        if (rows.size() - 1 > MAX_ROWS) {
            throw BizException.of(ResultCode.PARAM_INVALID, "A single import must not exceed " + MAX_ROWS + " rows");
        }

        Map<String, Integer> header = headerIndex(rows.get(0));
        // Visible and enabled engines: code -> Engine (avoids per-row DB queries)
        Map<String, Engine> visibleEngines = visibleEnabledEngines();
        // Write ownership: admin users 0 (platform common), SaaS users own organization
        Long organ = TenantScope.writeOrgan((Long) null);

        int inserted = 0;
        int updated = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            int rowNo = i;
            String taskCode = cell(row, header.get("task_code"));
            try {
                String taskName = cell(row, header.get("task_name"));
                String engineCode = cell(row, header.get("engine_code"));
                String keyField = StringUtils.defaultIfBlank(cell(row, header.get("key_field")), "uid")
                        .toLowerCase();
                String description = CsvUtil.truncate(cell(row, header.get("description")), 500);

                validateRow(taskCode, taskName, engineCode, keyField, visibleEngines);

                EngineTask existing = taskMapper.selectOne(new LambdaQueryWrapper<EngineTask>()
                        .eq(EngineTask::getOrganId, organ)
                        .eq(EngineTask::getTaskCode, taskCode)
                        .eq(EngineTask::getDeleted, 0));
                if (existing != null) {
                    existing.setTaskName(taskName);
                    existing.setEngineCode(engineCode);
                    existing.setKeyField(keyField);
                    existing.setDescription(description);
                    existing.setUpdatedTime(LocalDateTime.now());
                    taskMapper.updateById(existing);
                    updated++;
                } else {
                    EngineTask t = new EngineTask();
                    t.setOrganId(organ);
                    t.setTaskCode(taskCode);
                    t.setTaskName(taskName);
                    t.setEngineCode(engineCode);
                    t.setKeyField(keyField);
                    t.setDescription(description);
                    t.setStatus(1);
                    t.setDeleted(0);
                    t.setCreatedBy(com.helix.console.system.security.UserContext.currentUserId());
                    t.setCreatedTime(LocalDateTime.now());
                    t.setUpdatedTime(LocalDateTime.now());
                    taskMapper.insert(t);
                    inserted++;
                }
            } catch (BizException e) {
                errors.add(error(rowNo, taskCode, e.getMessage()));
            } catch (Exception e) {
                log.warn("Exception at row {} during engine task import: {}", rowNo, e.getMessage());
                errors.add(error(rowNo, taskCode, "Import error: " + e.getMessage()));
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inserted", inserted);
        out.put("updated", updated);
        out.put("total", rows.size() - 1);
        out.put("errors", errors);
        return out;
    }

    private void validateRow(String taskCode, String taskName, String engineCode, String keyField,
                             Map<String, Engine> visibleEngines) {
        if (StringUtils.isBlank(taskCode)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Task code (task_code) must not be blank");
        }
        if (!TASK_CODE_PATTERN.matcher(taskCode).matches()) {
            throw BizException.of(ResultCode.PARAM_INVALID,
                    "Task code must start with a letter or underscore and contain only letters, digits, underscores or hyphens, max 64 chars");
        }
        if (StringUtils.isBlank(taskName)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Task name (task_name) must not be blank");
        }
        if (taskName.length() > 200) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Task name must not exceed 200 characters");
        }
        if (StringUtils.isBlank(engineCode)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Bound engine (engine_code) must not be blank");
        }
        if (!visibleEngines.containsKey(engineCode)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Engine does not exist, is disabled, or is not accessible: " + engineCode);
        }
        if (!KEY_FIELDS.contains(keyField)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "User key column (key_field) only supports uid / pid");
        }
    }

    /** Visible and enabled engines (code -> Engine) */
    private Map<String, Engine> visibleEnabledEngines() {
        List<Engine> engines = engineMapper.selectList(
                new LambdaQueryWrapper<Engine>().eq(Engine::getStatus, 1));
        Map<String, Engine> out = new HashMap<>();
        for (Engine e : engines) {
            if (StringUtils.isBlank(e.getCode())) {
                continue;
            }
            Long organId = e.getOrganId() == null ? null : e.getOrganId().longValue();
            if (TenantScope.isVisible(organId)) {
                out.put(e.getCode(), e);
            }
        }
        return out;
    }

    // ===== 2. Page query =====

    @Override
    public PageResult<EngineTask> pageTasks(long pageNo, long pageSize, String keyword, Integer status) {
        LambdaQueryWrapper<EngineTask> w = new LambdaQueryWrapper<>();
        w.eq(EngineTask::getDeleted, 0);
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            w.in(EngineTask::getOrganId, organs);
        }
        if (status != null) {
            w.eq(EngineTask::getStatus, status);
        }
        if (StringUtils.isNotBlank(keyword)) {
            w.and(x -> x.like(EngineTask::getTaskCode, keyword)
                    .or().like(EngineTask::getTaskName, keyword));
        }
        w.orderByDesc(EngineTask::getId);
        Page<EngineTask> page = taskMapper.selectPage(new Page<>(pageNo, pageSize), w);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNo, pageSize);
    }

    // ===== 3. Template download =====

    @Override
    public void downloadTemplate(HttpServletResponse response) {
        List<String> lines = new ArrayList<>();
        lines.add("task_code,task_name,engine_code,key_field,description");
        lines.add("risk_batch_daily,Daily risk batch run,RISK_MAIN,uid,Example: run the main risk engine once for existing users");
        lines.add("# task_code task code (required, unique per tenant) | task_name name (required) | "
                + "engine_code bound engine code (required) | key_field user key column uid/pid (optional, default uid) | description description (optional)");
        try {
            CsvDownloadUtil.write(response, "Engine Task Import Template", lines);
        } catch (IOException e) {
            throw BizException.of(ResultCode.SYSTEM_ERROR, "Template download failed: " + e.getMessage());
        }
    }

    // ===== 4. Status / delete =====

    @Override
    public void changeStatus(Long id, Integer status) {
        EngineTask t = requireTask(id);
        TenantScope.checkVisible(t.getOrganId());
        if (status == null || (status != 0 && status != 1)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Status only supports 0 disabled / 1 enabled");
        }
        t.setStatus(status);
        t.setUpdatedTime(LocalDateTime.now());
        taskMapper.updateById(t);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void deleteTask(Long id) {
        EngineTask t = requireTask(id);
        TenantScope.checkVisible(t.getOrganId());
        // Project convention: manual logical delete (0 normal / 1 deleted); query side adds deleted = 0 filter
        t.setDeleted(1);
        t.setUpdatedTime(LocalDateTime.now());
        taskMapper.updateById(t);
    }

    private EngineTask requireTask(Long id) {
        EngineTask t = id == null ? null : taskMapper.selectOne(new LambdaQueryWrapper<EngineTask>()
                .eq(EngineTask::getId, id)
                .eq(EngineTask::getDeleted, 0));
        if (t == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Engine task does not exist: " + id);
        }
        return t;
    }

    // ===== Utilities =====

    private Map<String, Integer> headerIndex(String[] head) {
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < head.length; i++) {
            String h = StringUtils.trimToEmpty(head[i]).toLowerCase();
            if (!h.isEmpty()) {
                idx.putIfAbsent(h, i);
            }
        }
        for (String r : REQUIRED_COLS) {
            if (!idx.containsKey(r)) {
                throw BizException.of(ResultCode.PARAM_INVALID, "Header is missing column " + r);
            }
        }
        for (String o : OPTIONAL_COLS) {
            idx.putIfAbsent(o, -1);
        }
        return idx;
    }

    private String cell(String[] row, Integer idx) {
        if (idx == null || idx < 0 || idx >= row.length) {
            return null;
        }
        return StringUtils.trimToNull(row[idx]);
    }

    private Map<String, Object> error(int row, String taskCode, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("row", row);
        m.put("taskCode", taskCode);
        m.put("message", message);
        return m;
    }
}
