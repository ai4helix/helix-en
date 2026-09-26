package com.helix.console.system.service;

import com.helix.console.system.dto.TreeNodeVO;
import com.helix.console.system.entity.SysResource;

import java.util.List;

public interface SysResourceService {

    List<TreeNodeVO> tree();

    List<TreeNodeVO> currentUserTree();

    SysResource detail(Long resourceId);

    Long create(SysResource resource);

    void update(SysResource resource);

    void remove(Long resourceId);
}
