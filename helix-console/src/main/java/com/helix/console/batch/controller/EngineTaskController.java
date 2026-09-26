package com.helix.console.batch.controller;

import com.helix.console.batch.entity.EngineTask;
import com.helix.console.batch.service.EngineTaskService;
import com.helix.console.common.PageResult;
import com.helix.console.common.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/batch/engine-task")
public class EngineTaskController {

    @Resource
    private EngineTaskService engineTaskService;

    @PostMapping("/import")
    public Result<Map<String, Object>> importTasks(@RequestParam("file") MultipartFile file) {
        return Result.ok(engineTaskService.importTasks(file));
    }

    @GetMapping("/template")
    public void template(HttpServletResponse response) {
        engineTaskService.downloadTemplate(response);
    }

    @GetMapping("/page")
    public Result<PageResult<EngineTask>> page(@RequestParam(defaultValue = "1") long pageNo,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Integer status) {
        return Result.ok(engineTaskService.pageTasks(pageNo, pageSize, keyword, status));
    }

    @PostMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        engineTaskService.changeStatus(id, status);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        engineTaskService.deleteTask(id);
        return Result.ok();
    }
}
