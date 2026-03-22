# 验收演示脚本（建议顺序）

> **Docker 一键启动：** 根目录执行 `docker compose up -d --build`，访问 `http://localhost:3000`；端口与排障见 [DEPLOY.md](./DEPLOY.md)。

1. **本地开发时** 启动 MySQL、Redis，导入 [schema.sql](../game-ai-backend/src/main/resources/schema.sql)；如需升级已有库，执行 [schema_migration_20260321.sql](../game-ai-backend/src/main/resources/schema_migration_20260321.sql)。
2. 启动 `game-ai-engine`（8000）、`game-ai-backend`（8080）、`game-ai-frontend`（5173）。（使用 Docker 时由 Compose 统一拉起，无需逐服务手动启。）
3. 浏览器打开 `http://localhost:5173`（本地开发）或 `http://localhost:3000`（Docker），使用 **admin / admin123** 登录（或 **commander / commander123** 仅演示指挥/对比）。
4. **场景配置**：查看或新建场景，在扩展 JSON 中配置 `obstacles`、`win_condition.max_steps`，观察二维预览网格。
5. **算法模板**：确认 MAPPO 模板及超参（含 `rollout_episodes`、`ppo_epochs`）。
6. **数据接入**：上传 CSV/JSON，或使用 `POST /api/datasets/ingest`（需 ENGINEER/ADMIN 角色）写入样本数据。
7. **智能体训练**：创建任务并启动；观察 WebSocket 实时曲线；若已有任务在训练，新任务自动 **排队**，完成后自动启动下一条。
8. **性能对比**：多选任务 ID，加载对比图，导出 HTML 报告（含策略检查点路径说明）。
9. **部署演示**：训练完成后在 `checkpoints` 目录存在 `policy_red_final.pt` / `policy_blue_final.pt`；可调用 `POST http://127.0.0.1:8000/api/infer/predict` 传入 `checkpoint_dir` 与 25 维观测向量进行推理。
10. **离线评估**：`POST http://127.0.0.1:8000/api/eval/rollout`，JSON 示例：`{"checkpoint_dir":"checkpoints/<task_id>","n_episodes":16,"seed":42}`，可选 `scene_config` 与训练时 `sceneMap` 结构一致。
