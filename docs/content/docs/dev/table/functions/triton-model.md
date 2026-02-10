---
title: "Triton Model Functions"
weight: 5
type: docs
aliases:
  - /dev/table/triton-model/
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

# Triton Model Functions

Flink provides seamless integration with [NVIDIA Triton Inference Server](https://developer.nvidia.com/triton-inference-server) through the `flink-model-triton` module. This integration enables real-time model inference within Flink streaming and batch jobs using SQL or Table API.

{{< hint info >}}
The `flink-model-triton` module is available since Flink 2.0 and requires the `flink-model-triton` dependency.
{{< /hint >}}

## Overview

Triton Inference Server is a high-performance inference serving software that supports multiple deep learning frameworks (TensorFlow, PyTorch, ONNX, TensorRT, etc.). The Flink Triton integration allows you to:

- **Invoke ML models** deployed on Triton servers directly from SQL queries
- **Stream inference** with low latency and high throughput
- **Batch prediction** on large datasets
- **Stateful models** support with sequence tracking
- **Automatic retries** with exponential backoff for fault tolerance
- **Metrics monitoring** for inference latency, success/failure rates

## Quick Start

### Prerequisites

1. **Add the dependency** to your project:

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

2. **Running Triton Server**: Ensure you have access to a running Triton Inference Server instance. See [Triton Quick Start](https://github.com/triton-inference-server/server/blob/main/docs/getting_started/quickstart.md) for setup instructions.

### Basic Example

Here's a simple example of using a Triton model for text classification:

{{< tabs "basic-example" >}}
{{< tab "SQL" >}}
```sql
-- Create a model function pointing to your Triton server
CREATE MODEL sentiment_analyzer WITH (
  'provider' = 'triton',
  'endpoint' = 'http://triton-server:8000/v2/models',
  'model-name' = 'sentiment_model',
  'model-version' = 'latest',
  'timeout' = '30s'
);

-- Use the model in a query
SELECT 
  review_id,
  review_text,
  sentiment_analyzer(review_text) AS sentiment_score
FROM product_reviews;
```
{{< /tab >}}
{{< tab "Table API (Java)" >}}
```java
// Create table environment
TableEnvironment tEnv = TableEnvironment.create(...);

// Create model with configuration
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

// Use the model
Table result = tEnv.sqlQuery(
    "SELECT review_id, review_text, " +
    "sentiment_analyzer(review_text) AS sentiment_score " +
    "FROM product_reviews"
);
```
{{< /tab >}}
{{< /tabs >}}

## Core Concepts

### Model Registration

Models must be registered using the `CREATE MODEL` statement before they can be used. The registration specifies how to connect to the Triton server and which model to invoke.

### Input/Output Mapping

Flink automatically maps Flink data types to Triton tensor types:

| Flink Type | Triton Type | Notes |
|------------|-------------|-------|
| `BOOLEAN` | `BOOL` | Single boolean value |
| `TINYINT`, `SMALLINT`, `INT` | `INT32` | Integer types |
| `BIGINT` | `INT64` | Long integer |
| `FLOAT` | `FP32` | Single precision float |
| `DOUBLE` | `FP64` | Double precision float |
| `STRING` | `BYTES` | UTF-8 encoded text |
| `ARRAY<T>` | Tensor with shape | Multi-dimensional arrays |

### Inference Modes

The Triton integration supports both:

- **Asynchronous inference** (default): Non-blocking requests with configurable parallelism
- **Synchronous inference**: Blocking requests for simpler control flow

## Configuration Options

### Required Options

| Option | Type | Description |
|--------|------|-------------|
| `provider` | String | Must be `'triton'` to use the Triton model provider |
| `endpoint` | String | Full URL of the Triton server endpoint, e.g., `http://triton-server:8000/v2/models` |
| `model-name` | String | Name of the model to invoke on the Triton server |

### Optional Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `model-version` | String | `'latest'` | Version of the model to use |
| `timeout` | Duration | `30s` | HTTP request timeout (connect + read + write) |
| `flatten-batch-dim` | Boolean | `false` | Whether to flatten the batch dimension for array inputs (shape [1,N] becomes [N]) |
| `priority` | Integer | - | Request priority level (0-255), higher values indicate higher priority |
| `sequence-id` | String | - | Sequence ID for stateful models |
| `sequence-id-auto-increment` | Boolean | `false` | Enable auto-increment for sequence IDs |
| `sequence-start` | Boolean | `false` | Mark request as the start of a sequence |
| `sequence-end` | Boolean | `false` | Mark request as the end of a sequence |
| `compression` | String | - | Compression algorithm for request body (currently only `gzip` supported) |
| `auth-token` | String | - | Authentication token for secured Triton servers |
| `custom-headers` | Map | - | Custom HTTP headers as key-value pairs |

## Usage Examples

### Text Classification

```sql
-- Create model
CREATE MODEL text_classifier WITH (
  'provider' = 'triton',
  'endpoint' = 'http://triton-server:8000/v2/models',
  'model-name' = 'bert_classifier',
  'timeout' = '10s'
);

-- Classify customer feedback
SELECT 
  customer_id,
  feedback_text,
  text_classifier(feedback_text) AS category,
  event_time
FROM customer_feedback;
```

### Image Classification

```sql
-- Create model for image processing
CREATE MODEL image_classifier WITH (
  'provider' = 'triton',
  'endpoint' = 'https://triton-server:8000/v2/models',
  'model-name' = 'resnet50',
  'model-version' = '1',
  'timeout' = '5s'
);

-- Classify images
SELECT 
  image_id,
  image_classifier(image_pixels) AS predicted_class,
  processing_time
FROM image_stream;
```

### Recommendation System

```sql
-- Create recommendation model
CREATE MODEL recommender WITH (
  'provider' = 'triton',
  'endpoint' = 'http://triton-server:8000/v2/models',
  'model-name' = 'product_recommender',
  'model-version' = '2'
);

-- Generate recommendations
SELECT 
  user_id,
  recommender(user_id, user_features, context) AS recommended_products
FROM user_activity;
```

### Fraud Detection

```sql
-- Create fraud detection model
CREATE MODEL fraud_detector WITH (
  'provider' = 'triton',
  'endpoint' = 'http://triton-server:8000/v2/models',
  'model-name' = 'fraud_detection_model',
  'timeout' = '2s',
  'priority' = '200'  -- High priority for critical transactions
);

-- Real-time fraud detection
SELECT 
  transaction_id,
  amount,
  fraud_detector(
    transaction_features,
    user_history,
    device_info
  ) AS fraud_score
FROM transactions
WHERE fraud_score > 0.8;  -- Alert on high-risk transactions
```

### Named Entity Recognition (NER)

```sql
-- Create NER model
CREATE MODEL ner_model WITH (
  'provider' = 'triton',
  'endpoint' = 'http://triton-server:8000/v2/models',
  'model-name' = 'bert_ner',
  'compression' = 'gzip'  -- Compress large text inputs
);

-- Extract entities from documents
SELECT 
  doc_id,
  ner_model(document_text) AS entities
FROM documents;
```

## Advanced Features

### Stateful Models and Sequence Tracking

For stateful models (e.g., RNN, LSTM) that maintain state across multiple requests, use sequence tracking:

```sql
-- Create stateful model with sequence tracking
CREATE MODEL conversation_model WITH (
  'provider' = 'triton',
  'endpoint' = 'http://triton-server:8000/v2/models',
  'model-name' = 'chatbot_rnn',
  'sequence-id' = 'conversation-${user_id}',
  'sequence-start' = 'true',
  'sequence-end' = 'false'
);
```

For automatic sequence ID generation with failure recovery:

```sql
CREATE MODEL stateful_predictor WITH (
  'provider' = 'triton',
  'endpoint' = 'http://triton-server:8000/v2/models',
  'model-name' = 'lstm_predictor',
  'sequence-id' = 'job-12345',
  'sequence-id-auto-increment' = 'true'
);
```

When `sequence-id-auto-increment` is enabled, the system appends a unique counter to the sequence ID in the format: `{sequence-id}-{subtask-index}-{counter}`. This ensures:
- **Sequence isolation** across parallel subtasks
- **Automatic recovery** after job failures (counter resets)
- **No duplicate requests** for non-reentrant models

### Fault Tolerance and Retry

The Triton integration includes built-in retry logic with exponential backoff:

- **Automatic retries** for transient errors (5xx server errors, network timeouts)
- **No retry** for client errors (4xx, invalid inputs)
- **Exponential backoff**: 100ms → 200ms → 400ms → 800ms
- **Default value fallback**: Return a default value after max retries exceeded

Example with retry configuration (note: explicit retry configuration will be added in future versions, currently uses default behavior):

```sql
-- Model with automatic retry (default behavior)
CREATE MODEL resilient_model WITH (
  'provider' = 'triton',
  'endpoint' = 'http://triton-server:8000/v2/models',
  'model-name' = 'my_model',
  'timeout' = '30s'
);
```

### Authentication and Security

For secured Triton servers, use authentication tokens:

```sql
CREATE MODEL secure_model WITH (
  'provider' = 'triton',
  'endpoint' = 'https://secure-triton:8000/v2/models',
  'model-name' = 'private_model',
  'auth-token' = '${SECRET:triton-token}',  -- Reference to secret
  'custom-headers' = MAP[
    'X-API-Key', 'your-api-key',
    'X-Client-ID', 'flink-job-123'
  ]
);
```

{{< hint warning >}}
Never hardcode sensitive tokens in SQL. Use Flink's secret management or environment variables.
{{< /hint >}}

### Request Compression

For large inputs (e.g., high-resolution images, long documents), enable compression to reduce network bandwidth:

```sql
CREATE MODEL image_model WITH (
  'provider' = 'triton',
  'endpoint' = 'http://triton-server:8000/v2/models',
  'model-name' = 'high_res_classifier',
  'compression' = 'gzip'  -- Compress request body
);
```

Compression is particularly beneficial when:
- Input data size > 1KB
- Network bandwidth is limited
- Latency is acceptable (compression adds ~5-10ms overhead)

## Monitoring and Metrics

The Triton model functions automatically expose the following metrics through Flink's metrics system:

| Metric | Type | Description |
|--------|------|-------------|
| `requests` | Counter | Total number of inference requests |
| `success` | Counter | Number of successful inferences |
| `failure` | Counter | Number of failed inferences |
| `latency` | Histogram | Inference latency distribution (milliseconds) |
| `output_rows` | Counter | Total number of output rows produced |

You can access these metrics through:
- Flink Web UI (Task Metrics tab)
- Metrics reporters (Prometheus, InfluxDB, etc.)
- REST API

Example Prometheus query:
```promql
rate(flink_taskmanager_job_task_operator_requests_total[5m])
```

## Best Practices

### Performance Optimization

1. **Connection Pooling**: The Triton client automatically reuses HTTP connections. Ensure your Triton server is configured for keep-alive connections.

2. **Batch Processing**: For high throughput, process records in mini-batches rather than one-by-one:
   ```sql
   -- Use windowing to create micro-batches
   SELECT 
     window_start,
     COLLECT_LIST(text) AS batch_texts,
     model(COLLECT_LIST(text)) AS batch_results
   FROM TABLE(
     TUMBLE(TABLE documents, DESCRIPTOR(event_time), INTERVAL '1' SECOND))
   GROUP BY window_start;
   ```

3. **Parallelism**: Configure parallelism based on Triton server capacity:
   ```java
   // Set appropriate parallelism
   env.setParallelism(16);
   ```

4. **Timeout Tuning**: Set timeout based on model complexity:
   - Simple models (e.g., linear regression): 1-5s
   - Medium models (e.g., BERT): 5-30s
   - Complex models (e.g., GPT): 30-120s

5. **Model Version Pinning**: Use specific versions in production:
   ```sql
   'model-version' = '3'  -- Pin to version 3, don't use 'latest'
   ```

### Error Handling

1. **Handle inference failures gracefully**:
   ```sql
   SELECT 
     user_id,
     COALESCE(model(features), -1.0) AS prediction  -- Default to -1 on failure
   FROM user_data;
   ```

2. **Monitor failure rates**: Set up alerts for high failure rates.

3. **Use filtering** to separate successful and failed predictions:
   ```sql
   -- Route failed predictions to a separate stream
   SELECT * FROM predictions WHERE prediction IS NOT NULL;
   SELECT * FROM predictions WHERE prediction IS NULL;
   ```

### Resource Management

1. **Memory**: Ensure sufficient memory for serialization/deserialization:
   ```yaml
   taskmanager.memory.managed.size: 2gb
   ```

2. **Network buffers**: Increase network buffers for high throughput:
   ```yaml
   taskmanager.network.memory.fraction: 0.2
   ```

3. **Async capacity**: Configure async operator capacity:
   ```java
   env.setMaxAsyncInflightRequests(100);  // Default is 100
   ```

## Troubleshooting

### Common Issues

#### Connection Refused

**Symptom**: `Connection refused` or `Failed to connect to Triton server`

**Solutions**:
- Verify Triton server is running: `curl http://triton-server:8000/v2/health/ready`
- Check network connectivity from Flink cluster to Triton server
- Verify endpoint URL is correct (including protocol: http/https)
- Check firewall rules and security groups

#### Timeout Errors

**Symptom**: `Request timed out after 30s`

**Solutions**:
- Increase timeout: `'timeout' = '60s'`
- Check Triton server logs for slow models
- Monitor Triton server resource usage (CPU/GPU)
- Consider model optimization or quantization

#### Type Mismatch Errors

**Symptom**: `Type mismatch: expected FLOAT but got INT32`

**Solutions**:
- Check model input/output schema: `curl http://triton-server:8000/v2/models/{model}/config`
- Cast Flink types explicitly: `CAST(value AS FLOAT)`
- Verify array dimensions match model expectations

#### Sequence Errors

**Symptom**: `Invalid sequence ID` or `Sequence not found`

**Solutions**:
- Ensure `sequence-id` is configured
- Use `sequence-start` and `sequence-end` correctly
- Enable `sequence-id-auto-increment` for automatic handling
- Check Triton server sequence timeout configuration

#### High Latency

**Symptom**: Inference takes longer than expected

**Solutions**:
- Enable request compression: `'compression' = 'gzip'`
- Check network latency between Flink and Triton
- Increase Triton server instance count
- Use dynamic batching in Triton
- Monitor Triton queue time and compute time

### Debugging Tips

1. **Enable detailed logging**:
   ```yaml
   rootLogger.level = DEBUG
   logger.triton.name = org.apache.flink.model.triton
   logger.triton.level = DEBUG
   ```

2. **Check Triton server logs**:
   ```bash
   docker logs triton-server
   ```

3. **Verify model availability**:
   ```bash
   curl http://triton-server:8000/v2/models/{model_name}/ready
   ```

4. **Test with sample request**:
   ```bash
   curl -X POST http://triton-server:8000/v2/models/{model_name}/infer \
     -H "Content-Type: application/json" \
     -d @sample_request.json
   ```

5. **Monitor metrics**: Check the Flink Web UI for request/failure counters and latency histograms.

## Limitations

- **Streaming models**: Currently, the integration sends individual requests per record. Batch optimization (sending multiple records in one request) is planned for future releases.
- **Model warmup**: Cold start latency may be high for the first request after job start or failover. Consider implementing warmup logic.
- **TLS configuration**: Custom TLS/SSL certificate configuration is not yet supported. Use trusted certificates or disable verification (not recommended for production).
- **Payload size**: Very large payloads (>10MB) may cause memory pressure. Consider chunking or preprocessing large inputs.

## Further Reading

- [Triton Inference Server Documentation](https://docs.nvidia.com/deeplearning/triton-inference-server/user-guide/docs/)
- [Triton Model Configuration](https://github.com/triton-inference-server/server/blob/main/docs/user_guide/model_configuration.md)
- [Triton Performance Analyzer](https://github.com/triton-inference-server/client/blob/main/src/c++/perf_analyzer/README.md)
- [Flink Async I/O]({{< ref "docs/dev/datastream/operators/asyncio" >}})
- [Flink Metrics]({{< ref "docs/ops/metrics" >}})

## Next Steps

- Explore [User-Defined Functions]({{< ref "docs/dev/table/functions/udfs" >}}) for custom preprocessing/postprocessing
- Learn about [Flink ML](https://nightlies.apache.org/flink/flink-ml-docs-master/) for training pipelines
- Check out [Table API & SQL]({{< ref "docs/dev/table/overview" >}}) for more query capabilities
