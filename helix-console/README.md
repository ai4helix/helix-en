# helix-console — SaaS 多租户风控决策引擎 · 控制台

> 本文档对应 2026-09 现状。历史口径

## 一、系统概览

helix 是一套 **SaaS 多租户的风控决策引擎**：租户在控制台配置决策流（规则/评分卡/决策表/名单），
发布后由引擎服务装载为内存快照对外提供实时决策。

| 模块 | 职责 | 端口（dev） |
|---|---|---|
| `helix-console` | 管理控制台后端：登录/用户/角色/组织、决策流编辑与发布、数据管理、批量跑批、平台运营 | 8086 |
| `helix-engine` | 决策引擎：快照装载 + 无锁执行 + 影子评估 + 决策日志 | 8087 |
| `helix-console-web` | 控制台前端（Vue3 + element-plus，Vite） | 5174 |
| `helix-feature` | 数据中台：特征取数 + 租户指标数据留存 | 8090 |
| `helix-app` / `helix-channel` / `helix-gateway` / `helix-facade` | 业务侧/渠道/网关/门面（本仓库内，非控制台范畴） | — |

## 二、数据库（双库）

v4 起 console 为**双数据源**（`DataSourceConfig` 按 Mapper 包路由）：

| 逻辑源 | 库 | 内容 |
|---|---|---|
| `sys` | `helix_console` | 用户/角色/资源/组织（`t_user`、`t_role`、`t_resource`、`t_organization`） |
| `engine` | `helix_engine` | 引擎配置 + 批量任务：`t_engine` 系列、`t_rule(+condition)`、`t_scorecard`、`t_knowledge_tree`、`t_field(+type)`、`t_list_db(+entry)`、`t_decision_table(+dt_*)`、`t_flow_edge/publish`、`t_indicator_batch(+item)` |

> ⚠️ 禁止跨库 JOIN；服务间数据访问走接口（见 `db/CONVENTIONS.md`）。
> 结构演进脚本在 `db/`（`v3-*.sql`、`v4-saas-schema.sql`、`v5-batch-schema.sql`）。

## 三、功能域（`com.helix.console.*`）

| 包 | 能力 | 主要接口 |
|---|---|---|
| `system` | 登录/JWT、用户/角色/资源/组织 CRUD、**租户手机注册** | `/api/auth/**`、`/api/system/**` |
| `engine` | 引擎/版本/画布保存/发布（灰度、影子、权重）/决策表 | `/api/engine/**` |
| `knowledge` | 规则（AST 条件树）、评分卡、知识树 | `/api/knowledge/**` |
| `datamanage` | 字段字典、名单库(+entry) | `/api/datamanage/**` |
| `result` | 决策日志/命中/轨迹查询（三表范式化） | `/api/result/**` |
| `batch` | **批量跑批**：字段导入、数据导入、发起执行、结果下载、模板下载、用户批量导入 | `/api/batch/**` |
| `platform` | **平台运营**（仅管理用户）：租户/角色/跑引擎统计 | `/api/platform/**` |

> 历史上的 `manage` 业务管理域（消费贷）已于 v4.1 整体下线，app 数据源一并移除。

## 四、SaaS 多租户（v4）

- **用户分型**：`t_user.user_type`（1=平台管理用户，2=SaaS 机构用户）。
- **租户隔离**：引擎配置表带 `organ_id`（0=平台公共，全租户可见）；统一由
  `system/security/TenantScope` 提供 `visibleOrgans/writeOrgan/checkVisible`。
- **租户注册**：手机号 + 短信验证码开通机构，默认绑定平台预配置的
  `ROLE_TENANT_ADMIN`（角色由平台管理员统一配置，租户管理员只能加用户）。
- **决策日志租户化**：`t_decision_log.organ_id` 由引擎写入时携带。

## 五、关键设计

- **快照即全部**（helix-engine）：配置装载为不可变 `EngineSnapshot`（volatile 引用整体替换），
  决策路径无锁读；一次决策固定一代快照（挂在 `DecisionContext`）。
- **发布即固化**：产物（AST/决策表配置）落 `t_flow_publish`，灰度/影子/权重切换不重建快照。
- **重载粒度**：全量 `reload` / 名单 `reloadLists` / 引擎级 `reloadEngine(s)`（含名单）。
  名单变更由 console 反查引用节点后**只通知受影响引擎**。
- **自愈**：`SnapshotSelfHealJob` 周期对账配置变更水位（`loadWatermark`），
  兜底「发布回调失败」；通知本身带 3 次退避重试。
- **规则求值**：装载期把条件编译为 AST（极性感知容错），执行期纯 static 求值，
  **已彻底移除 Drools**。

## 六、启动

```bash
# 依赖：MySQL（helix_console / helix_engine 双库）、Redis
# 1) 数据库结构：按 db/ 下 v1 全量 + v3/v4/v5 增量依次执行
# 2) 后端
cd helix-console && mvn spring-boot:run -Dspring-boot.run.profiles=dev     # 8086
cd helix-engine && mvn spring-boot:run -Dspring-boot.run.profiles=dev     # 8087
# 3) 前端
cd helix-console-web && npm install && npm run dev                        # 5174
```

配置均走 `application-dev.yml`（数据源/Redis/JWT/注册短信等），
引擎库账号需对 `helix_engine.*` 有完整权限（见 `helix-engine/.../application-dev.yml`）。

## 七、演示账号（密码均 `Init@1234`）

| 账号 | 角色 |
|---|---|
| `admin` | 平台管理员（平台运营方，仅见「平台运营」） |
| `13700000001` | 租户管理员（示例租户科技，= 注册手机号） |
| `strategist` / `approver` | 租户策略师 / 审批员 |

## 八、文档索引

| 文档 | 内容 |
|---|---|
| `db/ENGINE-SCHEMA-V3.md` | 引擎库 v3 结构与设计 |
| `db/RULE-ENGINE-DESIGN.md` | 规则引擎设计（AST 化） |
| `db/SAAS-DESIGN.md` | v4 多租户改造设计 |
| `db/v4-saas-schema.sql` / `db/v5-batch-schema.sql` | 结构演进脚本 |
| `db/CONVENTIONS.md` | 命名与跨库规范 |
