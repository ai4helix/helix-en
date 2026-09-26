package com.helix.console.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helix.console.common.BizException;
import com.helix.console.common.PageResult;
import com.helix.console.common.ResultCode;
import com.helix.console.system.dto.FeedbackAdminVO;
import com.helix.console.system.dto.FeedbackImage;
import com.helix.console.system.dto.FeedbackSubmitReq;
import com.helix.console.system.entity.Feedback;
import com.helix.console.system.entity.SysOrganization;
import com.helix.console.system.mapper.FeedbackMapper;
import com.helix.console.system.mapper.SysOrganizationMapper;
import com.helix.console.system.security.LoginUser;
import com.helix.console.system.security.TenantScope;
import com.helix.console.system.security.UserContext;
import com.helix.console.system.service.FeedbackService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * User feedback service implementation.
 *
 * <p>Image storage: the server generates {@code UUID (32 hex chars) + whitelisted
 * extension} file names saved under {@code helix.feedback.upload-dir} (default
 * {@code uploads/feedback/} inside the project; deployments override it via env
 * var, same convention as {@code logs/}); the table stores only a JSON array of
 * file names; files are served back through {@code GET /api/feedback/file/{name}}
 * (login required); the file-name regex whitelist prevents path traversal.</p>
 */
@Slf4j
@Service
public class FeedbackServiceImpl implements FeedbackService {

    /** Type: problem */
    public static final int CATEGORY_PROBLEM = 1;
    /** Type: suggestion */
    public static final int CATEGORY_SUGGESTION = 2;
    /** Pending */
    public static final int STATUS_PENDING = 0;
    /** Handled */
    public static final int STATUS_HANDLED = 1;

    private static final int CONTENT_MAX = 2000;
    private static final int REPLY_MAX = 1000;
    private static final int MAX_IMAGES = 4;
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    /** Server-generated file name whitelist: 32 hex chars + extension (also the only gate against path traversal) */
    private static final Pattern FILENAME_PATTERN =
            Pattern.compile("^[0-9a-f]{32}\\.(jpg|jpeg|png|gif|webp|bmp)$");

    private static final Set<String> ALLOWED_EXTS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp", "bmp"));

    private static final Map<String, String> CONTENT_TYPES;

    static {
        Map<String, String> m = new HashMap<>();
        m.put("jpg", "image/jpeg");
        m.put("jpeg", "image/jpeg");
        m.put("png", "image/png");
        m.put("gif", "image/gif");
        m.put("webp", "image/webp");
        m.put("bmp", "image/bmp");
        CONTENT_TYPES = Collections.unmodifiableMap(m);
    }

    private final FeedbackMapper feedbackMapper;
    private final SysOrganizationMapper organizationMapper;
    private final ObjectMapper objectMapper;

    @Value("${helix.feedback.upload-dir:uploads/feedback}")
    private String uploadDir;

    public FeedbackServiceImpl(FeedbackMapper feedbackMapper,
                               SysOrganizationMapper organizationMapper,
                               ObjectMapper objectMapper) {
        this.feedbackMapper = feedbackMapper;
        this.organizationMapper = organizationMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Long submit(FeedbackSubmitReq req) {
        if (req == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Feedback content must not be blank");
        }
        Integer category = req.getCategory();
        if (category == null || (category != CATEGORY_PROBLEM && category != CATEGORY_SUGGESTION)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Feedback type must be Problem or Suggestion");
        }
        String content = StringUtils.trimToEmpty(req.getContent());
        if (content.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Feedback content must not be blank");
        }
        if (content.length() > CONTENT_MAX) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Feedback content must not exceed " + CONTENT_MAX + " characters");
        }

        List<String> images = normalizeImages(req.getImages());

        LoginUser user = requireUser();
        Feedback f = new Feedback();
        f.setOrganId(user.getOrganId() == null ? Integer.valueOf(0) : user.getOrganId().intValue());
        f.setUserId(user.getUserId());
        f.setUsername(user.getAccount());
        f.setCategory(category);
        f.setContent(content);
        try {
            f.setImages(images.isEmpty() ? null : objectMapper.writeValueAsString(images));
        } catch (Exception e) {
            throw BizException.of(ResultCode.SYSTEM_ERROR, "Failed to serialize screenshot info");
        }
        f.setStatus(STATUS_PENDING);
        f.setCreatedTime(LocalDateTime.now());
        feedbackMapper.insert(f);
        log.info("User submitted feedback id={} user={} organ={} category={} images={}",
                f.getId(), user.getAccount(), user.getOrganId(), category, images.size());
        return f.getId();
    }

