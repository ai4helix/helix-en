package com.helix.console.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helix.console.common.BizException;
import com.helix.console.common.ResultCode;
import com.helix.console.datamanage.entity.Field;
import com.helix.console.datamanage.entity.FieldType;
import com.helix.console.datamanage.entity.ListDb;
import com.helix.console.datamanage.entity.ListEntry;
import com.helix.console.datamanage.mapper.FieldMapper;
import com.helix.console.datamanage.mapper.FieldTypeMapper;
import com.helix.console.datamanage.mapper.ListDbMapper;
import com.helix.console.datamanage.mapper.ListEntryMapper;
import com.helix.console.engine.entity.DecisionTableEntity;
import com.helix.console.engine.entity.DtCellEntity;
import com.helix.console.engine.entity.DtColumnEntity;
import com.helix.console.engine.entity.DtRowEntity;
import com.helix.console.engine.entity.Engine;
import com.helix.console.engine.entity.EngineNode;
import com.helix.console.engine.entity.EngineVersion;
import com.helix.console.engine.entity.FlowEdgeEntity;
import com.helix.console.engine.entity.NodeKnowledgeRel;
import com.helix.console.engine.client.EngineClient;
import com.helix.console.engine.mapper.DecisionTableMapper;
import com.helix.console.engine.mapper.DtCellMapper;
import com.helix.console.engine.mapper.DtColumnMapper;
import com.helix.console.engine.mapper.DtRowMapper;
import com.helix.console.engine.mapper.EngineMapper;
import com.helix.console.engine.mapper.EngineNodeMapper;
import com.helix.console.engine.mapper.EngineVersionMapper;
import com.helix.console.engine.mapper.FlowEdgeMapper;
import com.helix.console.engine.mapper.NodeKnowledgeRelMapper;
import com.helix.console.engine.service.DecisionFlowService;
import com.helix.console.knowledge.entity.KnowledgeTree;
import com.helix.console.knowledge.entity.Rule;
import com.helix.console.knowledge.entity.RuleCondition;
import com.helix.console.knowledge.entity.Scorecard;
import com.helix.console.knowledge.enums.TreeType;
import com.helix.console.knowledge.mapper.KnowledgeTreeMapper;
import com.helix.console.knowledge.mapper.RuleConditionMapper;
import com.helix.console.knowledge.mapper.RuleMapper;
import com.helix.console.knowledge.mapper.ScorecardMapper;
import com.helix.console.system.entity.SysOrganization;
import com.helix.console.system.mapper.SysOrganizationMapper;
import com.helix.console.system.security.UserContext;
import com.helix.console.system.service.DemoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tenant demo data initialization service implementation.
 *
 * <p>The data blueprint is the small consumer loan demo from
 * {@code db/v6-tenant-demo-data.sql}, generated programmatically per tenant
 * (explicit naming + organ ownership). Topology:
 * Start -> Blacklist Screening -> Policy Rules (4 deny / 1 manual / 2 score
 * adjust) -> Scorecard (3-dimension binning) -> Quota Decision Table (FIRST,
 * 4 rows including a fallback reject) -> End.</p>
 *
 * <p>Transaction and idempotency: engine DB inserts run as a whole under
 * {@code engineTxManager} (programmatic transaction, avoiding broken
 * self-invocation proxies within the same class); after the engine data commits,
 * console {@code t_organization.demo_status=1} is updated; then the standard
 * publish flow {@link DecisionFlowService#publishVersion} runs (artifact
 * solidification + boot_state + snapshot refresh). Publish failure does not
 * block initialization (when the engine is unreachable the user can publish
 * manually on the decision flow page; the data itself is complete).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoServiceImpl implements DemoService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SysOrganizationMapper organizationMapper;
    private final EngineMapper engineMapper;
    private final EngineVersionMapper engineVersionMapper;
    private final EngineNodeMapper engineNodeMapper;
    private final FlowEdgeMapper flowEdgeMapper;
    private final NodeKnowledgeRelMapper nodeKnowledgeRelMapper;
    private final RuleMapper ruleMapper;
    private final RuleConditionMapper ruleConditionMapper;
    private final ScorecardMapper scorecardMapper;
    private final KnowledgeTreeMapper knowledgeTreeMapper;
    private final FieldMapper fieldMapper;
    private final FieldTypeMapper fieldTypeMapper;
    private final ListDbMapper listDbMapper;
    private final ListEntryMapper listEntryMapper;
    private final DecisionTableMapper decisionTableMapper;
    private final DtColumnMapper dtColumnMapper;
    private final DtRowMapper dtRowMapper;
    private final DtCellMapper dtCellMapper;
    private final DecisionFlowService decisionFlowService;
    private final EngineClient engineClient;

    /** engine DB transaction manager (multi-datasource routing by Mapper package; dedicated to engine-domain writes).
     *  Non-final + @Resource by-name injection: with @RequiredArgsConstructor constructor injection
     *  @Qualifier does not apply, and two PlatformTransactionManager candidates would be ambiguous. */
    @javax.annotation.Resource(name = "engineTxManager")
    private PlatformTransactionManager engineTxManager;

    @Override
    public Map<String, Object> status() {
        return baseStatus(currentOrganId());
    }

    @Override
    public Map<String, Object> init() {
        Long organId = currentOrganId();
        String suffix = "_O" + organId;
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> out = baseStatus(organId);
        if (Boolean.TRUE.equals(out.get("initialized"))) {
            // Idempotency 1: already initialized, return the current status directly
            out.put("engineCode", demoEngineCode(organId));
            out.put("published", false);
            return out;
        }
        Engine existed = engineMapper.selectOne(new LambdaQueryWrapper<Engine>()
                .eq(Engine::getCode, demoEngineCode(organId))
                .eq(Engine::getOrganId, organId.intValue())
                .last("limit 1"));
        if (existed != null) {
            // Idempotency 2: the demo engine exists but the status flag is unset (last status update failed); just fix the status
            markInitialized(organId);
            out = baseStatus(organId);
            out.put("engineCode", existed.getCode());
            out.put("published", false);
            return out;
        }

        // 1. Generate the whole engine DB data (single transaction)
        Integer versionId = new TransactionTemplate(engineTxManager).execute(st -> {
            int organ = organId.intValue();
            Integer typeId = ensureBaseFieldType();
            Integer ruleRoot = ensureTreeRoot(TreeType.RULE, "Rule Set", organ, now);
            Integer cardRoot = ensureTreeRoot(TreeType.SCORECARD, "Scorecard", organ, now);
            Integer fieldCatalog = ensureDemoFieldCatalog(organ, now);

            insertFields(organ, typeId, fieldCatalog, now);
            Map<String, Integer> rules = insertRules(organ, ruleRoot, suffix, now);
            Integer cardId = insertScorecard(organ, cardRoot, suffix, now);
            Integer listId = insertListDb(organ, now);
            Integer tableId = insertDecisionTable(organ, suffix, now);
            return insertEngineGraph(organ, suffix, rules, cardId, listId, tableId, now);
        });

        // 2. Standard publish (failure does not block: data is complete and can be published manually on the decision flow page)
        boolean published = false;
        try {
            decisionFlowService.publishVersion(versionId);
            published = true;
            // Incremental reload on publish reuses the old list collections -- the demo
            // list DB just inserted for this tenant would not enter the snapshot (list
            // nodes would be treated as "DB not loaded"). Run one extra engine
            // incremental reload with list refresh (refreshLists=true, one write lock)
            // so the new list is immediately usable.
            try {
                engineClient.refreshEngines(java.util.Collections.singletonList(demoEngineCode(organId)));
            } catch (Exception e) {
                log.warn("[demo init] demo list snapshot refresh failed (engine-side scheduled reconciliation will recover) err={}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("[demo init] demo engine publish failed (data generated; publish manually on the decision flow page) versionId={} err={}",
                    versionId, e.getMessage());
        }

        // 3. Mark initialization status in the console DB (the idempotent branch recovers on failure)
        markInitialized(organId);

        Map<String, Object> out2 = baseStatus(organId);
        out2.put("engineCode", demoEngineCode(organId));
        out2.put("versionId", versionId);
        out2.put("published", published);
        return out2;
    }

    // ------------------------------------------------------------------ Status

    private Long currentOrganId() {
        Long organId = UserContext.currentOrganId();
        if (organId == null) {
            throw BizException.of(ResultCode.FORBIDDEN, "Current user organization not found; cannot operate demo data");
        }
        return organId;
    }

    private Map<String, Object> baseStatus(Long organId) {
        SysOrganization org = organizationMapper.selectById(organId);
        boolean initialized = org != null && org.getDemoStatus() != null && org.getDemoStatus() == 1;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("organId", organId);
        out.put("initialized", initialized);
        return out;
    }

    private void markInitialized(Long organId) {
        SysOrganization upd = new SysOrganization();
        upd.setId(organId);
        upd.setDemoStatus(1);
        organizationMapper.updateById(upd);
    }

    private String demoEngineCode(Long organId) {
        return "DEMO_LOAN_O" + organId;
    }

    // ------------------------------------------------------------------ Engine DB data generation

    /** Field type "Basic Attributes": globally shared; reuse when it exists */
    private Integer ensureBaseFieldType() {
        FieldType exists = fieldTypeMapper.selectOne(new LambdaQueryWrapper<FieldType>()
                .eq(FieldType::getFieldType, "Basic Attributes")
                .last("limit 1"));
        if (exists != null) {
            return exists.getId();
        }
        FieldType ft = new FieldType();
        ft.setFieldType("Basic Attributes");
        ft.setParentId(0);
        ft.setIsCommon(1);
        fieldTypeMapper.insert(ft);
        return ft.getId();
    }

    /** Business tree root fallback (registration creates trees lazily; demo init must guarantee roots exist) */
    private Integer ensureTreeRoot(TreeType treeType, String name, int organ, LocalDateTime now) {
        KnowledgeTree root = knowledgeTreeMapper.selectOne(new LambdaQueryWrapper<KnowledgeTree>()
                .eq(KnowledgeTree::getTreeType, treeType.getCode())
                .eq(KnowledgeTree::getOrganId, organ)
                .isNull(KnowledgeTree::getEngineId)
                .eq(KnowledgeTree::getStatus, 1)
                .orderByAsc(KnowledgeTree::getId)
                .last("limit 1"));
        if (root != null) {
            return root.getId();
        }
        KnowledgeTree created = new KnowledgeTree();
        created.setName(name);
        created.setParentId(0);
        created.setTreeType(treeType.getCode());
        created.setOrganId(organ);
        created.setType(1);
        created.setStatus(1);
        created.setCreatedTime(now);
        created.setUpdatedTime(now);
        knowledgeTreeMapper.insert(created);
        return created.getId();
    }

    /** Field category catalog "Demo Fields" (tree_type=4 top level) */
    private Integer ensureDemoFieldCatalog(int organ, LocalDateTime now) {
        KnowledgeTree exists = knowledgeTreeMapper.selectOne(new LambdaQueryWrapper<KnowledgeTree>()
                .eq(KnowledgeTree::getTreeType, TreeType.FIELD.getCode())
                .eq(KnowledgeTree::getOrganId, organ)
                .eq(KnowledgeTree::getName, "Demo Fields")
                .eq(KnowledgeTree::getStatus, 1)
                .last("limit 1"));
        if (exists != null) {
            return exists.getId();
        }
        KnowledgeTree created = new KnowledgeTree();
        created.setName("Demo Fields");
        created.setParentId(0);
        created.setTreeType(TreeType.FIELD.getCode());
        created.setOrganId(organ);
        created.setType(1);
        created.setStatus(1);
        created.setCreatedTime(now);
        created.setUpdatedTime(now);
        knowledgeTreeMapper.insert(created);
        return created.getId();
    }

    /** 8 demo fields (blueprint v6) */
    private void insertFields(int organ, Integer typeId, Integer catalogId, LocalDateTime now) {
        String[][] defs = {
                {"f_AGE", "Age", "1", "0"},
                {"f_CITY", "City", "2", "0"},
                {"f_MOBILE", "Mobile No.", "2", "0"},
                {"f_IDNO", "ID No.", "2", "0"},
                {"f_LIMIT", "Applied Amount", "4", "0"},
                {"f_CREDIT_SCORE", "Credit Score", "1", "0"},
                {"f_INCOME", "Monthly Income", "4", "0"},
                {"f_LIMIT_RESULT", "Quota Plan", "2", "1"}
        };
        for (String[] d : defs) {
            Field f = new Field();
            f.setFieldEn(d[0]);
            f.setFieldCn(d[1]);
            f.setFieldTypeid(typeId);
            f.setCatalogId(catalogId);
            f.setValueType(Integer.valueOf(d[2]));
            f.setIsOutput(Integer.valueOf(d[3]));
            f.setOrganId(organ);
            f.setStatus(1);
            f.setCreatedTime(now);
            f.setUpdatedTime(now);
            fieldMapper.insert(f);
        }
    }

    /** 7 rules + condition ASTs (including an AND group example); returns code -> id */
    private Map<String, Integer> insertRules(int organ, Integer ruleRoot, String suffix, LocalDateTime now) {
        // code, name, priority, content, resultTypeV2, scoreValue, ruleAudit
        String[][] defs = {
                {"R_DEMO_AGE", "Age Admission Reject", "1", "f_AGE LT 18", "DENY", null, "0"},
                {"R_DEMO_CITY", "Restricted City Reject", "2", "f_CITY IN Putian,Tieling", "DENY", null, "0"},
                {"R_DEMO_MOBILE", "Virtual Number Reject", "3", "f_MOBILE STARTS_WITH 170", "DENY", null, "0"},
                {"R_DEMO_MANUAL_AMT", "Large Amount Manual Review", "4", "f_LIMIT GT 500000", "MANUAL", null, "3"},
                {"R_DEMO_SUB_AGE", "Young Age Score Deduction", "1", "f_AGE BETWEEN 18,24", "SUB_SCORE", "20", "0"},
                {"R_DEMO_ADD_CITY", "Quality City Score Bonus", "2", "f_CITY IN Shanghai,Shenzhen,Hangzhou", "ADD_SCORE", "10", "0"},
                {"R_DEMO_YOUNG_NOCREDIT", "Young Low Credit Reject", "5", "f_AGE BETWEEN 18,22 AND f_CREDIT_SCORE LT 500", "DENY", null, "0"}
        };
        Map<String, Integer> ids = new LinkedHashMap<>();
        for (String[] d : defs) {
            boolean scoreRule = "ADD_SCORE".equals(d[4]) || "SUB_SCORE".equals(d[4]);
            Rule r = new Rule();
            r.setName(d[1]);
            r.setCode(d[0] + suffix);
            r.setPriority(Integer.valueOf(d[2]));
            r.setOrganId(organ);
            r.setParentId(ruleRoot);
            r.setEngineId(null); // knowledge base visibility requires engine_id=NULL (engine loading references by node id)
            r.setStatus(1);
            r.setDeleted(0);
            r.setType(1);
            r.setIsNon(0);
            r.setContent(d[3]);
            r.setLastLogical("-1");
            r.setResultTypeV2(d[4]);
            r.setRuleType(scoreRule ? 1 : 0);
            r.setRuleAudit(Integer.valueOf(d[6]));
            r.setScore(scoreRule ? Integer.valueOf(d[5]) : null);
            r.setScoreValue(scoreRule ? Integer.valueOf(d[5]) : null);
            r.setConditionVersion(2);
            r.setCreatedTime(now);
            r.setUpdatedTime(now);
            ruleMapper.insert(r);
            ids.put(d[0], r.getId());
        }

        leaf(ids.get("R_DEMO_AGE"), "f_AGE", "LT", "18", 1);
        leaf(ids.get("R_DEMO_CITY"), "f_CITY", "IN", "Putian,Tieling", 1);
        leaf(ids.get("R_DEMO_MOBILE"), "f_MOBILE", "STARTS_WITH", "170", 1);
        leaf(ids.get("R_DEMO_MANUAL_AMT"), "f_LIMIT", "GT", "500000", 1);
        leaf(ids.get("R_DEMO_SUB_AGE"), "f_AGE", "BETWEEN", "18,24", 1);
        leaf(ids.get("R_DEMO_ADD_CITY"), "f_CITY", "IN", "Shanghai,Shenzhen,Hangzhou", 1);

        // AND group example: f_AGE BETWEEN 18,22 AND f_CREDIT_SCORE < 500
        Integer rid = ids.get("R_DEMO_YOUNG_NOCREDIT");
        RuleCondition group = new RuleCondition();
        group.setRuleId(rid);
        group.setParentId(null);
        group.setNodeType(2);
        group.setSortNo(1);
        group.setDepth(1);
        group.setDeleted(0);
        ruleConditionMapper.insert(group);
        leaf(rid, "f_AGE", "BETWEEN", "18,22", 1, group.getId(), 2);
        leaf(rid, "f_CREDIT_SCORE", "LT", "500", 2, group.getId(), 2);
        return ids;
    }

    private void leaf(Integer ruleId, String field, String op, String value, int sortNo) {
        leaf(ruleId, field, op, value, sortNo, null, 1);
    }

    private void leaf(Integer ruleId, String field, String op, String value, int sortNo, Long parentId, int depth) {
        RuleCondition c = new RuleCondition();
        c.setRuleId(ruleId);
        c.setParentId(parentId);
        c.setNodeType(1);
        c.setFieldCode(field);
        c.setOperator(op);
        c.setValue(value);
        c.setSortNo(sortNo);
        c.setDepth(depth);
        c.setDeleted(0);
        ruleConditionMapper.insert(c);
    }

    /** Scorecard: credit/age/income three-dimension [min,max) bins (engine matchBin only supports numeric bins) */
    private Integer insertScorecard(int organ, Integer cardRoot, String suffix, LocalDateTime now) {
        List<Map<String, Object>> dims = new ArrayList<>();
        dims.add(dim("f_CREDIT_SCORE", "Credit Score",
                new int[][]{{0, 550, -30}, {550, 650, 0}, {650, 750, 30}, {750, 10000, 60}}));
        dims.add(dim("f_AGE", "Age",
                new int[][]{{0, 25, -10}, {25, 40, 10}, {40, 10000, 5}}));
        dims.add(dim("f_INCOME", "Monthly Income",
                new int[][]{{0, 5000, -10}, {5000, 20000, 10}, {20000, 1000000, 20}}));

        Scorecard sc = new Scorecard();
        sc.setName("Demo Base Scorecard");
        sc.setCode("DEMO_CARD" + suffix);
        sc.setDescription("Credit/Age/Income three-dimension binning, [min,max) half-open (demo data)");
        sc.setOrganId(organ);
        sc.setParentId(cardRoot);
        sc.setEngineId(null); // same as rules: knowledge base visibility requires NULL
        sc.setType(1);
        sc.setStatus(1);
        sc.setDeleted(0);
        sc.setScore(writeJson(dims));
        sc.setCreatedTime(now);
        sc.setUpdatedTime(now);
        scorecardMapper.insert(sc);
        return sc.getId();
    }

    private Map<String, Object> dim(String field, String name, int[][] bins) {
        List<Map<String, Object>> binList = new ArrayList<>();
        for (int[] b : bins) {
            Map<String, Object> bin = new LinkedHashMap<>();
            bin.put("min", b[0]);
            bin.put("max", b[1]);
            bin.put("score", b[2]);
            binList.add(bin);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("field", field);
        d.put("name", name);
        d.put("bins", binList);
        return d;
    }

    /** Blacklist DB + 5 entries (blueprint v6) */
    private Integer insertListDb(int organ, LocalDateTime now) {
        ListDb db = new ListDb();
        db.setListType("b");
        db.setListName("Demo Blacklist");
        db.setDataSource(1);
        db.setListAttr("Risk Blacklist");
        db.setListDesc("Demo data: mobile + ID number blacklist");
        db.setTableColumn("mobile,idno");
        db.setMatchType(1);
        db.setQueryType(1);
        db.setQueryField("f_MOBILE,f_IDNO");
        db.setOrganId(organ);
        db.setStatus(1);
        db.setCreatedTime(now);
        db.setUpdatedTime(now);
        listDbMapper.insert(db);

        String[][] entries = {
                {"13800000003", "mobile", "Demo Blacklist - Mobile 1"},
                {"17012345678", "mobile", "Demo Blacklist - Virtual Number"},
                {"13912345678", "mobile", "Demo Blacklist - Mobile 2"},
                {"110101199001011234", "idno", "Demo Blacklist - ID No. 1"},
                {"310101198505054321", "idno", "Demo Blacklist - ID No. 2"}
        };
        for (String[] e : entries) {
            ListEntry le = new ListEntry();
            le.setListId(db.getId());
            le.setEntryValue(e[0]);
            le.setEntryType(e[1]);
            le.setRemark(e[2]);
            le.setStatus(1);
            le.setDeleted(0);
            le.setCreatedTime(now);
            le.setUpdatedTime(now);
            listEntryMapper.insert(le);
        }
        return db.getId();
    }

    /** Quota decision table (FIRST, 4 rows including a fallback reject; code is globally unique -> organ suffix) */
    private Integer insertDecisionTable(int organ, String suffix, LocalDateTime now) {
        DecisionTableEntity dt = new DecisionTableEntity();
        dt.setCode("DEMO_DT_QUOTA" + suffix);
        dt.setName("Demo Quota Decision Table");
        dt.setDescription("Quota plan by credit score; reject below 550 (demo data)");
        dt.setHitPolicy("FIRST");
        dt.setEngineId(null); // backfilled after the engine is created
        dt.setOrganId(organ);
        dt.setStatus(1);
        dt.setDeleted(0);
        dt.setCreatedTime(now);
        dt.setUpdatedTime(now);
        decisionTableMapper.insert(dt);

        DtColumnEntity cond = new DtColumnEntity();
        cond.setTableId(dt.getId());
        cond.setColType(1);
        cond.setFieldCode("f_CREDIT_SCORE");
        cond.setOperator("GE");
        cond.setTitle("Credit Score");
        cond.setSeq(1);
        cond.setDeleted(0);
        dtColumnMapper.insert(cond);

        DtColumnEntity out = new DtColumnEntity();
        out.setTableId(dt.getId());
        out.setColType(2);
        out.setTitle("Quota Plan");
        out.setSeq(2);
        out.setDeleted(0);
        dtColumnMapper.insert(out);

        String[][] rows = {
                {"1", null, "200000,8", "f_CREDIT_SCORE >= 750", "750"},
                {"2", null, "100000,12", "f_CREDIT_SCORE >= 650", "650"},
                {"3", null, "50000,18", "f_CREDIT_SCORE >= 550", "550"},
                {"4", "DENY", "0,0", "f_CREDIT_SCORE < 550 (fallback row)", "0"}
        };
        for (String[] r : rows) {
            DtRowEntity row = new DtRowEntity();
            row.setTableId(dt.getId());
            row.setRowNo(Integer.valueOf(r[0]));
            row.setResultType(r[1]);
            row.setResultValue(r[2]);
            row.setExpression(r[3]);
            row.setEnabled(1);
            row.setDeleted(0);
            row.setCreatedTime(now);
            row.setUpdatedTime(now);
            dtRowMapper.insert(row);

            DtCellEntity cell = new DtCellEntity();
            cell.setTableId(dt.getId());
            cell.setRowId(row.getId());
            cell.setColId(cond.getId());
            cell.setCellValue(r[4]);
            cell.setDeleted(0);
            dtCellMapper.insert(cell);
        }
        return dt.getId();
    }

    /** Engine + version (draft) + 5 nodes + 4 edges + node-knowledge relations; returns the version id */
    private Integer insertEngineGraph(int organ, String suffix, Map<String, Integer> rules,
                                      Integer cardId, Integer listId, Integer tableId, LocalDateTime now) {
        Engine engine = new Engine();
        engine.setCode("DEMO_LOAN" + suffix);
        engine.setName("Demo - Small Consumer Loan");
        engine.setDescription("Demo data: blacklist -> policy rules -> scorecard -> quota decision");
        engine.setStatus(1);
        engine.setOrganId(organ);
        engine.setCreatedTime(now);
        engine.setUpdatedTime(now);
        engineMapper.insert(engine);

        EngineVersion ver = new EngineVersion();
        ver.setEngineId(engine.getId());
        ver.setVersion(1);
        ver.setSubVersion(1);
        ver.setBootState(0); // draft; the standard publish flow sets 1
        ver.setStatus(1);
        ver.setLayout(0);
        ver.setCreatedTime(now);
        engineVersionMapper.insert(ver);

        // Backfill the decision table's engine; rules/scorecard keep engine_id=NULL (knowledge base visibility)
        DecisionTableEntity dtUpd = new DecisionTableEntity();
        dtUpd.setId(tableId);
        dtUpd.setEngineId(engine.getId());
        decisionTableMapper.updateById(dtUpd);

        // Nodes (next_nodes double-written with the edge table, consistent with the canvas save format)
        node(ver.getId(), "ND_START", "Start", 1, 0, null, 120, 260, "ND_BLACK", now);
        node(ver.getId(), "ND_BLACK", "Blacklist Screening", 5, 1, writeJson(listJson(listId)), 320, 260, "ND_POLICY", now);
        node(ver.getId(), "ND_POLICY", "Policy Rules", 2, 2, writeJson(policyJson(rules)), 520, 260, "ND_SCORE", now);
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardId", cardId);
        node(ver.getId(), "ND_SCORE", "Base Scorecard", 4, 3, writeJson(card), 720, 260, "ND_DECISION", now);
        Map<String, Object> dt = new LinkedHashMap<>();
        dt.put("decision_table_id", tableId);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("field_code", "f_LIMIT_RESULT");
        output.put("field_name", "Quota Plan");
        dt.put("output", output);
        node(ver.getId(), "ND_DECISION", "Quota Decision", 9, 4, writeJson(dt), 920, 260, "", now);

        edge(ver.getId(), "ND_START", "ND_BLACK", "Pass", 1, 1, now);
        edge(ver.getId(), "ND_BLACK", "ND_POLICY", "Pass", 1, 1, now);
        edge(ver.getId(), "ND_POLICY", "ND_SCORE", "Pass", 1, 1, now);
        edge(ver.getId(), "ND_SCORE", "ND_DECISION", "Pass", 1, 1, now);

        // Node-rule relations (engine loading fallback references)
        EngineNode policy = engineNodeMapper.selectOne(new LambdaQueryWrapper<EngineNode>()
                .eq(EngineNode::getVersionId, ver.getId())
                .eq(EngineNode::getNodeCode, "ND_POLICY")
                .last("limit 1"));
        for (Integer ruleId : rules.values()) {
            NodeKnowledgeRel rel = new NodeKnowledgeRel();
            rel.setNodeId(policy.getNodeId());
            rel.setKnowledgeId(ruleId);
            rel.setKnowledgeType(1);
            nodeKnowledgeRelMapper.insert(rel);
        }
        return ver.getId();
    }

    private void node(Integer versionId, String code, String name, int type, int order,
                      String nodeJson, int x, int y, String next, LocalDateTime now) {
        EngineNode n = new EngineNode();
        n.setVersionId(versionId);
        n.setNodeCode(code);
        n.setNodeName(name);
        n.setNodeType(type);
        n.setNodeOrder(order);
        n.setNodeJson(nodeJson);
        n.setNodeX(BigDecimal.valueOf(x));
        n.setNodeY(BigDecimal.valueOf(y));
        n.setNextNodes(next);
        engineNodeMapper.insert(n);
    }

    private void edge(Integer versionId, String from, String to, String label, int priority, int kind,
                      LocalDateTime now) {
        FlowEdgeEntity e = new FlowEdgeEntity();
        e.setVersionId(versionId);
        e.setFromCode(from);
        e.setToCode(to);
        e.setLabel(label);
        e.setPriority(priority);
        e.setEdgeKind(kind);
        e.setDeleted(0);
        e.setCreatedTime(now);
        e.setUpdatedTime(now);
        flowEdgeMapper.insert(e);
    }

    // ------------------------------------------------------------------ node_json assembly

    /** List node: {list_db_ids:[id], matchFields:[...]} */
    private Map<String, Object> listJson(Integer listId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("list_db_ids", Arrays.asList(listId));
        m.put("matchFields", Arrays.asList("f_MOBILE", "f_IDNO"));
        return m;
    }

    /** Rule node: deny_rules(5) + addOrSubRules(2), consistent with the canvas save format */
    private Map<String, Object> policyJson(Map<String, Integer> rules) {
        Map<String, Object> deny = new LinkedHashMap<>();
        deny.put("isSerial", 0);
        deny.put("rules", Arrays.asList(
                ruleRef(rules, "R_DEMO_AGE", 1),
                ruleRef(rules, "R_DEMO_CITY", 2),
                ruleRef(rules, "R_DEMO_MOBILE", 3),
                ruleRef(rules, "R_DEMO_YOUNG_NOCREDIT", 4),
                ruleRef(rules, "R_DEMO_MANUAL_AMT", 5)));
        Map<String, Object> addOrSub = new LinkedHashMap<>();
        addOrSub.put("threshold", 1.0);
        addOrSub.put("rules", Arrays.asList(
                ruleRef(rules, "R_DEMO_SUB_AGE", 1),
                ruleRef(rules, "R_DEMO_ADD_CITY", 2)));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deny_rules", deny);
        m.put("addOrSubRules", addOrSub);
        return m;
    }

    private Map<String, Object> ruleRef(Map<String, Integer> rules, String code, int priority) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rules.get(code));
        m.put("code", code);
        m.put("name", code);
        m.put("priority", priority);
        return m;
    }

    private String writeJson(Object obj) {
        try {
            return JSON.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize demo node_json", e);
        }
    }
}
