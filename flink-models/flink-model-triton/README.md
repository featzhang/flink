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
| `endpoint` | String | Full URL of the Triton Inference Server endpoint (e.g., `http://localhost:8000/v2/models`) |
| `model-name` | String | Name of the model to invoke on Triton server |
| `model-version` | String | Version of the model to use (defaults to "latest") |

### Optional Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `timeout` | Long | 30000 | Request timeout in milliseconds |
| `max-retries` | Integer | 3 | Maximum number of retries for failed requests |
| `batch-size` | Integer | 1 | Batch size for inference requests |
| `priority` | Integer | - | Request priority level (0-255, higher values = higher priority) |
| `sequence-id` | String | - | Sequence ID for stateful models |
| `sequence-start` | Boolean | false | Whether this is the start of a sequence for stateful models |
| `sequence-end` | Boolean | false | Whether this is the end of a sequence for stateful models |
| `binary-data` | Boolean | false | Whether to use binary data transfer (defaults to JSON) |
| `compression` | String | - | Compression algorithm to use (e.g., 'gzip') |
| `auth-token` | String | - | Authentication token for secured Triton servers |
| `custom-headers` | String | - | Custom HTTP headers in JSON format |

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

- **Connection Errors**: Automatic retry with exponential backoff
- **Timeout Handling**: Configurable request timeouts
- **HTTP Errors**: Detailed error messages from Triton server
- **Serialization Errors**: JSON parsing and validation errors

## Performance Considerations

- **Connection Pooling**: HTTP clients are pooled and reused for efficiency
- **Asynchronous Processing**: Non-blocking requests prevent thread starvation
- **Batch Processing**: Configure batch size for optimal throughput
- **Resource Management**: Automatic cleanup of HTTP resources

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
- OkHttp for HTTP client functionality
- Jackson for JSON processing
- Flink Table API for model integration

All dependencies are shaded to avoid conflicts with your application.
