package com.helix.console.batch.client;

import com.helix.console.common.BizException;
import com.helix.console.common.ResultCode;
import com.helix.facade.feature.FeatureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Data Center (helix-feature) user indicator data client (facade contract adapter).
 *
 * <p>Maps to the v5 endpoints {@code /dataCenter/userData/import} and {@code /dataCenter/userData/query}:
 * user indicator data imported per tenant is retained in the Data Center, while batch runs still use
 * the local snapshot ({@code t_indicator_batch_item.data_json}); Data Center data is reserved for
 * later real-time decisions / reuse by other services.</p>
 *
 * <p>Transport goes through {@link FeatureService} from helix-facade (Feign, nacos service discovery
 * + Ribbon load balancing); this class remains an adapter for envelope code checks and exception
 * translation.</p>
 */
@Slf4j
@Component
public class DataCenterClient {

    private final FeatureService featureService;

    public DataCenterClient(FeatureService featureService) {
        this.featureService = featureService;
    }

    /**
     * Batch import user indicator data.
     *
     * @param batchNo batch no (batch run task id)
     * @param rows    each row: userKey + fields(indicator name -> value)
     * @return number of written user-indicator pairs
     */
    public int importUserData(String batchNo, List<Map<String, Object>> rows) {
        Map<String, Object> body = new HashMap<>();
        body.put("batchNo", batchNo);
        body.put("rows", rows);
        Map<String, Object> envelope = call(() -> featureService.importUserData(body), "User indicator data import");
        Object data = envelope.get("data");
        return data instanceof Number ? ((Number) data).intValue() : 0;
    }

    /**
     * Query indicator data by user keys: user_key -> (field_en -> value).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Object>> queryUserData(Collection<String> userKeys) {
        Map<String, Object> body = new HashMap<>();
        body.put("userKeys", userKeys);
        Map<String, Object> envelope = call(() -> featureService.queryUserData(body), "User indicator data query");
        Object data = envelope.get("data");
        return data instanceof Map ? (Map<String, Map<String, Object>>) data : new HashMap<>();
    }

    /** Send the request and parse the unified envelope: code=0 returns the raw envelope, otherwise throws BizException */
    private Map<String, Object> call(Supplier<Map<String, Object>> invocation, String action) {
        try {
            Map<String, Object> envelope = invocation.get();
            if (envelope == null) {
                throw BizException.of(ResultCode.SYSTEM_ERROR, "Data Center did not respond: " + action);
            }
            Object code = envelope.get("code");
            if (!(code instanceof Number) || ((Number) code).intValue() != 0) {
                throw BizException.of(ResultCode.SYSTEM_ERROR,
                        action + " failed: " + String.valueOf(envelope.getOrDefault("message", "Unknown error")));
            }
            return envelope;
        } catch (feign.FeignException e) {
            log.error("Failed to call Data Center, action={}", action, e);
            throw BizException.of(ResultCode.SYSTEM_ERROR,
                    "Cannot connect to Data Center (helix-feature): " + e.getMessage());
        }
    }
}
