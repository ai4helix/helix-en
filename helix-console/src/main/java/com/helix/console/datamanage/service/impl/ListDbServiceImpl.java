package com.helix.console.datamanage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.helix.console.common.BizException;
import com.helix.console.common.PageResult;
import com.helix.console.common.ResultCode;
import com.helix.console.datamanage.entity.ListDb;
import com.helix.console.datamanage.entity.ListEntry;
import com.helix.console.datamanage.enums.ListType;
import com.helix.console.datamanage.mapper.ListDbMapper;
import com.helix.console.datamanage.mapper.ListEntryMapper;
import com.helix.console.datamanage.service.ListDbService;
import com.helix.console.system.security.TenantScope;
import com.helix.console.engine.client.EngineClient;
import com.helix.console.engine.mapper.EngineNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * List DB service implementation.
 *
 * <p>Status conventions match rules/scorecards: 1 enabled / 0 disabled / -1 recycle bin.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListDbServiceImpl implements ListDbService {

    private static final int STATUS_RECYCLE = -1;
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;

    private final ListDbMapper listDbMapper;
    private final ListEntryMapper listEntryMapper;
    private final EngineClient engineClient;
    private final EngineNodeMapper engineNodeMapper;

    @Override
    public PageResult<ListDb> page(String listType, Integer status, String keyword,
                                   long pageNo, long pageSize) {
        LambdaQueryWrapper<ListDb> qw = new LambdaQueryWrapper<>();
        // Organization users only see list DBs of [0 platform public, this organization]
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            qw.in(ListDb::getOrganId, organs);
        }
        if (StringUtils.isNotBlank(listType)) {
            qw.eq(ListDb::getListType, listType);
        }
        if (status == null) {
            qw.in(ListDb::getStatus, STATUS_DISABLED, STATUS_ENABLED);
        } else {
            qw.eq(ListDb::getStatus, status);
        }
        if (StringUtils.isNotBlank(keyword)) {
            qw.and(w -> w.like(ListDb::getListName, keyword).or().like(ListDb::getListAttr, keyword));
        }
        qw.orderByDesc(ListDb::getId);
        Page<ListDb> page = listDbMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNo, pageSize);
    }

    @Override
    public ListDb detail(Integer id) {
        ListDb db = listDbMapper.selectById(id);
        if (db == null) {
            throw BizException.of(ResultCode.LIST_DB_NOT_FOUND);
        }
        // Viewing list DBs of other organizations is forbidden
        TenantScope.checkVisible(db.getOrganId() == null ? null : db.getOrganId().longValue());
        return db;
    }

    @Override
    public List<ListDb> listAvailable(String listType) {
        LambdaQueryWrapper<ListDb> qw = new LambdaQueryWrapper<>();
        // The node selector likewise only offers visible list DBs
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs != null) {
            qw.in(ListDb::getOrganId, organs);
        }
        qw.eq(ListDb::getStatus, STATUS_ENABLED);
        if (StringUtils.isNotBlank(listType)) {
            qw.eq(ListDb::getListType, listType);
        }
        qw.orderByDesc(ListDb::getId);
        return listDbMapper.selectList(qw);
    }

    @Override
    public List<ListDb> listByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        // Filter out list DBs of other organizations
        return listDbMapper.selectBatchIds(ids).stream()
                .filter(d -> TenantScope.isVisible(d.getOrganId() == null ? null : d.getOrganId().longValue()))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public Integer create(ListDb listDb) {
        validate(listDb);
        validateDuplicate(listDb.getListName(), listDb.getListType(), null);
        // Admin users default to platform public (0); organization users are forced to their own organization
        listDb.setOrganId(TenantScope.writeOrgan(listDb.getOrganId()));
        listDb.setListType(ListType.of(listDb.getListType()).getCode());
        listDb.setStatus(STATUS_ENABLED);
        LocalDateTime now = LocalDateTime.now();
        listDb.setCreatedTime(now);
        listDb.setUpdatedTime(now);
        listDbMapper.insert(listDb);
        return listDb.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void update(ListDb listDb) {
        if (listDb.getId() == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "List DB id must not be empty");
        }
        ListDb exist = listDbMapper.selectById(listDb.getId());
        if (exist == null) {
            throw BizException.of(ResultCode.LIST_DB_NOT_FOUND);
        }
        // Modifying list DBs of other organizations is forbidden
        TenantScope.checkVisible(exist.getOrganId() == null ? null : exist.getOrganId().longValue());
        validate(listDb);
        validateDuplicate(listDb.getListName(), listDb.getListType(), listDb.getId());
        listDb.setListType(ListType.of(listDb.getListType()).getCode());
        listDbMapper.updateById(listDb);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public Integer copy(Integer id) {
        ListDb src = listDbMapper.selectById(id);
        if (src == null) {
            throw BizException.of(ResultCode.LIST_DB_NOT_FOUND);
        }
        // Copying list DBs of other organizations is forbidden
        TenantScope.checkVisible(src.getOrganId() == null ? null : src.getOrganId().longValue());
        ListDb copy = new ListDb();
        copy.setListType(src.getListType());
        copy.setListName(buildCopyName(src.getListName()));
        // The copy belongs to the same organization as the source
        copy.setOrganId(src.getOrganId());
        copy.setDataSource(src.getDataSource());
        copy.setListAttr(src.getListAttr());
        copy.setListDesc(src.getListDesc());
        copy.setTableColumn(src.getTableColumn());
        copy.setMatchType(src.getMatchType());
        copy.setQueryType(src.getQueryType());
        copy.setQueryField(src.getQueryField());
        copy.setStatus(STATUS_DISABLED);
        LocalDateTime now = LocalDateTime.now();
        copy.setCreatedTime(now);
        copy.setUpdatedTime(now);
        listDbMapper.insert(copy);
        return copy.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void changeStatus(List<Integer> ids, Integer status) {
        if (ids == null || ids.isEmpty() || status == null) {
            return;
        }
        // Only apply to list DBs visible to this organization
        ids = filterVisible(ids);
        ids.forEach(id -> {
            ListDb db = new ListDb();
            db.setId(id);
            db.setStatus(status);
            listDbMapper.updateById(db);
        });
    }

    @Override
    public void moveToRecycle(List<Integer> ids) {
        changeStatus(ids, STATUS_RECYCLE);
    }

    @Override
    public void restore(List<Integer> ids) {
        changeStatus(ids, STATUS_DISABLED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void removePermanently(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // Only physically delete list DBs visible to this organization
        ids = filterVisible(ids);
        for (Integer id : ids) {
            ListDb db = listDbMapper.selectById(id);
            if (db != null && (db.getStatus() == null || db.getStatus() != STATUS_RECYCLE)) {
                throw BizException.of(ResultCode.PARAM_INVALID, "Only list DBs in the recycle bin can be permanently deleted");
            }
        }
        listDbMapper.deleteBatchIds(ids);
    }

    private void validate(ListDb db) {
        if (StringUtils.isBlank(db.getListName())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "List DB name must not be empty");
        }
        if (StringUtils.isBlank(db.getTableColumn())) {
            throw BizException.of(ResultCode.PARAM_INVALID, "Select at least one field to participate in matching");
        }
        if (db.getDataSource() == null) {
            db.setDataSource(2);
        }
        if (db.getMatchType() == null) {
            db.setMatchType(1);
        }
        if (db.getQueryType() == null) {
            db.setQueryType(1);
        }
        // query_field defaults to table_column when empty, consistent with the legacy DB convention
        if (StringUtils.isBlank(db.getQueryField())) {
            db.setQueryField(db.getTableColumn());
        }
    }

    private void validateDuplicate(String name, String listType, Integer excludeId) {
        if (StringUtils.isBlank(name)) {
            return;
        }
        // Duplicate check is limited to organizations visible to the current user
        List<Long> organs = TenantScope.visibleOrgans();
        LambdaQueryWrapper<ListDb> qw = new LambdaQueryWrapper<ListDb>()
                .eq(ListDb::getListName, name)
                .eq(ListDb::getListType, ListType.of(listType).getCode())
                .ne(ListDb::getStatus, STATUS_RECYCLE);
        if (organs != null) {
            qw.in(ListDb::getOrganId, organs);
        }
        if (excludeId != null) {
            qw.ne(ListDb::getId, excludeId);
        }
        if (listDbMapper.selectCount(qw) > 0) {
            throw BizException.of(ResultCode.DATA_DUPLICATE, "List DB name already exists under the same type: " + name);
        }
    }

    private String buildCopyName(String name) {
        String base = StringUtils.defaultString(name);
        String candidate = base + "_copy";
        List<Long> organs = TenantScope.visibleOrgans();
        int i = 1;
        while (true) {
            LambdaQueryWrapper<ListDb> qw = new LambdaQueryWrapper<ListDb>()
                    .eq(ListDb::getListName, candidate).ne(ListDb::getStatus, STATUS_RECYCLE);
            if (organs != null) {
                qw.in(ListDb::getOrganId, organs);
            }
            if (listDbMapper.selectCount(qw) == 0) {
                break;
            }
            candidate = base + "_copy" + (++i);
        }
        return candidate;
    }

    /** Filters the id list down to list DB ids visible to the current user */
    private List<Integer> filterVisible(List<Integer> ids) {
        List<Long> organs = TenantScope.visibleOrgans();
        if (organs == null || ids == null || ids.isEmpty()) {
            return ids;
        }
        return listDbMapper.selectBatchIds(ids).stream()
                .filter(d -> TenantScope.isVisible(d.getOrganId() == null ? null : d.getOrganId().longValue()))
                .map(ListDb::getId)
                .collect(java.util.stream.Collectors.toList());
    }

    // ------------------------------------------------------------------ List entries

    @Override
    public PageResult<ListEntry> pageEntries(Integer listId, String keyword, Integer status,
                                             long pageNo, long pageSize) {
        requireListDb(listId);
        LambdaQueryWrapper<ListEntry> qw = new LambdaQueryWrapper<>();
        qw.eq(ListEntry::getListId, listId);
        qw.eq(ListEntry::getDeleted, 0);
        if (StringUtils.isNotBlank(keyword)) {
            qw.and(w -> w.like(ListEntry::getEntryValue, keyword).or().like(ListEntry::getRemark, keyword));
        }
        if (status != null) {
            qw.eq(ListEntry::getStatus, status);
        }
        qw.orderByDesc(ListEntry::getId);
        Page<ListEntry> page = listEntryMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public int addEntries(Integer listId, String valuesText, String remark,
                          LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {
        ListDb listDb = requireListDb(listId);
        if (StringUtils.isBlank(valuesText)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "List entry content must not be empty");
        }
        // Split by newline/comma/semicolon/whitespace, trim, drop empties, deduplicate
        Set<String> values = new LinkedHashSet<>();
        for (String token : valuesText.split("[\\n,;，；\\s]+")) {
            String v = token.trim();
            if (!v.isEmpty()) {
                values.add(v);
            }
        }
        if (values.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "No valid list entries parsed");
        }
        // Unique key (list_id, entry_value, deleted): query existing entries first and skip duplicates
        Set<String> existing = new LinkedHashSet<>();
        listEntryMapper.selectList(new LambdaQueryWrapper<ListEntry>()
                        .eq(ListEntry::getListId, listId)
                        .eq(ListEntry::getDeleted, 0)
                        .in(ListEntry::getEntryValue, values))
                .forEach(e -> existing.add(e.getEntryValue()));

        int inserted = 0;
        for (String value : values) {
            if (existing.contains(value)) {
                continue;
            }
            ListEntry entry = new ListEntry();
            entry.setListId(listId);
            entry.setEntryValue(value);
            entry.setEntryType(listDb.getListAttr());
            entry.setRemark(remark);
            entry.setEffectiveFrom(effectiveFrom);
            entry.setEffectiveTo(effectiveTo);
            entry.setStatus(STATUS_ENABLED);
            entry.setDeleted(0);
            listEntryMapper.insert(entry);
            inserted++;
        }
        if (inserted > 0) {
            notifyEngineReload(listId);
        }
        return inserted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void changeEntryStatus(Integer listId, List<Long> entryIds, Integer status) {
        requireListDb(listId);
        if (entryIds == null || entryIds.isEmpty() || status == null) {
            return;
        }
        for (Long entryId : entryIds) {
            ListEntry entry = listEntryMapper.selectById(entryId);
            if (entry == null || !listId.equals(entry.getListId())) {
                continue;
            }
            ListEntry upd = new ListEntry();
            upd.setId(entryId);
            upd.setStatus(status);
            listEntryMapper.updateById(upd);
        }
        notifyEngineReload(listId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, transactionManager = "engineTxManager")
    public void removeEntries(Integer listId, List<Long> entryIds) {
        requireListDb(listId);
        if (entryIds == null || entryIds.isEmpty()) {
            return;
        }
        for (Long entryId : entryIds) {
            ListEntry entry = listEntryMapper.selectById(entryId);
            if (entry == null || !listId.equals(entry.getListId())) {
                continue;
            }
            ListEntry upd = new ListEntry();
            upd.setId(entryId);
            upd.setDeleted(1);
            listEntryMapper.updateById(upd);
        }
        notifyEngineReload(listId);
    }

    private ListDb requireListDb(Integer listId) {
        if (listId == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "List DB id must not be empty");
        }
        ListDb db = listDbMapper.selectById(listId);
        if (db == null) {
            throw BizException.of(ResultCode.LIST_DB_NOT_FOUND);
        }
        // Operating on list DBs of other organizations (including list entries) is forbidden
        TenantScope.checkVisible(db.getOrganId() == null ? null : db.getOrganId().longValue());
        return db;
    }

    /**
     * Notifies the engine to rebuild snapshots after list changes (loading the new entry set).
     *
     * <p>Targets "affected engines" precisely — first looks up which engines' list nodes
     * reference the list DB, then rebuilds only those engines; it does not rescan other
     * engines' publish artifacts or re-parse other engines' condition ASTs
     * (in SaaS, a tenant A list change does not affect B/C/D).</p>
     *
     * <p>When no reference is found (the list DB is not used by any node yet), it degrades
     * to "refresh lists only"; list data is shared globally in the snapshot, so a single
     * load takes effect for all engines.</p>
     *
     * <p>The client already retries with backoff (3 attempts); if it still fails, only a
     * warning is logged without blocking the business flow — the engine-side
     * {@code SnapshotSelfHealJob} periodically reconciles config changes and rebuilds as a fallback.</p>
     */
    private void notifyEngineReload(Integer listDbId) {
        try {
            List<String> codes = listDbId == null
                    ? java.util.Collections.emptyList()
                    : engineNodeMapper.selectEngineCodesByListDb(listDbId);
            engineClient.refreshEngines(codes);
            log.info("List DB {} change notified engines for incremental reload: affected engines {}",
                    listDbId, codes.isEmpty() ? "(none, lists refreshed only)" : codes);
        } catch (Exception e) {
            log.warn("Failed to notify engines to refresh after list change (engine-side scheduled reconciliation will rebuild as fallback): {}", e.getMessage());
        }
    }
}
