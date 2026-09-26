package com.helix.console.datamanage.service;

import com.helix.console.datamanage.dto.FieldDetailVO;
import com.helix.console.datamanage.dto.FieldLineageDTO;
import com.helix.console.datamanage.dto.LineageGraphDTO;

public interface FieldLineageService {

    FieldLineageDTO lineage(Integer fieldId);

    FieldDetailVO detail(Integer fieldId);

    LineageGraphDTO graph(Integer versionId);
}
