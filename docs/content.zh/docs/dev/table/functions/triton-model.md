---
title: "Triton 模型函数"
weight: 5
type: docs
aliases:
  - /zh/dev/table/triton-model/
---
<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Triton 模型函数

Flink 通过 `flink-model-triton` 模块提供了与 [NVIDIA Triton 推理服务器](https://developer.nvidia.com/triton-inference-server) 的无缝集成。此集成使得在 Flink 流式和批处理作业中使用 SQL 或 Table API 进行实时模型推理成为可能。

{{< hint info >}}
`flink-model-triton` 模块自 Flink 2.0 起可用，需要添加 `flink-model-triton` 依赖。
{{< /hint >}}

## 概览

Triton 推理服务器是一个高性能的推理服务软件，支持多种深度学习框架（TensorFlow、PyTorch、ONNX、TensorRT 等）。Flink Triton 集成允许您：

- **调用 ML 模型**：直接从 SQL 查询中调用部署在 Triton 服务器上的模型
- **流式推理**：实现低延迟、高吞吐量的推理
- **批量预测**：对大规模数据集进行预测
- **有状态模型支持**：通过序列跟踪支持有状态模型
- **自动重试**：使用指数退避策略实现容错
- **指标监控**：监控推理延迟、成功/失败率

## 快速开始

### 前置条件

1. **添加依赖**到您的项目：

{{< tabs "triton-dependency" >}}
{{< tab "Maven" >}}
```xml
<dependency>
    <groupId>org.apache.flink</groupId>
    <artifactId>flink-model-triton</artifactId>
    <version>{{< version >}}</version>
</dependency>
```
{{< /tab >}}
{{< tab "Gradle" >}}
```gradle
implementation 'org.apache.flink:flink-model-triton:{{< version >}}'
```
{{< /tab >}}
{{< /tabs >}}

2. **运行 Triton 服务器**：确保您可以访问正在运行的 Triton 推理服务器实例。请参阅 [Triton 快速开始](https://github.com/triton-inference-server/server/blob/main/docs/getting_started/quickstart.md) 了解安装说明。

### 基本示例

以下是使用 Triton 模型进行文本分类的简单示例：

{{< tabs "basic-example" >}}
{{< tab "SQL" >}}
```sql
-- 创建指向 Triton 服务器的模型函数
CREATE MODEL sentiment_analyzer WITH (
  'provider' = 'triton',
  'endpoint' = 'http://triton-server:8000/v2/models',
  'model-name' = 'sentiment_model',
  'model-version' = 'latest',
  'timeout' = '30s'
);

-- 在查询中使用模型
SELECT 
  review_id,
  review_text,
  sentiment_analyzer(review_text) AS sentiment_score
FROM product_reviews;
```
{{< /tab >}}
{{< tab "Table API (Java)" >}}
```java
// 创建表环境
TableEnvironment tEnv = TableEnvironment.create(...);

// 创建带配置的模型
Map<String, String> config = new HashMap<>();
config.put("provider", "triton");
config.put("endpoint", "http://triton-server:8000/v2/models");
config.put("model-name", "sentiment_model");
config.put("model-version", "latest");
config.put("timeout", "30s");

tEnv.executeSql(
    "CREATE MODEL sentiment_analyzer WITH " +
    "('provider' = 'triton', " +
    " 'endpoint' = 'http://triton-server:8000/v2/models', " +
    " 'model-name' = 'sentiment_model', " +
    " 'model-version' = 'latest', " +
    " 'timeout' = '30s')"
);

// 使用模型
Table result = tEnv.sqlQuery(
    "SELECT review_id, review_text, " +
    "sentiment_analyzer(review_text) AS sentiment_score " +
    "FROM product_reviews"
);
```
{{< /tab >}}
{{< /tabs >}}

## 核心概念

### 模型注册

在使用模型之前，必须使用 `CREATE MODEL` 语句进行注册。注册指定了如何连接到 Triton 服务器以及要调用哪个模型。

### 输入/输出映射

Flink 自动将 Flink 数据类型映射到 Triton 张量类型：

| Flink 类型 | Triton 类型 | 说明 |
|-----------|------------|------|
| `BOOLEAN` | `BOOL` | 单个布尔值 |
| `TINYINT`, `SMALLINT`, `INT` | `INT32` | 整数类型 |
| `BIGINT` | `INT64` | 长整数 |
| `FLOAT` | `FP32` | 单精度浮点数 |
| `DOUBLE` | `FP64` | 双精度浮点数 |
| `STRING` | `BYTES` | UTF-8 编码文本 |
| `ARRAY<T>` | 带 shape 的张量 | 多维数组 |

### 推理模式

Triton 集成支持：

- **异步推理**（默认）：非阻塞请求，可配置并行度
- **同步推理**：阻塞请求，控制流更简单

## 配置选项

### 必需选项

| 选项 | 类型 | 描述 |
|-----|-----|------|
| `provider` | String | 必须为 `'triton'` 以使用 Triton 模型提供者 |
| `endpoint` | String | Triton 服务器端点的完整 URL，例如 `http://triton-server:8000/v2/models` |
| `model-name` | String | 要在 Triton 服务器上调用的模型名称 |

### 可选选项

| 选项 | 类型 | 默认值 | 描述 |
|-----|-----|--------|------|
| `model-version` | String | `'latest'` | 要使用的模型版本 |
| `timeout` | Duration | `30s` | HTTP 请求超时（连接 + 读取 + 写入） |
| `flatten-batch-dim` | Boolean | `false` | 是否展平数组输入的批次维度（shape [1,N] 变为 [N]） |
| `priority` | Integer | - | 请求优先级（0-255），值越高优先级越高 |
| `sequence-id` | String | - | 有状态模型的序列 ID |
| `sequence-id-auto-increment` | Boolean | `false` | 启用序列 ID 自动递增 |
| `sequence-start` | Boolean | `false` | 标记请求为序列开始 |
| `sequence-end` | Boolean | `false` | 标记请求为序列结束 |
| `compression` | String | - | 请求体压缩算法（目前仅支持 `gzip`） |
| `auth-token` | String | - | 受保护 Triton 服务器的认证令牌 |
| `custom-headers` | Map | - | 自定义 HTTP 头，键值对形式 |

## 使用示例

### 文本分类

```sql
-- 创建模型
CREATE MODEL text_classifier WITH (
  'provider' = 'triton',
  'endpoint' = 'http://triton-server:8000/v2/models',
  'model-name' = 'bert_classifier',
  'timeout' = '10s'
);

-- 分类客户反馈
SELECT 
  customer_id,
  feedback_text,
  text_classifier(feedback_text) AS category,
  event_time
FROM customer_feedback;
```

### 图像分类

```sql
-- 创建图像处理模型
CREATE MODEL image_classifier WITH (
  'provider' = 'triton',
  'endpoint' = 'https://triton-server:8000/v2/models',
  'model-name' = 'resnet50',
  'model-version' = '1',
  'timeout' = '5s'
);

-- 分类图像
SELECT 
  image_id,
  image_classifier(image_pixels) AS predicted_class,
  processing_time
FROM image_stream;
```

### 推荐系统

```sql
-- 创建推荐模型
CREATE MODEL recommender WITH (
  'provider' = 'triton',
  'endpoint' = 'http://triton-server:8000/v2/models',
  'model-name' = 'product_recommender',
  'model-version' = '2'
);

-- 生成推荐
SELECT 
  user_id,
  recommender(user_id, user_features, context) AS recommended_products
FROM user_activity;
```

### 欺诈检测

```sql
-- 创建欺诈检测模型
CREATE MODEL fraud_detector WITH (
  'provider' = 'triton',
  'endpoint' = 'http://triton-server:8000/v2/models',
  'model-name' = 'fraud_detection_model',
  'timeout' = '2s',
  'priority' = '200'  -- 关键交易的高优先级
);

-- 实时欺诈检测
SELECT 
  transaction_id,
  amount,
  fraud_detector(
    transaction_features,
    user_history,
    device_info
  ) AS fraud_score
FROM transactions
WHERE fraud_score > 0.8;  -- 对高风险交易报警
```

### 命名实体识别 (NER)

```sql
-- 创建 NER 模型
CREATE MODEL ner_model WITH (
  'provider' = 'triton',
  'endpoint' = 'http://triton-server:8000/v2/models',
  'model-name' = 'bert_ner',
  'compression' = 'gzip'  -- 压缩大文本输入
);

-- 从文档中提取实体
SELECT 
  doc_id,
  ner_model(document_text) AS entities
FROM documents;
```

## 高级特性

### 有状态模型和序列跟踪

对于需要跨多个请求维护状态的有状态模型（如 RNN、LSTM），使用序列跟踪：

```sql
-- 创建带序列跟踪的有状态模型
CREATE MODEL conversation_model WITH (
  'provider' = 'triton',
  'endpoint' = 'http://triton-server:8000/v2/models',
  'model-name' = 'chatbot_rnn',
  'sequence-id' = 'conversation-${user_id}',
  'sequence-start' = 'true',
  'sequence-end' = 'false'
);
```

对于具有故障恢复的自动序列 ID 生成：

```sql
CREATE MODEL stateful_predictor WITH (
  'provider' = 'triton',
  'endpoint' = 'http://triton-server:8000/v2/models',
  'model-name' = 'lstm_predictor',
  'sequence-id' = 'job-12345',
  'sequence-id-auto-increment' = 'true'
);
```

当启用 `sequence-id-auto-increment` 时，系统会以 `{sequence-id}-{subtask-index}-{counter}` 的格式为序列 ID 追加唯一计数器。这确保了：
- 并行子任务间的**序列隔离**
- 作业失败后的**自动恢复**（计数器重置）
- 非可重入模型的**无重复请求**

### 容错和重试

Triton 集成包含带指数退避的内置重试逻辑：

- 对瞬态错误（5xx 服务器错误、网络超时）**自动重试**
- 对客户端错误（4xx、无效输入）**不重试**
- **指数退避**：100ms → 200ms → 400ms → 800ms
- **默认值回退**：超过最大重试次数后返回默认值

带重试配置的示例（注意：未来版本将添加显式重试配置，目前使用默认行为）：

```sql
-- 带自动重试的模型（默认行为）
CREATE MODEL resilient_model WITH (
  'provider' = 'triton',
  'endpoint' = 'http://triton-server:8000/v2/models',
  'model-name' = 'my_model',
  'timeout' = '30s'
);
```

### 认证和安全

对于受保护的 Triton 服务器，使用认证令牌：

```sql
CREATE MODEL secure_model WITH (
  'provider' = 'triton',
  'endpoint' = 'https://secure-triton:8000/v2/models',
  'model-name' = 'private_model',
  'auth-token' = '${SECRET:triton-token}',  -- 引用密钥
  'custom-headers' = MAP[
    'X-API-Key', 'your-api-key',
    'X-Client-ID', 'flink-job-123'
  ]
);
```

{{< hint warning >}}
切勿在 SQL 中硬编码敏感令牌。使用 Flink 的密钥管理或环境变量。
{{< /hint >}}

### 请求压缩

对于大型输入（如高分辨率图像、长文档），启用压缩以减少网络带宽：

```sql
CREATE MODEL image_model WITH (
  'provider' = 'triton',
  'endpoint' = 'http://triton-server:8000/v2/models',
  'model-name' = 'high_res_classifier',
  'compression' = 'gzip'  -- 压缩请求体
);
```

在以下情况下压缩特别有益：
- 输入数据大小 > 1KB
- 网络带宽有限
- 延迟可接受（压缩增加约 5-10ms 开销）

## 监控和指标

Triton 模型函数通过 Flink 的指标系统自动暴露以下指标：

| 指标 | 类型 | 描述 |
|-----|-----|------|
| `requests` | Counter | 推理请求总数 |
| `success` | Counter | 成功推理的数量 |
| `failure` | Counter | 失败推理的数量 |
| `latency` | Histogram | 推理延迟分布（毫秒） |
| `output_rows` | Counter | 产生的输出行总数 |

您可以通过以下方式访问这些指标：
- Flink Web UI（任务指标选项卡）
- 指标报告器（Prometheus、InfluxDB 等）
- REST API

Prometheus 查询示例：
```promql
rate(flink_taskmanager_job_task_operator_requests_total[5m])
```

## 最佳实践

### 性能优化

1. **连接池**：Triton 客户端自动重用 HTTP 连接。确保您的 Triton 服务器配置了 keep-alive 连接。

2. **批处理**：为了实现高吞吐量，以小批量而不是逐条处理记录：
   ```sql
   -- 使用窗口创建微批次
   SELECT 
     window_start,
     COLLECT_LIST(text) AS batch_texts,
     model(COLLECT_LIST(text)) AS batch_results
   FROM TABLE(
     TUMBLE(TABLE documents, DESCRIPTOR(event_time), INTERVAL '1' SECOND))
   GROUP BY window_start;
   ```

3. **并行度**：根据 Triton 服务器容量配置并行度：
   ```java
   // 设置适当的并行度
   env.setParallelism(16);
   ```

4. **超时调优**：根据模型复杂度设置超时：
   - 简单模型（如线性回归）：1-5s
   - 中等模型（如 BERT）：5-30s
   - 复杂模型（如 GPT）：30-120s

5. **模型版本固定**：在生产环境中使用特定版本：
   ```sql
   'model-version' = '3'  -- 固定到版本 3，不要使用 'latest'
   ```

### 错误处理

1. **优雅处理推理失败**：
   ```sql
   SELECT 
     user_id,
     COALESCE(model(features), -1.0) AS prediction  -- 失败时默认为 -1
   FROM user_data;
   ```

2. **监控失败率**：为高失败率设置告警。

3. **使用过滤**分离成功和失败的预测：
   ```sql
   -- 将失败的预测路由到单独的流
   SELECT * FROM predictions WHERE prediction IS NOT NULL;
   SELECT * FROM predictions WHERE prediction IS NULL;
   ```

### 资源管理

1. **内存**：确保有足够的内存用于序列化/反序列化：
   ```yaml
   taskmanager.memory.managed.size: 2gb
   ```

2. **网络缓冲区**：为高吞吐量增加网络缓冲区：
   ```yaml
   taskmanager.network.memory.fraction: 0.2
   ```

3. **异步容量**：配置异步算子容量：
   ```java
   env.setMaxAsyncInflightRequests(100);  // 默认为 100
   ```

## 故障排查

### 常见问题

#### 连接被拒绝

**症状**：`Connection refused` 或 `Failed to connect to Triton server`

**解决方案**：
- 验证 Triton 服务器是否正在运行：`curl http://triton-server:8000/v2/health/ready`
- 检查从 Flink 集群到 Triton 服务器的网络连通性
- 验证端点 URL 是否正确（包括协议：http/https）
- 检查防火墙规则和安全组

#### 超时错误

**症状**：`Request timed out after 30s`

**解决方案**：
- 增加超时：`'timeout' = '60s'`
- 检查 Triton 服务器日志中的慢模型
- 监控 Triton 服务器资源使用情况（CPU/GPU）
- 考虑模型优化或量化

#### 类型不匹配错误

**症状**：`Type mismatch: expected FLOAT but got INT32`

**解决方案**：
- 检查模型输入/输出 schema：`curl http://triton-server:8000/v2/models/{model}/config`
- 显式转换 Flink 类型：`CAST(value AS FLOAT)`
- 验证数组维度是否符合模型期望

#### 序列错误

**症状**：`Invalid sequence ID` 或 `Sequence not found`

**解决方案**：
- 确保配置了 `sequence-id`
- 正确使用 `sequence-start` 和 `sequence-end`
- 启用 `sequence-id-auto-increment` 进行自动处理
- 检查 Triton 服务器序列超时配置

#### 高延迟

**症状**：推理时间超过预期

**解决方案**：
- 启用请求压缩：`'compression' = 'gzip'`
- 检查 Flink 和 Triton 之间的网络延迟
- 增加 Triton 服务器实例数量
- 在 Triton 中使用动态批处理
- 监控 Triton 队列时间和计算时间

### 调试技巧

1. **启用详细日志**：
   ```yaml
   rootLogger.level = DEBUG
   logger.triton.name = org.apache.flink.model.triton
   logger.triton.level = DEBUG
   ```

2. **检查 Triton 服务器日志**：
   ```bash
   docker logs triton-server
   ```

3. **验证模型可用性**：
   ```bash
   curl http://triton-server:8000/v2/models/{model_name}/ready
   ```

4. **使用示例请求测试**：
   ```bash
   curl -X POST http://triton-server:8000/v2/models/{model_name}/infer \
     -H "Content-Type: application/json" \
     -d @sample_request.json
   ```

5. **监控指标**：检查 Flink Web UI 中的请求/失败计数器和延迟直方图。

## 限制

- **流式模型**：目前，集成为每条记录发送单独的请求。批量优化（在一个请求中发送多条记录）计划在未来版本中实现。
- **模型预热**：作业启动或故障恢复后首次请求的冷启动延迟可能较高。考虑实现预热逻辑。
- **TLS 配置**：尚不支持自定义 TLS/SSL 证书配置。使用受信任的证书或禁用验证（不建议用于生产）。
- **负载大小**：非常大的负载（>10MB）可能导致内存压力。考虑分块或预处理大型输入。

## 延伸阅读

- [Triton 推理服务器文档](https://docs.nvidia.com/deeplearning/triton-inference-server/user-guide/docs/)
- [Triton 模型配置](https://github.com/triton-inference-server/server/blob/main/docs/user_guide/model_configuration.md)
- [Triton 性能分析器](https://github.com/triton-inference-server/client/blob/main/src/c++/perf_analyzer/README.md)
- [Flink 异步 I/O]({{< ref "docs/dev/datastream/operators/asyncio" >}})
- [Flink 指标]({{< ref "docs/ops/metrics" >}})

## 下一步

- 探索[用户自定义函数]({{< ref "docs/dev/table/functions/udfs" >}})进行自定义预处理/后处理
- 了解 [Flink ML](https://nightlies.apache.org/flink/flink-ml-docs-master/) 的训练流水线
- 查看 [Table API & SQL]({{< ref "docs/dev/table/overview" >}}) 了解更多查询功能
