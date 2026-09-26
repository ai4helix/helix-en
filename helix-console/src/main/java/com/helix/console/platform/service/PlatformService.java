package com.helix.console.platform.service;

import com.helix.console.platform.dto.EngineStatVO;
import com.helix.console.platform.dto.RoleStatVO;
import com.helix.console.platform.dto.TenantStatVO;

import java.util.List;
import java.util.Map;

public interface PlatformService {

    Map<String, Object> overview();

    List<TenantStatVO> tenantStats();

    List<RoleStatVO> roleStats();

    List<EngineStatVO> engineStats();
}
