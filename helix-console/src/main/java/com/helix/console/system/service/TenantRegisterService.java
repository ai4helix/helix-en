package com.helix.console.system.service;

import com.helix.console.system.dto.RegisterDTO;

import java.util.Map;

public interface TenantRegisterService {

    Map<String, Object> sendCaptcha(String phone);

    Map<String, Object> register(RegisterDTO dto);
}
