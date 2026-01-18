# Flink Triton Model Integration

This module provides integration between Apache Flink and NVIDIA Triton Inference Server, enabling real-time model inference within Flink streaming applications.

## Features

- **REST API Integration**: Communicates with Triton Inference Server via HTTP/REST API
- **Asynchronous Processing**: Non-blocking inference requests for high throughput
- **Flexible Configuration**: Comprehensive configuration options for various use cases
- **Multi-Type Support**: Supports various input/output data types (STRING, INT, FLOAT, DOUBLE, ARRAY, etc.)
- **Error Handling**: Built-in retry mechanisms and error handling
- **Resource Management**: Efficient HTTP client pooling and resource management

## Configuration Options

### Required Options

| Option | Type | Description |
|--------|------|-------------|
| `endpoint` | String | Base URL of the Triton Inference Server (e.g., `http://localhost:8000` or `http://localhost:8000/v2/models`). The integration will auto-complete to the full inference path. |
| `model-name` | String | Name of the model to invoke on Triton server |
| `model-version` | String | Version of the model to use (defaults to "latest") |

### Optional Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `timeout` | Long | 30000 | HTTP request timeout in milliseconds (connect + read + write). Separate from Flink's async timeout. |
| `max-retries` | Integer | 3 | Maximum retry attempts for connection failures (IOException). HTTP 4xx/5xx errors are NOT retried automatically. |
| `batch-size` | Integer | 1 | **Server-side batching hint** for Triton's dynamic batching. Does NOT trigger Flink-side batching. Each record is sent as a separate HTTP request. |
| `priority` | Integer | - | Request priority level (0-255, higher values = higher priority) |
| `sequence-id` | String | - | Sequence ID for stateful models |
| `sequence-start` | Boolean | false | Whether this is the start of a sequence for stateful models |
| `sequence-end` | Boolean | false | Whether this is the end of a sequence for stateful models |
| `binary-data` | Boolean | false | Whether to use binary data transfer (currently experimental, defaults to JSON) |
| `compression` | String | - | Compression algorithm to use (e.g., 'gzip') |
| `auth-token` | String | - | Authentication token for secured Triton servers |
| `custom-headers` | String | - | Custom HTTP headers in JSON format |

### Important Notes on Batching

The `batch-size` parameter is a **hint to Triton's server-side dynamic batching**, not a Flink-side batching mechanism:

- **Flink behavior**: Each input record triggers one HTTP request (1:1 mapping)
- **Triton behavior**: The server can aggregate multiple concurrent requests into a batch
- **For Flink-side batching**: Configure AsyncDataStream's `capacity` and `timeout` parameters when using async I/O operators

**Example:**
```java
// Flink async I/O configuration (affects Flink-side buffering)
AsyncDataStream.unorderedWait(
    dataStream,
    new AsyncModelFunction(),
    5000,      // timeout
    TimeUnit.MILLISECONDS,
    100        // capacity (max concurrent requests)
);
```

## Usage Example

### Basic Text Processing

```sql
CREATE MODEL my_triton_model (
  input STRING,
  output STRING
) WITH (
  'provider' = 'triton',
  'endpoint' = 'http://localhost:8000/v2/models',
  'model-name' = 'text-classification',
  'model-version' = '1',
  'timeout' = '10000',
  'max-retries' = '5'
);
```

### Image Classification with Array Input

```sql
CREATE MODEL image_classifier (
  image_data ARRAY<FLOAT>,
  predictions ARRAY<FLOAT>
) WITH (
  'provider' = 'triton',
  'endpoint' = 'http://localhost:8000/v2/models',
  'model-name' = 'resnet50',
  'model-version' = '1'
);
```

### Numeric Prediction

```sql
CREATE MODEL numeric_model (
  features ARRAY<DOUBLE>,
  score FLOAT
) WITH (
  'provider' = 'triton',
  'endpoint' = 'http://localhost:8000/v2/models',
  'model-name' = 'linear-regression',
  'model-version' = 'latest'
);
```

