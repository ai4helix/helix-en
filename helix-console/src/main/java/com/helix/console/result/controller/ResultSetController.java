package com.helix.console.result.controller;

import com.helix.console.common.PageResult;
import com.helix.console.common.Result;
import com.helix.console.result.dto.BatchTestDTO;
import com.helix.console.result.dto.BatchTestResultVO;
import com.helix.console.result.dto.ResultQuery;
import com.helix.console.result.dto.ResultSetVO;
import com.helix.console.result.service.ResultSetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * Result set and batch test APIs.
 */
@Tag(name = "Result Set & Batch Test")
@RestController
@RequestMapping("/api/result")
@RequiredArgsConstructor
@Validated
public class ResultSetController {

    private final ResultSetService resultSetService;

    @Operation(summary = "Result set page query")
    @PostMapping("/page")
    public Result<PageResult<ResultSetVO>> page(
            @RequestBody(required = false) ResultQuery query,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(resultSetService.page(query, pageNo, pageSize));
    }

    @Operation(summary = "Result set detail (with node traces)")
    @GetMapping("/{id}")
    public Result<ResultSetVO> detail(@PathVariable Integer id) {
        return Result.ok(resultSetService.detail(id));
    }

    @Operation(summary = "Query result by trace id")
    @GetMapping("/trace/{traceId}")
    public Result<ResultSetVO> detailByTrace(@PathVariable String traceId) {
        return Result.ok(resultSetService.detailByTraceId(traceId));
    }

    @Operation(summary = "Batch test")
    @PostMapping("/batch")
    public Result<BatchTestResultVO> batchTest(@Valid @RequestBody BatchTestDTO dto) {
        return Result.ok(resultSetService.batchTest(dto));
    }

    @Operation(summary = "Delete the given batch")
    @DeleteMapping("/batch/{batchNo}")
    public Result<Void> removeBatch(@PathVariable String batchNo) {
        resultSetService.removeBatch(batchNo);
        return Result.ok();
    }
}
