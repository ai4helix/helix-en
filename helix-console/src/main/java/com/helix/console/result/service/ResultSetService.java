package com.helix.console.result.service;

import com.helix.console.common.PageResult;
import com.helix.console.engine.dto.ExecutionResult;
import com.helix.console.result.dto.BatchTestDTO;
import com.helix.console.result.dto.BatchTestResultVO;
import com.helix.console.result.dto.ResultQuery;
import com.helix.console.result.dto.ResultSetVO;

import java.util.Map;

public interface ResultSetService {

    void persist(ExecutionResult result, Map<String, Object> input, Integer type, String pid, String batchNo);

    PageResult<ResultSetVO> page(ResultQuery query, long pageNo, long pageSize);

    ResultSetVO detail(Integer id);

    ResultSetVO detailByTraceId(String traceId);

    BatchTestResultVO batchTest(BatchTestDTO dto);

    void removeBatch(String batchNo);
}
