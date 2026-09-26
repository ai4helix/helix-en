package com.helix.console.system.controller;

import com.helix.console.common.PageResult;
import com.helix.console.common.Result;
import com.helix.console.system.dto.FeedbackAdminVO;
import com.helix.console.system.dto.FeedbackImage;
import com.helix.console.system.dto.FeedbackSubmitReq;
import com.helix.console.system.service.FeedbackService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public Result<Long> submit(@RequestBody FeedbackSubmitReq req) {
        return Result.ok(feedbackService.submit(req));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadImage(@RequestPart("file") MultipartFile file) {
        return Result.ok(feedbackService.uploadImage(file));
    }

    @GetMapping("/file/{filename:.+}")
    public ResponseEntity<byte[]> file(@PathVariable("filename") String filename) {
        FeedbackImage image = feedbackService.getImage(filename);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .body(image.getContent());
    }

    @GetMapping("/page")
    public Result<PageResult<FeedbackAdminVO>> page(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        return Result.ok(feedbackService.page(status, keyword, pageNo, pageSize));
    }

    @PostMapping("/{id}/handle")
    public Result<Map<String, Object>> handle(@PathVariable("id") Long id,
                                              @RequestBody Map<String, String> body) {
        feedbackService.handle(id, body == null ? null : body.get("reply"));
        return Result.ok(Collections.singletonMap("handled", true));
    }
}
