# Helix EN — SaaS Multi-Tenant Risk Decision Engine

English edition of the Helix risk decision engine. Tenants configure decision flows (rules / scorecards / decision tables / list DBs) in the console, publish them, and the engine service loads the published artifacts as immutable in-memory snapshots to serve real-time decisions over a REST API.

This repository is a fully English-localized fork: all code comments, user-facing messages, UI copy, seed data and database contents are in English. The sibling project `helix-cn` keeps the original Chinese edition for reference.

## Modules

| Module | Responsibility | Dev port |
|---|---|---|
| `helix-console` | Console backend: login/JWT, users / roles / organizations, decision flow editing & publishing, knowledge base, data management, batch runs, platform operations | `8286` |
| `helix-engine` | Decision engine: snapshot loading, lock-free execution, shadow evaluation, decision logging | `8287` |
| `helix-facade` | Inter-service contract layer: wire DTOs and Feign clients for channel / engine / feature calls | library |
| `helix-console-web` | Console frontend (Vue 3 + Element Plus + AntV X6, Vite) | dev `5374` |

## Tech Stack

**Backend** — Java 8 · Spring Boot · Spring Cloud (Nacos discovery/config, OpenFeign + Ribbon) · MyBatis-Plus · Druid · JWT · springdoc (Swagger UI at `/doc.html`)

**Frontend** — Vue 3 · TypeScript · Vite 5 · AntV X6 2.x (canvas with history / selection / snapline / keyboard / clipboard plugins) · Element Plus · Pinia · Vue Router

**Storage** — MySQL (two schemas) · Redis

## Architecture at a Glance

```
            ┌────────────────────┐
            │  helix-console-web │  Vue3 + X6 canvas
            └─────────┬──────────┘
                      │ /api (Vite proxy / nginx)
            ┌─────────▼──────────┐        ┌──────────────────┐
            │   helix-console    │──Feign─▶   helix-engine   │
            │  config plane      │        │  runtime plane   │
            │  (8286)            │        │  (8287)          │
            └───┬──────────┬─────┘        └────┬────────┬────┘
                │          │  publish callback │        │ decision logs
        ┌───────▼──┐   ┌───▼────────┐   ┌──────▼───┐ ┌──▼─────────┐
        │  sys DB  │   │ engine DB  │   │ engine DB│ │ engine DB  │
        └──────────┘   └────────────┘   └──────────┘ └────────────┘
        helix_en_console              helix_en_engine
        (users/roles/orgs/menus)      (engines/rules/scorecards/tables/
                                       lists/fields/publishes/logs/batch)
```

Service discovery uses Nacos (`127.0.0.1:8848`, namespace = active profile). The console reaches the engine by service name `helix-engine` through the facade `@FeignClient`.

## Feature Map

### Console backend (`com.helix.console.*`)

| Package | Capability | API prefix |
|---|---|---|
| `system` | Login/JWT, user / role / resource / organization CRUD, tenant phone registration | `/api/auth/**`, `/api/system/**` |
| `engine` | Engines / versions / canvas save & load / publishing (gray release, shadow, weighted routing) / decision tables | `/api/engine/**` |
| `knowledge` | Rules (AST condition trees), scorecards, knowledge tree | `/api/knowledge/**` |
| `datamanage` | Field dictionary, list DBs (blacklist / whitelist + entries) | `/api/datamanage/**` |
| `result` | Decision logs / hits / traces (three-table normalized queries) | `/api/result/**` |
| `batch` | Batch runs: field import, indicator data import, task execution, result & template download, user bulk import | `/api/batch/**` |
| `platform` | Platform operations (admin only): tenants, roles, per-tenant engine run statistics | `/api/platform/**` |

### Engine core (`com.helix.engine.*`)

- **Snapshot is everything** — configuration loads into an immutable `EngineSnapshot` (volatile whole-reference swap); the decision path is lock-free. One decision is pinned to one snapshot generation via `DecisionContext`.
- **Publish solidifies** — artifacts (compiled ASTs / decision table configs) are persisted to `t_flow_publish`; gray / shadow / weight switches do not rebuild snapshots.
- **Reload granularity** — full `reload`, list-only `reloadLists`, per-engine `reloadEngine(s)` (including lists). List changes notify only the engines that reference them.
- **Self-healing** — `SnapshotSelfHealJob` periodically reconciles the config change watermark (`loadWatermark`) as a fallback for failed publish callbacks; notifications retry with backoff.
- **Rule evaluation** — conditions compile to ASTs at load time (polarity-aware fault tolerance), evaluated by a pure-static evaluator at run time. No Drools.
- **Shadow evaluation** — run a candidate version against real traffic and log results without affecting the master decision.

## Multi-Tenancy (SaaS)

- **User types**: `t_user.user_type` — `1` platform admin, `2` SaaS organization user.
- **Tenant isolation**: engine configuration tables carry `organ_id` (`0` = platform-shared, visible to all tenants). Access is centralized in `system/security/TenantScope` (`visibleOrgans` / `writeOrgan` / `checkVisible`).
- **Tenant registration**: mobile + SMS code provisions an organization and binds the platform-preconfigured `ROLE_TENANT_ADMIN` (platform admins configure roles; tenant admins only manage users).
- **Tenant-scoped logs**: `t_decision_log.organ_id` is written by the engine on every decision.

## Database

Dual-schema, dual-datasource (`DataSourceConfig` routes by Mapper package):

