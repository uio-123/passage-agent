# P5 确定性并发压测

压测器面向 Docker `demo` profile 的持久化场景端点，默认矩阵为并发 `1/4/8/16`、每档预热 30 秒、测量 3 分钟、冷却 30 秒、重复 3 次。它测量项目的 HTTP、MySQL、事件与 Artifact 持久化链路，不代表真实模型吞吐。

```bash
node performance/run-load.mjs performance/scenarios/docker-demo-v1.json http://localhost:18123 performance/reports/<run-id>
```

每个请求按 60% 正常研究、20% 免研究、10% Reviewer 返工、10% 可恢复故障混合生成唯一场景 ID。报告包含环境指纹、逐轮结果和并发聚合，固定为 `DRAFT/NON_RELEASE`；正式性能数字仍需人工评测门禁解锁、固定环境并接受基线。

快速自测：

```bash
node --test performance/run-load.test.mjs
```

对已启动的 Demo 栈做短时链路冒烟（只验证压测器与并发档位，不形成容量基线）：

```bash
node performance/run-load.mjs performance/scenarios/docker-demo-smoke-v1.json http://localhost:18123 performance/reports/<draft-smoke-id>
```

报告目录不可覆盖。压测失败响应只记录受控错误摘要，不记录正文、Prompt、Cookie 或认证信息。
