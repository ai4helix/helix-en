package com.helix.console.system.service;

import com.helix.console.system.dto.LoginDTO;
import com.helix.console.system.dto.LoginResultVO;
import com.helix.console.system.security.LoginUser;

public interface AuthService {

    LoginResultVO login(LoginDTO dto, String clientIp);

    LoginUser loadLoginUser(Long userId);

    void changePassword(Long userId, String oldPassword, String newPassword);

    void resetPassword(Long userId, String newPassword);
}
