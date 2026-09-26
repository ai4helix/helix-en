package com.helix.console.batch.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.helix.console.batch.entity.EngineTask;
import com.helix.console.batch.mapper.EngineTaskMapper;
import com.helix.console.common.BizException;
import com.helix.console.common.PageResult;
import com.helix.console.engine.entity.Engine;
import com.helix.console.engine.mapper.EngineMapper;
import com.helix.console.system.security.LoginUser;
import com.helix.console.system.security.UserContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Engine task import unit tests: validation, tenant ownership, upsert,
 * logical delete and tenant filtering.
 *
 * <p>Covers two conventions that easily regress:</p>
 * <ul>
 *   <li>logical delete must be "manual setDeleted(1) + updateById", never deleteById
 *       (the global logic-delete-value disagrees with the DDL 0/1 convention; enabling
 *       @TableLogic would make data unqueryable);</li>
 *   <li>all queries must carry {@code deleted = 0} and inject tenant filtering per
 *       {@link com.helix.console.system.security.TenantScope}.</li>
 * </ul>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class EngineTaskServiceImplTest {

    private static final String HEADER = "task_code,task_name,engine_code,key_field,description\n";

    @Mock
    private EngineTaskMapper taskMapper;

    @Mock
    private EngineMapper engineMapper;

    private EngineTaskServiceImpl service;

    @Before
    public void setUp() {
        // Pure unit tests without a Spring container: load entity table info manually,
        // otherwise LambdaQueryWrapper.getSqlSegment() fails on the missing lambda cache
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), EngineTask.class);

        service = new EngineTaskServiceImpl(taskMapper, engineMapper);
        // One visible and enabled engine exists by default
        when(engineMapper.selectList(any())).thenReturn(Collections.singletonList(engine("RISK_MAIN", 0)));
    }

    @After
    public void tearDown() {
        UserContext.clear();
    }

    // ===== Import: write ownership =====

    @Test
    public void import_shouldInsertAsPlatformPublic_whenAdmin() {
        loginAsAdmin();
        when(taskMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> result = service.importTasks(csv("risk_daily,Daily Batch Run,RISK_MAIN,uid,notes\n"));

        assertEquals(1, result.get("inserted"));
        assertEquals(0, result.get("updated"));
        assertEquals(1, result.get("total"));
        assertTrue(((List<?>) result.get("errors")).isEmpty());

        EngineTask saved = captureInserted();
        assertEquals(Long.valueOf(0L), saved.getOrganId());   // admin users default to platform common
        assertEquals(Integer.valueOf(0), saved.getDeleted()); // DDL convention: 0 normal
        assertEquals(Integer.valueOf(1), saved.getStatus());
        assertEquals("uid", saved.getKeyField());
        assertEquals(Long.valueOf(1L), saved.getCreatedBy());
    }

    @Test
    public void import_shouldForceTenantOrgan_whenSaasUser() {
        loginAsSaas(7L);
        when(taskMapper.selectOne(any())).thenReturn(null);

        service.importTasks(csv("risk_daily,Daily Batch Run,RISK_MAIN,uid,notes\n"));

        assertEquals(Long.valueOf(7L), captureInserted().getOrganId());
    }

    @Test
    public void import_shouldUpdateExistingInsteadOfInserting() {
        loginAsAdmin();
        EngineTask existing = new EngineTask();
        existing.setId(99L);
        existing.setTaskCode("risk_daily");
        when(taskMapper.selectOne(any())).thenReturn(existing);

        Map<String, Object> result = service.importTasks(csv("risk_daily,Renamed Task,RISK_MAIN,pid,new notes\n"));

        assertEquals(0, result.get("inserted"));
        assertEquals(1, result.get("updated"));

        ArgumentCaptor<EngineTask> captor = ArgumentCaptor.forClass(EngineTask.class);
        verify(taskMapper).updateById(captor.capture());
        verify(taskMapper, never()).insert(any());
        assertEquals(Long.valueOf(99L), captor.getValue().getId());
        assertEquals("Renamed Task", captor.getValue().getTaskName());
        assertEquals("pid", captor.getValue().getKeyField());
    }

    // ===== Import: validation and per-row fault tolerance =====

    @Test
    public void import_shouldRejectRowErrorsButKeepValidRows() {
        loginAsAdmin();
        when(taskMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> result = service.importTasks(csv(
                "1bad,Invalid code,RISK_MAIN,uid,\n"
                        + "good_task,Engine missing,NOT_EXIST,uid,\n"
                        + "ok_task,Normal task,RISK_MAIN,uid,\n"));

        assertEquals(1, result.get("inserted"));   // only ok_task lands in the DB
        assertEquals(3, result.get("total"));
        assertEquals(2, ((List<?>) result.get("errors")).size());
    }

    @Test
    public void import_shouldRejectWhenRequiredHeaderColumnMissing() {
        loginAsAdmin();
        MockMultipartFile file = new MockMultipartFile("file", "t.csv", "text/csv",
                "task_code,engine_code\nrisk_daily,RISK_MAIN\n".getBytes(StandardCharsets.UTF_8));

        try {
            service.importTasks(file);
            fail("Missing task_name column must be rejected");
        } catch (BizException e) {
            assertEquals(10001, e.getCode());
            assertTrue(e.getMessage().contains("task_name"));
        }
    }

    @Test
    public void import_shouldRejectEmptyFile() {
        loginAsAdmin();
        try {
            service.importTasks(new MockMultipartFile("file", "t.csv", "text/csv", new byte[0]));
            fail("Empty file must be rejected");
        } catch (BizException e) {
            assertEquals(10001, e.getCode());
        }
    }

    // ===== Delete: manual logical delete + tenant check =====

    @Test
    public void delete_shouldSoftDeleteAndNeverPhysicallyRemove() {
        loginAsAdmin();
        when(taskMapper.selectOne(any())).thenReturn(task(5L, 0L));

        service.deleteTask(5L);

        ArgumentCaptor<EngineTask> captor = ArgumentCaptor.forClass(EngineTask.class);
        verify(taskMapper).updateById(captor.capture());
        verify(taskMapper, never()).deleteById(any());
        assertEquals(Integer.valueOf(1), captor.getValue().getDeleted());
        assertNotNull(captor.getValue().getUpdatedTime());
    }

    @Test
    public void delete_shouldForbidCrossTenant() {
        loginAsSaas(7L);
        when(taskMapper.selectOne(any())).thenReturn(task(5L, 99L));

        try {
            service.deleteTask(5L);
            fail("Cross-tenant delete must be rejected");
        } catch (BizException e) {
            assertEquals(20003, e.getCode());
        }
        verify(taskMapper, never()).updateById(any());
    }

    @Test
    public void delete_shouldFailWhenTaskAbsentOrAlreadyDeleted() {
        loginAsAdmin();
        when(taskMapper.selectOne(any())).thenReturn(null);

        try {
            service.deleteTask(5L);
            fail("Missing task must raise an error");
        } catch (BizException e) {
            assertEquals(10002, e.getCode());
        }
    }

    // ===== Page: logical delete + tenant filtering =====

    @Test
    public void page_shouldFilterDeletedAndInjectTenantScope() {
        loginAsSaas(7L);
        Page<EngineTask> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(task(1L, 0L)));
        page.setTotal(1);
        doReturn(page).when(taskMapper)
                .selectPage(org.mockito.ArgumentMatchers.<Page<EngineTask>>any(),
                        org.mockito.ArgumentMatchers.<Wrapper<EngineTask>>any());

        PageResult<EngineTask> result = service.pageTasks(1, 10, null, null);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<EngineTask>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(taskMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue("Query must filter logical deletes, actual: " + sql, sql.contains("deleted"));
        assertTrue("Query must inject tenant filtering, actual: " + sql, sql.contains("organ_id"));
    }

    // ===== Helpers =====

    private EngineTask captureInserted() {
        ArgumentCaptor<EngineTask> captor = ArgumentCaptor.forClass(EngineTask.class);
        verify(taskMapper).insert(captor.capture());
        return captor.getValue();
    }

    private static EngineTask task(long id, long organId) {
        EngineTask t = new EngineTask();
        t.setId(id);
        t.setOrganId(organId);
        t.setTaskCode("risk_daily");
        t.setStatus(1);
        t.setDeleted(0);
        return t;
    }

    private static Engine engine(String code, int organId) {
        Engine e = new Engine();
        e.setCode(code);
        e.setStatus(1);
        e.setOrganId(organId);
        return e;
    }

    private static MockMultipartFile csv(String rows) {
        return new MockMultipartFile("file", "tasks.csv", "text/csv",
                (HEADER + rows).getBytes(StandardCharsets.UTF_8));
    }

    /** Platform admin user (userType=1): sees all tenants */
    private static void loginAsAdmin() {
        LoginUser u = new LoginUser();
        u.setUserId(1L);
        u.setUserType(1);
        u.setOrganId(1L);
        UserContext.set(u);
    }

    /** SaaS org user (userType=2): sees only [0, own organization] */
    private static void loginAsSaas(long organId) {
        LoginUser u = new LoginUser();
        u.setUserId(2L);
        u.setUserType(2);
        u.setOrganId(organId);
        UserContext.set(u);
    }
}
