package com.helix.console.engine.service;

import com.helix.console.engine.dto.DecisionTableSaveDTO;

import java.util.List;
import java.util.Map;

/**
 * Decision table management service (v3).
 */
public interface DecisionTableService {

    /** Save (create/edit, rebuilds columns/rows/cells wholesale), returns the decision table id */
    Integer save(DecisionTableSaveDTO dto);

    /** Detail: main table + columns + rows (including cell values) */
    Map<String, Object> detail(Integer id);

    /** List (ordered by update time descending) */
    List<Map<String, Object>> list(String keyword);

    /** Delete (logical delete of the whole table) */
    void delete(Integer id);
}
