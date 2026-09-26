package com.helix.console.engine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.helix.console.common.BizException;
import com.helix.console.common.ResultCode;
import com.helix.console.engine.dto.DecisionTableSaveDTO;
import com.helix.console.engine.entity.DecisionTableEntity;
import com.helix.console.engine.entity.DtCellEntity;
import com.helix.console.engine.entity.DtColumnEntity;
import com.helix.console.engine.entity.DtRowEntity;
import com.helix.console.engine.mapper.DecisionTableMapper;
import com.helix.console.engine.mapper.DtCellMapper;
import com.helix.console.engine.mapper.DtColumnMapper;
import com.helix.console.engine.mapper.DtRowMapper;
import com.helix.console.system.security.TenantScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decision table management service implementation.
 *
 * <p>Saving uses <b>whole-table rebuild</b>: columns/rows/cells are small in number, so delete-all + insert-all
 * is more reliable than cell-by-cell diff. On save, the readable expression of every row is also generated
 * (consistent with the engine's rendering convention) for direct display in decision logs and panels.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionTableServiceImpl implements com.helix.console.engine.service.DecisionTableService {

    private final DecisionTableMapper tableMapper;
    private final DtColumnMapper columnMapper;
    private final DtRowMapper rowMapper;
    private final DtCellMapper cellMapper;

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public Integer save(DecisionTableSaveDTO dto) {
        validate(dto);

        DecisionTableEntity table = new DecisionTableEntity();
        table.setId(dto.getId());
        table.setCode(dto.getCode());
        table.setName(dto.getName());
        table.setDescription(dto.getDescription());
        table.setHitPolicy(StringUtils.defaultIfBlank(dto.getHitPolicy(), "FIRST"));
        table.setEngineId(dto.getEngineId());
        table.setParentId(dto.getParentId());
        table.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        table.setRemark(dto.getRemark());
        table.setUpdatedTime(LocalDateTime.now());
        table.setDeleted(0);
        if (dto.getId() == null) {
            // Admin users default to platform public (0); organization users are forced to their own organization
            table.setOrganId(TenantScope.writeOrgan((Integer) null));
            table.setCreatedTime(LocalDateTime.now());
            tableMapper.insert(table);
        } else {
            DecisionTableEntity exist = tableMapper.selectById(dto.getId());
            if (exist == null) {
                throw BizException.of(ResultCode.PARAM_INVALID, "Decision table does not exist");
            }
            // Modifying other organizations' decision tables is not allowed
            TenantScope.checkVisible(exist.getOrganId() == null ? null : exist.getOrganId().longValue());
            tableMapper.updateById(table);
            // Whole-table rebuild: clear old columns/rows/cells
            columnMapper.delete(new LambdaQueryWrapper<DtColumnEntity>()
                    .eq(DtColumnEntity::getTableId, dto.getId()));
            rowMapper.delete(new LambdaQueryWrapper<DtRowEntity>()
                    .eq(DtRowEntity::getTableId, dto.getId()));
            cellMapper.delete(new LambdaQueryWrapper<DtCellEntity>()
                    .eq(DtCellEntity::getTableId, dto.getId()));
        }
        Integer tableId = table.getId();

        // Columns
        List<Integer> colIds = new ArrayList<>();
        if (dto.getColumns() != null) {
            for (DecisionTableSaveDTO.ColumnDTO c : dto.getColumns()) {
                DtColumnEntity col = new DtColumnEntity();
                col.setTableId(tableId);
                col.setColType(c.getColType());
                col.setFieldCode(c.getFieldCode());
                col.setOperator(c.getOperator());
                col.setTitle(c.getTitle());
                col.setSeq(c.getSeq() == null ? colIds.size() : c.getSeq());
                col.setCreatedTime(LocalDateTime.now());
                col.setUpdatedTime(LocalDateTime.now());
                col.setDeleted(0);
                columnMapper.insert(col);
                colIds.add(col.getId());
            }
        }

        // Rows + cells + expressions
        if (dto.getRows() != null) {
            for (DecisionTableSaveDTO.RowDTO r : dto.getRows()) {
                Map<Integer, String> valueByColIndex = new HashMap<>();
                if (r.getCells() != null) {
                    for (DecisionTableSaveDTO.CellDTO cell : r.getCells()) {
                        if (cell.getColIndex() != null && StringUtils.isNotBlank(cell.getValue())) {
                            valueByColIndex.put(cell.getColIndex(), cell.getValue().trim());
                        }
                    }
                }
                String expression = buildExpression(dto.getColumns(), valueByColIndex);

                DtRowEntity row = new DtRowEntity();
                row.setTableId(tableId);
                row.setRowNo(r.getRowNo());
                row.setResultType(r.getResultType());
                row.setResultValue(r.getResultValue());
                row.setExpression(expression);
                row.setScoreValue(r.getScoreValue());
                row.setEnabled(r.getEnabled() == null ? 1 : r.getEnabled());
                row.setRemark(r.getRemark());
                row.setCreatedTime(LocalDateTime.now());
                row.setUpdatedTime(LocalDateTime.now());
                row.setDeleted(0);
                rowMapper.insert(row);

                if (r.getCells() != null) {
                    for (DecisionTableSaveDTO.CellDTO cell : r.getCells()) {
                        Integer idx = cell.getColIndex();
                        if (idx == null || idx < 0 || idx >= colIds.size()
                                || StringUtils.isBlank(cell.getValue())) {
                            continue;
                        }
                        DtCellEntity ce = new DtCellEntity();
                        ce.setTableId(tableId);
                        ce.setRowId(row.getId());
                        ce.setColId(colIds.get(idx));
                        ce.setCellValue(cell.getValue().trim());
                        ce.setCreatedTime(LocalDateTime.now());
                        ce.setUpdatedTime(LocalDateTime.now());
                        ce.setDeleted(0);
                        cellMapper.insert(ce);
                    }
                }
            }
        }
        return tableId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager", readOnly = true)
    public Map<String, Object> detail(Integer id) {
        DecisionTableEntity table = tableMapper.selectById(id);
        if (table == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Decision table does not exist");
        }
        // Viewing other organizations' decision tables is not allowed
        TenantScope.checkVisible(table.getOrganId() == null ? null : table.getOrganId().longValue());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("table", table);
        out.put("columns", columnMapper.selectList(new LambdaQueryWrapper<DtColumnEntity>()
                .eq(DtColumnEntity::getTableId, id).orderByAsc(DtColumnEntity::getSeq)));

        List<DtRowEntity> rows = rowMapper.selectList(new LambdaQueryWrapper<DtRowEntity>()
                .eq(DtRowEntity::getTableId, id).orderByAsc(DtRowEntity::getRowNo));
        List<Map<String, Object>> rowMaps = new ArrayList<>();
        for (DtRowEntity r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("row", r);
            List<DtCellEntity> cells = cellMapper.selectList(new LambdaQueryWrapper<DtCellEntity>()
                    .eq(DtCellEntity::getRowId, r.getId()));
            m.put("cells", cells);
            rowMaps.add(m);
        }
        out.put("rows", rowMaps);
        return out;
    }

    @Override
    public List<Map<String, Object>> list(String keyword) {
        LambdaQueryWrapper<DecisionTableEntity> qw = new LambdaQueryWrapper<>();
        qw.ne(DecisionTableEntity::getDeleted, 1);
        // Organization users only see decision tables of [0 platform public, own organization]
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            qw.in(DecisionTableEntity::getOrganId, organs);
        }
        if (StringUtils.isNotBlank(keyword)) {
            qw.and(w -> w.like(DecisionTableEntity::getName, keyword)
                    .or().like(DecisionTableEntity::getCode, keyword));
        }
        qw.orderByDesc(DecisionTableEntity::getUpdatedTime);
        List<Map<String, Object>> out = new ArrayList<>();
        for (DecisionTableEntity t : tableMapper.selectList(qw)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("code", t.getCode());
            m.put("name", t.getName());
            m.put("hitPolicy", t.getHitPolicy());
            m.put("status", t.getStatus());
            m.put("updatedTime", t.getUpdatedTime());
            out.add(m);
        }
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void delete(Integer id) {
        DecisionTableEntity t = tableMapper.selectById(id);
        if (t == null) {
            return;
        }
        // Deleting other organizations' decision tables is not allowed
        TenantScope.checkVisible(t.getOrganId() == null ? null : t.getOrganId().longValue());
        t.setStatus(-1);
        t.setDeleted(1);
        t.setUpdatedTime(LocalDateTime.now());
        tableMapper.updateById(t);
    }

    /** Save-time validation */
    private void validate(DecisionTableSaveDTO dto) {
        if (StringUtils.isBlank(dto.getCode())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Decision table code must not be empty");
        }
        if (StringUtils.isBlank(dto.getName())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Decision table name must not be empty");
        }
        if (dto.getRows() != null) {
            for (DecisionTableSaveDTO.RowDTO r : dto.getRows()) {
                if (StringUtils.isBlank(r.getResultValue()) && StringUtils.isBlank(r.getResultType())) {
                    throw BizException.of(ResultCode.PARAM_INVALID,
                            "Row " + r.getRowNo() + " is missing an output value or conclusion");
                }
            }
        }
    }

    /**
     * Generate the readable expression of the row conditions (consistent with the engine-side ConditionExpressions
     * rendering convention: numbers unquoted, strings single-quoted, joined by AND).
     */
    private String buildExpression(List<DecisionTableSaveDTO.ColumnDTO> columns,
                                   Map<Integer, String> valueByColIndex) {
        if (columns == null || columns.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        for (DecisionTableSaveDTO.ColumnDTO c : columns) {
            String value = valueByColIndex.get(idx++);
            if (value == null || c.getFieldCode() == null) {
                continue;
            }
            String op = StringUtils.defaultIfBlank(c.getOperator(), "==");
            if (sb.length() > 0) {
                sb.append(" && ");
            }
            sb.append(c.getFieldCode()).append(' ');
            if ("between".equalsIgnoreCase(op)) {
                String[] parts = value.split(",");
                sb.append(">= ").append(literal(parts.length > 0 ? parts[0].trim() : ""))
                        .append(" && ").append(c.getFieldCode())
                        .append(" <= ").append(literal(parts.length > 1 ? parts[1].trim() : ""));
            } else {
                sb.append(op).append(' ').append(literal(value));
            }
        }
        return sb.length() == 0 ? null : "(" + sb + ")";
    }

    private String literal(String v) {
        if (v == null) {
            return "null";
        }
        return v.matches("-?\\d+(\\.\\d+)?") ? v : "'" + v + "'";
    }
}
