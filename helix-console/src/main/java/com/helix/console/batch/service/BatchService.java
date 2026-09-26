package com.helix.console.batch.service;

import com.helix.console.batch.entity.IndicatorBatch;
import com.helix.console.batch.entity.IndicatorBatchItem;
import com.helix.console.common.PageResult;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

public interface BatchService {

    Map<String, Object> importFields(MultipartFile file);

    Map<String, Object> importData(String name, String engineCode, String keyField, MultipartFile file);

    void run(Long batchId);

    IndicatorBatch detail(Long batchId);

    PageResult<IndicatorBatch> pageBatches(long pageNo, long pageSize);

    PageResult<IndicatorBatchItem> pageItems(Long batchId, long pageNo, long pageSize,
                                             Integer status, String keyword);

    void download(Long batchId, HttpServletResponse response);

    void downloadFieldTemplate(HttpServletResponse response);

    void downloadDataTemplate(String engineCode, String keyField, boolean example,
                              HttpServletResponse response);
}
