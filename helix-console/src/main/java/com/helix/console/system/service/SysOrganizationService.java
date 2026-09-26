package com.helix.console.system.service;

import com.helix.console.system.entity.SysOrganization;

import java.util.List;

public interface SysOrganizationService {

    List<SysOrganization> listAll();

    SysOrganization detail(Long organId);

    Long create(SysOrganization org);

    void update(SysOrganization org);

    void remove(Long organId);

    void changeStatus(Long organId, Integer status);
}
