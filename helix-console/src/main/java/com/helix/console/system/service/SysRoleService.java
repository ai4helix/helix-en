package com.helix.console.system.service;

import com.helix.console.common.PageResult;
import com.helix.console.system.entity.SysRole;

import java.util.List;
import java.util.Map;

public interface SysRoleService {

    PageResult<SysRole> page(String keyword, Long organId, long pageNo, long pageSize);

    List<SysRole> listAll();

    Map<String, Object> detail(Long roleId);

    Long create(SysRole role, List<Long> resourceIds);

    void update(SysRole role, List<Long> resourceIds);

    void remove(List<Long> roleIds);

    void changeStatus(List<Long> roleIds, Integer status);

    void bindResources(Long roleId, List<Long> resourceIds);
}
