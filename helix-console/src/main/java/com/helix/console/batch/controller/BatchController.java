package com.helix.console.batch.controller;

import com.helix.console.batch.entity.IndicatorBatch;
import com.helix.console.batch.entity.IndicatorBatchItem;
import com.helix.console.batch.service.BatchService;
import com.helix.console.common.PageResult;
import com.helix.console.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@RestController
@RequestMapping("/api/batch")
public class BatchController {

    @Resource
    private BatchService batchService;

    @PostMapping("/field/import")
    public Result<Map<String, Object>> importFields(@RequestParam("file") MultipartFile file) {
        return Result.ok(batchService.importFields(file));
    }

    @PostMapping("/data/import")
    public Result<Map<String, Object>> importData(@RequestParam("name") String name,
                                                  @RequestParam("engineCode") String engineCode,
                                                  @RequestParam(value = "keyField", defaultValue = "uid") String keyField,
                                                  @RequestParam("file") MultipartFile file) {
        return Result.ok(batchService.importData(name, engineCode, keyField, file));
    }

    @PostMapping("/{id}/run")
    public Result<Void> run(@PathVariable Long id) {
        batchService.run(id);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<IndicatorBatch> detail(@PathVariable Long id) {
        return Result.ok(batchService.detail(id));
    }

    @GetMapping("/page")
    public Result<PageResult<IndicatorBatch>> pageBatches(@RequestParam(defaultValue = "1") long pageNo,
                                                          @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(batchService.pageBatches(pageNo, pageSize));
    }

    @GetMapping("/{id}/items")
    public Result<PageResult<IndicatorBatchItem>> pageItems(@PathVariable Long id,
                                                            @RequestParam(defaultValue = "1") long pageNo,
                                                            @RequestParam(defaultValue = "20") long pageSize,
                                                            @RequestParam(required = false) Integer status,
                                                            @RequestParam(required = false) String keyword) {
        return Result.ok(batchService.pageItems(id, pageNo, pageSize, status, keyword));
    }

    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) {
        batchService.download(id, response);
    }

    @GetMapping("/template/field")
    public void fieldTemplate(HttpServletResponse response) {
        batchService.downloadFieldTemplate(response);
    }

    @GetMapping("/template/data")
    public void dataTemplate(@RequestParam(required = false) String engineCode,
                             @RequestParam(defaultValue = "uid") String keyField,
                             @RequestParam(defaultValue = "true") boolean example,
                             HttpServletResponse response) {
        batchService.downloadDataTemplate(engineCode, keyField, example, response);
    }

}
