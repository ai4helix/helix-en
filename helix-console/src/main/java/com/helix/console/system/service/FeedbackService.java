package com.helix.console.system.service;

import com.helix.console.common.PageResult;
import com.helix.console.system.dto.FeedbackAdminVO;
import com.helix.console.system.dto.FeedbackImage;
import com.helix.console.system.dto.FeedbackSubmitReq;
import org.springframework.web.multipart.MultipartFile;

public interface FeedbackService {

    Long submit(FeedbackSubmitReq req);

    String uploadImage(MultipartFile file);

    FeedbackImage getImage(String filename);

    PageResult<FeedbackAdminVO> page(Integer status, String keyword, long pageNo, long pageSize);

    void handle(Long id, String reply);
}
