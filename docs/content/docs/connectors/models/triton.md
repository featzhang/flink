---
title: "Triton"
weight: 2
type: docs
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

# Triton

Triton 模型函数允许 Flink SQL 调用 [NVIDIA Triton 推理服务器](https://github.com/triton-inference-server/server) 进行实时模型推理任务。

## 概述

该函数支持通过 Flink SQL 调用远程 Triton 推理服务器进行预测/推理任务。Triton 推理服务器是一个高性能的推理服务解决方案，支持多种机器学习框架，包括 TensorFlow、PyTorch、ONNX 等。

主要特性：
* **高性能**：针对低延迟和高吞吐量推理进行优化
* **多框架支持**：支持来自各种机器学习框架的模型
* **异步处理**：非阻塞推理请求，提高资源利用率
* **灵活配置**：为不同用例提供全面的配置选项
* **资源管理**：高效的 HTTP 客户端池化和自动资源清理

## 使用示例

以下示例创建了一个用于文本分类的 Triton 模型，并使用它来分析电影评论中的情感。

首先，使用以下 SQL 语句创建 Triton 模型：

```sql
CREATE MODEL triton_sentiment_classifier
INPUT (`input` STRING)
OUTPUT (`output` STRING)
WITH (
    'provider' = 'triton',
    'endpoint' = 'http://localhost:8000/v2/models',
    'model-name' = 'text-classification',
    'model-version' = '1',
    'timeout' = '10000',
    'max-retries' = '3'
);
```

假设以下数据存储在名为 `movie_reviews` 的表中，预测结果将存储在名为 `classified_reviews` 的表中：

```sql
CREATE TEMPORARY VIEW movie_reviews(id, movie_name, user_review, actual_sentiment)
AS VALUES
  (1, '好电影', '这部电影绝对精彩！演技和故事情节都很棒。', 'positive'),
  (2, '无聊的电影', '我看到一半就睡着了。非常失望。', 'negative'),
  (3, '一般的电影', '还可以，没什么特别的，但也不算糟糕。', 'neutral');

CREATE TEMPORARY TABLE classified_reviews(
  id BIGINT,
  movie_name VARCHAR,
  predicted_sentiment VARCHAR,
  actual_sentiment VARCHAR
) WITH (
  'connector' = 'print'
);
```

然后可以使用以下 SQL 语句对电影评论进行情感分类：

```sql
INSERT INTO classified_reviews
SELECT id, movie_name, output as predicted_sentiment, actual_sentiment
FROM ML_PREDICT(
  TABLE movie_reviews,
  MODEL triton_sentiment_classifier,
  DESCRIPTOR(user_review)
);
```

### 高级配置示例

对于需要身份验证和自定义头部的生产环境：

```sql
CREATE MODEL triton_advanced_model
INPUT (`input` STRING)
OUTPUT (`output` STRING)
WITH (
    'provider' = 'triton',
    'endpoint' = 'https://triton.example.com/v2/models',
    'model-name' = 'advanced-nlp-model',
    'model-version' = 'latest',
    'timeout' = '15000',
    'max-retries' = '5',
    'batch-size' = '4',
    'priority' = '100',
    'auth-token' = 'Bearer your-auth-token-here',
    'custom-headers' = '{"X-Custom-Header": "custom-value", "X-Request-ID": "req-123"}',
    'compression' = 'gzip'
);
```

### 有状态模型示例

对于需要序列处理的有状态模型：

```sql
CREATE MODEL triton_sequence_model
INPUT (`input` STRING)
OUTPUT (`output` STRING)
WITH (
    'provider' = 'triton',
    'endpoint' = 'http://localhost:8000/v2/models',
    'model-name' = 'sequence-model',
    'model-version' = '1',
    'sequence-id' = 'seq-001',
    'sequence-start' = 'true',
    'sequence-end' = 'false'
);
```

## 模型选项

### 必需选项

<table class="table table-bordered">
    <thead>
        <tr>
            <th class="text-left" style="width: 25%">选项</th>
            <th class="text-center" style="width: 8%">是否必需</th>
            <th class="text-center" style="width: 7%">默认值</th>
            <th class="text-center" style="width: 10%">类型</th>
            <th class="text-center" style="width: 50%">描述</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td>
                <h5>provider</h5>
            </td>
            <td>必需</td>
            <td style="word-wrap: break-word;">(无)</td>
            <td>String</td>
            <td>指定要使用的模型函数提供者，必须为 'triton'。</td>
        </tr>
        <tr>
            <td>
                <h5>endpoint</h5>
            </td>
            <td>必需</td>
            <td style="word-wrap: break-word;">(无)</td>
            <td>String</td>
            <td>Triton 推理服务器端点的完整 URL，例如 <code>http://localhost:8000/v2/models</code>。</td>
        </tr>
        <tr>
            <td>
                <h5>model-name</h5>
            </td>
            <td>必需</td>
            <td style="word-wrap: break-word;">(无)</td>
            <td>String</td>
            <td>要在 Triton 服务器上调用的模型名称。</td>
        </tr>
        <tr>
            <td>
                <h5>model-version</h5>
            </td>
            <td>必需</td>
            <td style="word-wrap: break-word;">latest</td>
            <td>String</td>
            <td>要使用的模型版本。默认为 'latest'。</td>
        </tr>
    </tbody>
</table>

### 可选选项

<table class="table table-bordered">
    <thead>
        <tr>
            <th class="text-left" style="width: 25%">选项</th>
            <th class="text-center" style="width: 8%">是否必需</th>
            <th class="text-center" style="width: 7%">默认值</th>
            <th class="text-center" style="width: 10%">类型</th>
            <th class="text-center" style="width: 50%">描述</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td>
                <h5>timeout</h5>
            </td>
            <td>可选</td>
            <td style="word-wrap: break-word;">30000</td>
            <td>Long</td>
            <td>请求超时时间（毫秒）。</td>
        </tr>
        <tr>
            <td>
                <h5>max-retries</h5>
            </td>
            <td>可选</td>
            <td style="word-wrap: break-word;">3</td>
            <td>Integer</td>
            <td>失败请求的最大重试次数。</td>
        </tr>
        <tr>
            <td>
                <h5>batch-size</h5>
            </td>
            <td>可选</td>
            <td style="word-wrap: break-word;">1</td>
            <td>Integer</td>
            <td>推理请求的批处理大小。</td>
        </tr>
        <tr>
            <td>
                <h5>priority</h5>
            </td>
            <td>可选</td>
            <td style="word-wrap: break-word;">(无)</td>
            <td>Integer</td>
            <td>请求优先级（0-255）。数值越高表示优先级越高。</td>
        </tr>
        <tr>
            <td>
                <h5>sequence-id</h5>
            </td>
            <td>可选</td>
            <td style="word-wrap: break-word;">(无)</td>
            <td>String</td>
            <td>有状态模型的序列 ID。</td>
        </tr>
        <tr>
            <td>
                <h5>sequence-start</h5>
            </td>
            <td>可选</td>
            <td style="word-wrap: break-word;">false</td>
            <td>Boolean</td>
            <td>对于有状态模型，是否为序列的开始。</td>
        </tr>
        <tr>
            <td>
                <h5>sequence-end</h5>
            </td>
            <td>可选</td>
            <td style="word-wrap: break-word;">false</td>
            <td>Boolean</td>
            <td>对于有状态模型，是否为序列的结束。</td>
        </tr>
        <tr>
            <td>
                <h5>binary-data</h5>
            </td>
            <td>可选</td>
            <td style="word-wrap: break-word;">false</td>
            <td>Boolean</td>
            <td>是否使用二进制数据传输。默认为 false（JSON）。</td>
        </tr>
        <tr>
            <td>
                <h5>compression</h5>
            </td>
            <td>可选</td>
            <td style="word-wrap: break-word;">(无)</td>
            <td>String</td>
            <td>要使用的压缩算法（例如 'gzip'）。</td>
        </tr>
        <tr>
            <td>
                <h5>auth-token</h5>
            </td>
            <td>可选</td>
            <td style="word-wrap: break-word;">(无)</td>
            <td>String</td>
            <td>安全 Triton 服务器的身份验证令牌。</td>
        </tr>
        <tr>
            <td>
                <h5>custom-headers</h5>
            </td>
            <td>可选</td>
            <td style="word-wrap: break-word;">(无)</td>
            <td>String</td>
            <td>JSON 格式的自定义 HTTP 头部，例如 <code>{"X-Custom-Header":"value"}</code>。</td>
        </tr>
    </tbody>
</table>

## 模式要求

<table class="table table-bordered">
    <thead>
        <tr>
            <th class="text-center">输入类型</th>
            <th class="text-center">输出类型</th>
            <th class="text-left">描述</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td>STRING</td>
            <td>STRING</td>
            <td>通用文本到文本推理（分类、生成等）</td>
        </tr>
    </tbody>
</table>

## Triton 服务器设置

要使用此集成，您需要运行 Triton 推理服务器。以下是基本设置指南：

### 使用 Docker

```bash
# 拉取 Triton 服务器镜像
docker pull nvcr.io/nvidia/tritonserver:23.10-py3

# 使用您的模型仓库运行 Triton 服务器
docker run --rm -p 8000:8000 -p 8001:8001 -p 8002:8002 \
  -v /path/to/your/model/repository:/models \
  nvcr.io/nvidia/tritonserver:23.10-py3 \
  tritonserver --model-repository=/models
```

### 模型仓库结构

您的模型仓库应遵循以下结构：

```
model_repository/
├── text-classification/
│   ├── config.pbtxt
│   └── 1/
│       └── model.py  # 或 model.onnx, model.plan 等
└── other-model/
    ├── config.pbtxt
    └── 1/
        └── model.savedmodel/
```

### 示例模型配置

以下是文本分类模型的示例 `config.pbtxt`：

```protobuf
name: "text-classification"
platform: "python"
max_batch_size: 8
input [
  {
    name: "INPUT_TEXT"
    data_type: TYPE_STRING
    dims: [ 1 ]
  }
]
output [
  {
    name: "OUTPUT_TEXT"
    data_type: TYPE_STRING
    dims: [ 1 ]
  }
]
```

## 性能考虑

1. **连接池**：HTTP 客户端被池化和重用以提高效率
2. **异步处理**：非阻塞请求防止线程饥饿
3. **批处理**：配置批处理大小以获得最佳吞吐量
4. **资源管理**：HTTP 资源的自动清理
5. **超时配置**：根据模型复杂性设置适当的超时值
6. **重试策略**：配置重试次数以处理瞬态故障

## 错误处理

集成包括全面的错误处理：

- **连接错误**：使用指数退避的自动重试
- **超时处理**：可配置的请求超时
- **HTTP 错误**：来自 Triton 服务器的详细错误消息
- **序列化错误**：JSON 解析和验证错误

## 监控和调试

启用调试日志以监控集成：

```properties
# 在 log4j2.properties 中
logger.triton.name = org.apache.flink.model.triton
logger.triton.level = DEBUG
```

这将提供以下详细日志：
- HTTP 请求/响应详情
- 客户端连接管理
- 错误条件和重试
- 性能指标

## 依赖项

要使用 Triton 模型函数，您需要在 Flink 应用程序中包含以下依赖项：

```xml
<dependency>
    <groupId>org.apache.flink</groupId>
    <artifactId>flink-model-triton</artifactId>
    <version>${flink.version}</version>
</dependency>
```

{{< top >}}