| Logical source | Schema | Contents |
|---|---|---|
| `sys` | `helix_en_console` (7 tables) | users / roles / resources / organizations / feedback (`t_user`, `t_role`, `t_resource`, `t_organization`, ...) |
| `engine` | `helix_en_engine` (23 tables) | `t_engine(_version/_node)` series, `t_rule`, `t_scorecard`, `t_knowledge_tree`, `t_field(_type)`, `t_list_db(_entry)`, `t_decision_table(+dt_*)`, `t_flow_edge/publish`, `t_decision_log`, `t_rule_history`, `t_indicator_batch(+item)`, `t_engine_task` |

Do not JOIN across schemas; cross-schema access goes through service interfaces.

This repository ships ready-to-import dumps under `db/`:

- `db/helix_en_console-full.sql` — schema + English seed data (users, roles, menus, demo tenants)
- `db/helix_en_engine-full.sql` — schema + English demo content (9 demo engines with rules, scorecards, decision tables, list DBs, knowledge trees; run-history and batch tables are empty)

## Getting Started

### Prerequisites

- JDK 8 (build and runtime target Java 8)
- Maven 3.6+
- Node.js 18+ (frontend)
- MySQL 5.7+/8.0 and Redis, or Docker

### 1. Start infrastructure

```bash
# example: local docker containers for MySQL and Redis
docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=123456 mysql:8
docker run -d --name redis -p 6379:6379 redis:6
docker run -d --name nacos -p 8848:8848 -e MODE=standalone nacos/nacos-server:v2.2.3
```

Create a Nacos namespace named `dev` (services register into `namespace = spring.profiles.active`).

### 2. Initialize the databases

```bash
docker exec -i mysql mysql -uroot -p123456 -e "
  CREATE DATABASE helix_en_console DEFAULT CHARACTER SET utf8mb4;
  CREATE DATABASE helix_en_engine  DEFAULT CHARACTER SET utf8mb4;"
docker exec -i mysql mysql -uroot -p123456 helix_en_console < db/helix_en_console-full.sql
docker exec -i mysql mysql -uroot -p123456 helix_en_engine  < db/helix_en_engine-full.sql
```

### 3. Build and run the backend

```bash
mvn -DskipTests package

# console (port 8286)
cd helix-console && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# engine (port 8287) — in another terminal
cd helix-engine && mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Configuration lives in `application-dev.yml` of each service and is overridable by environment variables (`HELIX_DS_SYS_URL/USER/PASS`, `HELIX_DS_ENGINE_URL/USER/PASS`, `SPRING_REDIS_HOST/PORT/PASSWORD`, etc.).

### 4. Run the frontend

```bash
cd helix-console-web
npm install
npm run dev      # http://localhost:5374, /api proxied to http://127.0.0.1:8286
npm run build    # production bundle in dist/
```

### 5. Sign in

Open `http://localhost:5374` and use a demo account:

| Account | Role |
|---|---|
| `admin` | Platform admin (platform operator; sees Platform Operations only) |
| `13700000001` | Tenant admin (Demo Tenant Tech; username = registration mobile) |
| `strategist` | Tenant risk strategist (Demo Tenant Tech) |
| `approver` | Tenant approver (Demo Tenant Tech) |

Password for all demo accounts: `Init@1234`

## Testing

```bash
# backend unit tests (111 engine + 13 console)
mvn test

# frontend type-check + production build
cd helix-console-web && npm run build
```

## Documentation Index

| Path | Content |
|---|---|
| `helix-console/README.md` | Chinese console README (architecture, design decisions, DB conventions) |
| `helix-console-web/README.md` | Frontend README (X6 canvas contract, how to add node types) |
| `helix-engine/src/main/resources/db/schema.sql` | Engine schema DDL (English comments) |
| `helix-console/src/main/resources/db/` | Seed scripts (English): `data-seed.sql`, menu seeds, `schema.sql` |
| `db/helix_en_*.sql` | Full English dumps for both schemas |

## Extending

**Add a node type to the flow designer**

1. Add the value to the backend `NodeType` enum.
2. Append an entry to `NODE_META` in `helix-console-web/src/views/flow/nodeMeta.ts`.
3. If it needs dedicated options, extend the form in `NodePropsPanel.vue` by `meta.type`.

No canvas-core changes required. See `helix-console-web/README.md` for the graph save/load contract.

**Derived fields** — use formulas referencing other fields (`formula` + `used_fieldid`), compiled into the expression evaluator; enum fields carry `value_scope` labels in the form `1Label2Label3Label`.

## Notes & Gotchas

- **Maven coordinates collide with the original Helix project** (same `groupId:artifactId`). Do not run `mvn install` into the same local `.m2` used by the original Helix, or artifacts such as `com.helix:helix-engine` will be overwritten. Reactor builds (`mvn package` / `mvn test`) are unaffected.
- **Nacos service-name collision**: this project registers as `helix-console` / `helix-engine` — do not run the original Helix project in the same Nacos namespace at the same time, or Ribbon will load-balance across both systems.
- Ports are isolated from the original Helix (console `8286`, engine `8287`, frontend dev `5374`; Redis database `2`), so they can run side by side as long as service names / namespaces differ.
- `helix-console/src/main/resources/db/data-bank-loan.sql` is a legacy seed referencing old-table structures (`t_field_user_rel` etc.) that do not exist in the current 23-table engine schema; it is kept for reference and is not imported.
- The demo login SMS code is returned in the API response in dev mode (`helix.register.sms.debug-return: true`); disable in production.