    @Override
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Please choose an image to upload");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw BizException.of(ResultCode.PARAM_INVALID, "A single image must not exceed 5MB");
        }
        String original = StringUtils.trimToEmpty(file.getOriginalFilename());
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!ALLOWED_EXTS.contains(ext)) {
            throw BizException.of(ResultCode.PARAM_INVALID,
                    "Only image formats are supported: jpg / jpeg / png / gif / webp / bmp");
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Paths.get(uploadDir);
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename).toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save feedback screenshot filename={}", filename, e);
            throw BizException.of(ResultCode.SYSTEM_ERROR, "Failed to save the image, please try again later");
        }
        return filename;
    }

    @Override
    public FeedbackImage getImage(String filename) {
        if (StringUtils.isBlank(filename) || !FILENAME_PATTERN.matcher(filename).matches()) {
            throw BizException.of(ResultCode.NOT_FOUND, "Image does not exist");
        }
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path path = base.resolve(filename).toAbsolutePath().normalize();
        if (!path.startsWith(base) || !Files.exists(path)) {
            throw BizException.of(ResultCode.NOT_FOUND, "Image does not exist");
        }
        try {
            String ext = filename.substring(filename.lastIndexOf('.') + 1);
            return new FeedbackImage(Files.readAllBytes(path),
                    CONTENT_TYPES.getOrDefault(ext, "application/octet-stream"));
        } catch (IOException e) {
            log.error("Failed to read feedback screenshot filename={}", filename, e);
            throw BizException.of(ResultCode.SYSTEM_ERROR, "Failed to read the image");
        }
    }

    @Override
    public PageResult<FeedbackAdminVO> page(Integer status, String keyword, long pageNo, long pageSize) {
        requirePlatformAdmin();
        QueryWrapper<Feedback> qw = new QueryWrapper<>();
        if (status != null) {
            qw.eq("status", status);
        }
        if (StringUtils.isNotBlank(keyword)) {
            qw.and(w -> w.like("username", keyword.trim()).or().like("content", keyword.trim()));
        }
        qw.orderByDesc("created_time");
        Page<Feedback> page = feedbackMapper.selectPage(new Page<>(pageNo, pageSize), qw);

        // Batch fill organization names (organization entity primary key is Long)
        Set<Long> organIds = page.getRecords().stream()
                .map(f -> f.getOrganId() == null ? null : f.getOrganId().longValue())
                .filter(id -> id != null && id != 0L)
                .collect(Collectors.toSet());
        Map<Long, String> organNames = new HashMap<>();
        if (!organIds.isEmpty()) {
            for (SysOrganization o : organizationMapper.selectBatchIds(organIds)) {
                organNames.put(o.getId(), o.getName());
            }
        }

        List<FeedbackAdminVO> vos = page.getRecords().stream()
                .map(f -> toVO(f, organNames))
                .collect(Collectors.toList());
        return PageResult.of(vos, page.getTotal(), pageNo, pageSize);
    }

    @Override
    public void handle(Long id, String reply) {
        requirePlatformAdmin();
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Feedback id must not be blank");
        }
        String replyText = StringUtils.trimToEmpty(reply);
        if (replyText.length() > REPLY_MAX) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Handling reply must not exceed " + REPLY_MAX + " characters");
        }
        Feedback f = feedbackMapper.selectById(id);
        if (f == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "Feedback does not exist");
        }
        f.setStatus(STATUS_HANDLED);
        f.setReply(StringUtils.trimToNull(replyText));
        f.setHandledBy(requireUser().getAccount());
        f.setHandledTime(LocalDateTime.now());
        feedbackMapper.updateById(f);
        log.info("Feedback handled id={} by={}", id, f.getHandledBy());
    }

    // ===== Internal utilities =====

    private FeedbackAdminVO toVO(Feedback f, Map<Long, String> organNames) {
        FeedbackAdminVO vo = new FeedbackAdminVO();
        vo.setId(f.getId());
        vo.setOrganId(f.getOrganId());
        vo.setOrganName(f.getOrganId() == null ? null : organNames.get(f.getOrganId().longValue()));
        vo.setUserId(f.getUserId());
        vo.setUsername(f.getUsername());
        vo.setCategory(f.getCategory());
        vo.setContent(f.getContent());
        vo.setImages(parseImages(f.getImages()));
        vo.setStatus(f.getStatus());
        vo.setReply(f.getReply());
        vo.setHandledBy(f.getHandledBy());
        vo.setHandledTime(f.getHandledTime());
        vo.setCreatedTime(f.getCreatedTime());
        return vo;
    }

    /** Validate and normalize the image file name list: dedupe, per-item whitelist check, max 4 */
    private List<String> normalizeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return new ArrayList<>();
        }
        if (images.size() > MAX_IMAGES) {
            throw BizException.of(ResultCode.PARAM_INVALID, "At most " + MAX_IMAGES + " screenshots can be uploaded");
        }
        List<String> result = new ArrayList<>();
        for (String name : images) {
            String n = StringUtils.trimToEmpty(name);
            if (n.isEmpty()) {
                continue;
            }
            if (!FILENAME_PATTERN.matcher(n).matches()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "Invalid screenshot file name: " + n);
            }
            if (!result.contains(n)) {
                result.add(n);
            }
        }
        if (result.size() > MAX_IMAGES) {
            throw BizException.of(ResultCode.PARAM_INVALID, "At most " + MAX_IMAGES + " screenshots can be uploaded");
        }
        return result;
    }

    private List<String> parseImages(String imagesJson) {
        if (StringUtils.isBlank(imagesJson)) {
            return new ArrayList<>();
        }
        try {
            List<String> list = objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {
            });
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            log.warn("Failed to parse feedback screenshot JSON: {}", imagesJson);
            return new ArrayList<>();
        }
    }

    private LoginUser requireUser() {
        LoginUser u = UserContext.get();
        if (u == null || u.getUserId() == null) {
            throw BizException.of(ResultCode.UNAUTHORIZED, "Please log in first");
        }
        return u;
    }

    private void requirePlatformAdmin() {
        if (!TenantScope.isAdmin()) {
            throw BizException.of(ResultCode.FORBIDDEN, "Only platform administrators can view and handle feedback");
        }
    }
}
