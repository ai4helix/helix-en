package com.helix.console.system.service;

import com.helix.console.common.PageResult;
import com.helix.console.system.dto.UserSaveDTO;
import com.helix.console.system.dto.UserVO;

import java.util.List;

public interface SysUserService {

    PageResult<UserVO> page(String keyword, Long organId, Integer status, long pageNo, long pageSize);

    UserVO detail(Long userId);

    Long create(UserSaveDTO dto);

    void update(UserSaveDTO dto);

    void remove(List<Long> userIds);

    void changeStatus(List<Long> userIds, Integer status);

    void bindRoles(Long userId, List<Long> roleIds);
}
