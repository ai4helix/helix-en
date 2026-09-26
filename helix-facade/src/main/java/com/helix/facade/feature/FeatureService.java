package com.helix.facade.feature;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Data Center (feature service) client.
 *
 * <p>Addressed by service name {@code helix-feature} via nacos service discovery instead of a
 * hard-coded address; on cluster deployment the nacos registry balances the load automatically.</p>
 */
@FeignClient("helix-feature")
public interface FeatureService {

    /**
     * Fetches data by engine feature names.
     */
    @PostMapping("/dataCenter/feature")
    DataCenterRsp callDataCenter(DataCenterReq dataCenterReq);

    /**
     * Batch-imports user metric data (retained by the Data Center).
     * Request body: {batchNo, rows: [{userKey, fields:{...}}]}; the response envelope's data is the number of records written.
     */
    @PostMapping("/dataCenter/userData/import")
    Map<String, Object> importUserData(@RequestBody Map<String, Object> req);

    /**
     * Queries metric data by a set of user IDs.
     * Request body: {userKeys: [...]}; the response envelope's data is user_key → (field_en → value).
     */
    @PostMapping("/dataCenter/userData/query")
    Map<String, Object> queryUserData(@RequestBody Map<String, Object> req);
}