### Table API

```java
// Create table environment
TableEnvironment tableEnv = TableEnvironment.create(EnvironmentSettings.inStreamingMode());

// Register the model
tableEnv.executeSql(
    "CREATE MODEL my_triton_model (" +
    "  input STRING," +
    "  output STRING" +
    ") WITH (" +
    "  'provider' = 'triton'," +
    "  'endpoint' = 'http://localhost:8000/v2/models'," +
    "  'model-name' = 'text-classification'," +
    "  'model-version' = '1'" +
    ")"
);

// Use the model for inference
Table result = tableEnv.sqlQuery(
    "SELECT input, ML_PREDICT('my_triton_model', input) as prediction " +
    "FROM input_table"
);
```

## Supported Data Types

**Current Version (v1) Limitation**: Only single input column and single output column are supported per model.

The Triton integration supports the following Flink data types:

| Flink Type | Triton Type | Description |
|------------|-------------|-------------|
| `BOOLEAN` | `BOOL` | Boolean values |
| `TINYINT` | `INT8` | 8-bit signed integer |
| `SMALLINT` | `INT16` | 16-bit signed integer |
| `INT` | `INT32` | 32-bit signed integer |
| `BIGINT` | `INT64` | 64-bit signed integer |
| `FLOAT` | `FP32` | 32-bit floating point |
| `DOUBLE` | `FP64` | 64-bit floating point |
| `STRING` / `VARCHAR` | `BYTES` | String/text data |
| `ARRAY<T>` | `TYPE[]` | Array of any supported type |

### Multi-Tensor Models (Workarounds for v1)

If your Triton model requires multiple input tensors, consider these approaches:

1. **JSON Encoding**: Serialize multiple fields into a JSON STRING
2. **Array Packing**: Concatenate values into a single ARRAY<T>
3. **Future Support**: ROW<...> and MAP<...> types are planned for future releases

### Type Mapping Examples

```sql
-- String input/output (text processing)
CREATE MODEL text_model (
  text STRING,
  result STRING
) WITH ('provider' = 'triton', ...);

-- Array input/output (image processing, embeddings)
CREATE MODEL embedding_model (
  text STRING,
  embedding ARRAY<FLOAT>
) WITH ('provider' = 'triton', ...);

-- Numeric computation
CREATE MODEL regression_model (
  features ARRAY<DOUBLE>,
  prediction DOUBLE
) WITH ('provider' = 'triton', ...);
```

## Advanced Configuration

```sql
CREATE MODEL advanced_triton_model (
  input STRING,
  output STRING
) WITH (
  'provider' = 'triton',
  'endpoint' = 'https://triton.example.com/v2/models',
  'model-name' = 'advanced-nlp-model',
  'model-version' = 'latest',
  'timeout' = '15000',
  'max-retries' = '3',
  'batch-size' = '4',
  'priority' = '100',
  'auth-token' = 'your-auth-token-here',
  'custom-headers' = '{"X-Custom-Header": "custom-value"}',
  'compression' = 'gzip'
);
```

## Triton Server Setup

To use this integration, you need a running Triton Inference Server. Here's a basic setup:

### Using Docker

```bash
# Pull Triton server image
docker pull nvcr.io/nvidia/tritonserver:23.10-py3

# Run Triton server with your model repository
docker run --rm -p 8000:8000 -p 8001:8001 -p 8002:8002 \
  -v /path/to/your/model/repository:/models \
  nvcr.io/nvidia/tritonserver:23.10-py3 \
  tritonserver --model-repository=/models
```

### Model Repository Structure

```
model_repository/
├── text-classification/
│   ├── config.pbtxt
│   └── 1/
│       └── model.py  # or model.onnx, model.plan, etc.
└── other-model/
    ├── config.pbtxt
    └── 1/
        └── model.savedmodel/
```

## Error Handling

The integration includes comprehensive error handling:

