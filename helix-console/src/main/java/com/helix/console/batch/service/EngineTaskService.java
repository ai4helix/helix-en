package com.helix.console.batch.service;

import com.helix.console.batch.entity.EngineTask;
import com.helix.console.common.PageResult;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

public interface EngineTaskService {

    Map<String, Object> importTasks(MultipartFile file);

    PageResult<EngineTask> pageTasks(long pageNo, long pageSize, String keyword, Integer status);

    void downloadTemplate(HttpServletResponse response);

    void changeStatus(Long id, Integer status);

    void deleteTask(Long id);
}
