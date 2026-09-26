package com.helix.console.batch.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.helix.console.batch.entity.IndicatorBatch;
import com.helix.console.batch.entity.IndicatorBatchItem;
import com.helix.console.batch.mapper.IndicatorBatchItemMapper;
import com.helix.console.batch.mapper.IndicatorBatchMapper;
import com.helix.console.common.BizException;
import com.helix.console.engine.client.EngineClient;
import com.helix.console.engine.mapper.EngineMapper;
import com.helix.console.system.security.LoginUser;
import com.helix.console.system.security.UserContext;
import com.helix.console.batch.client.DataCenterClient;
import com.helix.console.datamanage.mapper.FieldMapper;
import com.helix.console.knowledge.mapper.KnowledgeTreeMapper;
import com.helix.console.system.mapper.SysRelationMapper;
import com.helix.console.system.mapper.SysRoleMapper;
import com.helix.console.system.mapper.SysUserMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Batch run start (run) claim semantics unit tests.
 *
 * <p>Covers the convention: starting a batch run must be a "conditional-update
 * claim" (UPDATE ... WHERE status&lt;&gt;1), judged by affected rows -- the original
 * check-then-act implementation (query first, then update unconditionally) had a
 * concurrent double-run race: two concurrent run() calls both passed the check and
 * consumed the same items twice.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class BatchServiceRunClaimTest {

    @Mock
    private IndicatorBatchMapper batchMapper;
    @Mock
    private IndicatorBatchItemMapper itemMapper;
    @Mock
    private FieldMapper fieldMapper;
    @Mock
    private EngineMapper engineMapper;
    @Mock
    private EngineClient engineClient;
    @Mock
    private DataCenterClient dataCenterClient;
    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysRelationMapper sysRelationMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private KnowledgeTreeMapper knowledgeTreeMapper;
    @Mock
    private PlatformTransactionManager engineTxManager;

    private BatchServiceImpl service;

    @Before
    public void setUp() {
        // LambdaUpdateWrapper needs MybatisPlus entity metadata (same pattern as EngineTaskServiceImplTest)
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), IndicatorBatch.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), IndicatorBatchItem.class);
        service = new BatchServiceImpl(batchMapper, itemMapper, fieldMapper, engineMapper,
                engineClient, dataCenterClient, objectMapper, sysUserMapper, sysRoleMapper,
                sysRelationMapper, passwordEncoder, knowledgeTreeMapper, engineTxManager);
        LoginUser u = new LoginUser();
        u.setUserId(1L);
        u.setUserType(1);
        u.setOrganId(1L);
        UserContext.set(u);
    }

    @After
    public void tearDown() {
        UserContext.clear();
    }

    private IndicatorBatch pendingBatch() {
        IndicatorBatch b = new IndicatorBatch();
        b.setId(9L);
        b.setEngineCode("E_LOAN");
        b.setOrganId(1L);
        b.setStatus(0);
        b.setTotalRows(10);
        return b;
    }

    /** Claim failed (0 affected rows = already running / claimed by a concurrent caller): must throw a business exception and must not reset items */
    @Test
    public void run_claimFails_throwsAndTouchesNothing() {
        when(batchMapper.selectById(9L)).thenReturn(pendingBatch());
        when(batchMapper.update(any(), any())).thenReturn(0);

        try {
            service.run(9L);
            fail("Failed claim must throw a business exception");
        } catch (BizException e) {
            assertEquals("Task is already running, please try again later", e.getMessage());
        }
        verify(itemMapper, never()).update(any(), any());
    }

    /** Claim succeeded (1 affected row): reset items and submit async execution (no pending items converges to completed immediately) */
    @Test
    public void run_claimSucceeds_resetsItemsAndRuns() {
        when(batchMapper.selectById(9L)).thenReturn(pendingBatch());
        when(batchMapper.update(any(), any())).thenReturn(1);
        when(itemMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.run(9L);

        verify(itemMapper).update(any(), any());
        // doRun writes back the completed status after converging (async; wait loosely for one round)
        try {
            Thread.sleep(300);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        verify(batchMapper).updateById(any(IndicatorBatch.class));
    }

    /** Task missing/invisible: original semantics preserved (NOT_FOUND, claim untouched) */
    @Test
    public void run_missingBatch_throwsNotFound() {
        when(batchMapper.selectById(9L)).thenReturn(null);
        try {
            service.run(9L);
            fail("Missing task must throw NOT_FOUND");
        } catch (BizException e) {
            assertEquals("Batch run task does not exist", e.getMessage());
        }
        verify(batchMapper, never()).update(any(), any());
    }
}