- **Connection Errors**: Automatic retry with exponential backoff (OkHttp built-in)
- **Timeout Handling**: Configurable HTTP request timeout (default 30s)
- **HTTP Errors**: 4xx/5xx responses are NOT automatically retried
  - 400 Bad Request: Usually indicates shape/type mismatch
  - 404 Not Found: Model or version not available
  - 500 Internal Server Error: Triton inference failure
- **Serialization Errors**: JSON parsing and type validation errors

### Retry Behavior Matrix

| Error Type | Trigger | Flink Behavior | Triton Behavior |
|------------|---------|----------------|-----------------|
| Connection Timeout | Network issue | Fails async operation | N/A |
| HTTP Timeout | Slow inference | Fails after `timeout` ms | N/A |
| Connection Failure (IOException) | Network error | Retries up to `max-retries` | N/A |
| HTTP 4xx | Client error (bad input/shape) | No retry, fails immediately | Returns error JSON |
| HTTP 5xx | Server error (inference crash) | No retry, fails immediately | Returns error JSON |
| JSON Parse Error | Invalid response | No retry, fails immediately | N/A |

**Important**: Configure Flink's async timeout separately from HTTP timeout to avoid cascading failures:
```java
// Flink async timeout should be > HTTP timeout + retry overhead
AsyncDataStream.unorderedWait(stream, asyncFunc, 60000, TimeUnit.MILLISECONDS);
```

## Performance Considerations

- **Connection Pooling**: HTTP clients are shared across function instances with the same timeout/retry configuration (reference-counted singleton per JVM)
- **Asynchronous Processing**: Non-blocking requests prevent thread starvation
- **Batch Processing**: 
  - **Triton-side**: Enable dynamic batching in Triton's model config for optimal throughput
  - **Flink-side**: Configure AsyncDataStream capacity for concurrent request buffering
- **Resource Management**: Automatic cleanup of HTTP resources via reference counting

### Performance Tuning Tips

1. **Increase Async Capacity**: For high-throughput scenarios
   ```java
   AsyncDataStream.unorderedWait(stream, asyncFunc, timeout, TimeUnit.MILLISECONDS, 200); // capacity=200
   ```

2. **Enable Triton Dynamic Batching**: In model's `config.pbtxt`
   ```
   dynamic_batching {
     preferred_batch_size: [ 4, 8, 16 ]
     max_queue_delay_microseconds: 100
   }
   ```

3. **Tune Parallelism**: Match Flink parallelism to Triton server capacity
   ```java
   dataStream.map(...).setParallelism(10); // Adjust based on server resources
   ```

## Monitoring and Debugging

Enable debug logging to monitor the integration:

```properties
# In log4j2.properties
logger.triton.name = org.apache.flink.model.triton
logger.triton.level = DEBUG
```

This will provide detailed logs about:
- HTTP request/response details
- Client connection management
- Error conditions and retries
- Performance metrics

## Dependencies

This module includes the following key dependencies:
- OkHttp for HTTP client functionality (with connection pooling)
- Jackson for JSON processing
- Flink Table API for model integration

All dependencies are shaded to avoid conflicts with your application.

## Limitations and Future Work

### Current Limitations (v1)

1. **Single Input/Output Only**: Each model must have exactly one input column and one output column
2. **REST API Only**: Uses HTTP/REST protocol. gRPC is not yet supported
3. **No Flink-Side Batching**: Each record triggers a separate HTTP request (relies on Triton's server-side batching)
4. **Binary Data Mode**: Declared but not fully implemented (JSON only)

### Planned Enhancements (v2+)

- **Multi-Input/Output Support**: Using ROW<...> or MAP<...> types to map multiple Triton tensors
- **gRPC Protocol**: Native gRPC support for improved performance and streaming
- **Flink-Side Batching**: Optional aggregation of multiple records before sending to Triton
- **Binary Data Transfer**: Efficient binary serialization for large tensor data

**Feedback Welcome**: Please share your use cases and requirements via JIRA or mailing lists to help prioritize these features.
