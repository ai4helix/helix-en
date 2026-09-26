package com.helix.console.engine.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.helix.console.common.BizException;
import com.helix.console.common.ResultCode;
import com.helix.console.engine.entity.Engine;
import com.helix.console.engine.entity.EngineNode;
import com.helix.console.engine.entity.EngineVersion;
import com.helix.console.engine.enums.NodeType;
import com.helix.console.engine.mapper.EngineMapper;
import com.helix.console.engine.mapper.EngineNodeMapper;
import com.helix.console.engine.mapper.EngineVersionMapper;
import com.helix.console.engine.service.EngineService;
import com.helix.console.system.security.TenantScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EngineServiceImpl implements EngineService {

    private final EngineMapper engineMapper;
    private final EngineVersionMapper engineVersionMapper;
    private final EngineNodeMapper engineNodeMapper;

    @Override
    public List<Engine> listEngines(Integer organId) {
        LambdaQueryWrapper<Engine> qw = new LambdaQueryWrapper<>();
        qw.eq(Engine::getStatus, 1);
        // Organization users only see [0 platform public, own organization]; admin users can filter by the organId parameter
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            qw.in(Engine::getOrganId, organs);
        } else if (organId != null) {
            qw.eq(Engine::getOrganId, organId);
        }
        qw.orderByDesc(Engine::getUpdatedTime).orderByDesc(Engine::getId);
        return engineMapper.selectList(qw);
    }

    @Override
    public List<EngineVersion> listVersions(Integer engineId) {
        requireVisibleEngine(engineId);
        LambdaQueryWrapper<EngineVersion> qw = new LambdaQueryWrapper<>();
        qw.eq(EngineVersion::getEngineId, engineId)
                .in(EngineVersion::getStatus, 0, 1)
                .orderByDesc(EngineVersion::getBootState)
                .orderByDesc(EngineVersion::getVersion)
                .orderByDesc(EngineVersion::getSubVersion);
        return engineVersionMapper.selectList(qw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public Integer createEngine(Engine engine) {
        if (StringUtils.isBlank(engine.getCode())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Engine code must not be empty");
        }
        Long exists = engineMapper.selectCount(new LambdaQueryWrapper<Engine>()
                .eq(Engine::getCode, engine.getCode()));
        if (exists != null && exists > 0) {
            throw BizException.of(ResultCode.DATA_DUPLICATE, "Engine code already exists");
        }
        engine.setStatus(1);
        // Admin users default to platform public (0); organization users are forced to their own organization
        engine.setOrganId(TenantScope.writeOrgan(engine.getOrganId()));
        engineMapper.insert(engine);
        return engine.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public Integer createVersion(Integer engineId) {
        Engine engine = requireVisibleEngine(engineId);
        List<EngineVersion> versions = listVersions(engineId);
        int maxVersion = versions.stream()
                .map(EngineVersion::getVersion)
                .filter(v -> v != null)
                .max(Integer::compareTo)
                .orElse(0);

        EngineVersion draft = new EngineVersion();
        draft.setEngineId(engineId);
        draft.setVersion(maxVersion + 1);
        draft.setSubVersion(1);
        draft.setBootState(0);
        draft.setStatus(1);
        draft.setLayout(0);
        // created_time is one of the configuration-change signals for the engine's self-healing reconciliation
        // (this table has no updated_time), so it must not be left NULL
        draft.setCreatedTime(java.time.LocalDateTime.now());
        engineVersionMapper.insert(draft);

        // New versions automatically get a start node; the frontend canvas needs it as the entry point
        EngineNode start = new EngineNode();
        start.setVersionId(draft.getId());
        start.setNodeName("Start");
        start.setNodeCode("start");
        start.setNodeType(NodeType.START.getCode());
        start.setNodeOrder(0);
        start.setNextNodes("");
        engineNodeMapper.insert(start);
        return draft.getId();
    }

    /** Validate that the engine is visible to the current user (organization users limited to [0 platform public, own organization]) */
    private Engine requireVisibleEngine(Integer engineId) {
        Engine engine = engineMapper.selectById(engineId);
        if (engine == null) {
            throw BizException.of(ResultCode.ENGINE_NOT_FOUND);
        }
        TenantScope.checkVisible(engine.getOrganId() == null ? null : engine.getOrganId().longValue());
        return engine;
    }
}
