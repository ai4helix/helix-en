# helix-console-web

信贷规则引擎前端（AntV X6 决策流画布）。与后端 `helix-console` 前后端分离部署。

## 技术栈

- Vue 3 + TypeScript + Vite 5
- AntV X6 2.x（画布内核，含 history/selection/snapline/keyboard/clipboard 插件）
- Element Plus
- Pinia + Vue Router

## 启动

```bash
npm install
npm run dev     # http://localhost:5374，/api 代理到 http://127.0.0.1:8286
npm run build   # 产物输出到 dist/
```

## 目录结构

```
src/
├── api/
│   ├── request.ts          axios 封装，统一处理 Result 结构
│   └── flow.ts             决策流接口
├── router/index.ts
├── types/flow.ts           与后端 DTO 对齐的类型定义
├── styles/global.scss
└── views/flow/
    ├── FlowDesigner.vue    画布主界面（工具栏 + 物料区 + 画布 + 属性面板）
    ├── graphSetup.ts       X6 实例创建、插件装配、快捷键绑定
    ├── nodeMeta.ts         节点类型元数据（驱动物料区/配色/尺寸）
    └── components/
        ├── FlowNode.vue        X6 自定义 Vue 节点
        └── NodePropsPanel.vue  节点属性编辑面板
```

## 与后端的契约

- 加载：`GET /api/engine/flow/graph/{versionId}` → `{ versionId, zoom, cells[] }`
- 保存：`POST /api/engine/flow/graph/save`，body 为 `{ versionId, graph, layout }`
- 坐标/连线：`POST /api/engine/flow/node/move`，前端 **400ms 防抖**后批量提交
- 发布：`POST /api/engine/flow/version/{versionId}/publish`

`cells` 结构完全对齐 X6 的 `graph.fromJSON()` / `toJSON()`，前端只做轻量映射。

## 扩展新节点类型

1. 后端 `NodeType` 枚举增加取值；
2. 前端 `src/views/flow/nodeMeta.ts` 的 `NODE_META` 追加一项；
3. 若需专属配置项，在 `NodePropsPanel.vue` 中按 `meta.type` 追加表单项。

无需改动画布核心逻辑。
