/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.model.triton;

import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ConfigOptions;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.configuration.description.Description;
import org.apache.flink.table.catalog.Column;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.factories.ModelProviderFactory;
import org.apache.flink.table.functions.AsyncPredictFunction;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.types.logical.LogicalType;

import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static org.apache.flink.configuration.description.TextElement.code;

/** Abstract parent class for {@link AsyncPredictFunction}s for Triton Inference Server API. */
public abstract class AbstractTritonModelFunction extends AsyncPredictFunction {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractTritonModelFunction.class);

    public static final ConfigOption<String> ENDPOINT =
            ConfigOptions.key("endpoint")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            Description.builder()
                                    .text(
                                            "Full URL of the Triton Inference Server endpoint, e.g., %s",
                                            code("http://localhost:8000/v2/models"))
                                    .build());

    public static final ConfigOption<String> MODEL_NAME =
            ConfigOptions.key("model-name")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Name of the model to invoke on Triton server.");

    public static final ConfigOption<String> MODEL_VERSION =
            ConfigOptions.key("model-version")
                    .stringType()
                    .defaultValue("latest")
                    .withDescription("Version of the model to use. Defaults to 'latest'.");

    public static final ConfigOption<Long> TIMEOUT =
            ConfigOptions.key("timeout")
                    .longType()
                    .defaultValue(30000L)
                    .withDescription("Request timeout in milliseconds. Defaults to 30000ms.");

    public static final ConfigOption<Integer> MAX_RETRIES =
            ConfigOptions.key("max-retries")
                    .intType()
                    .defaultValue(3)
                    .withDescription(
                            "Maximum number of retries for failed requests. Defaults to 3.");

    public static final ConfigOption<Integer> BATCH_SIZE =
            ConfigOptions.key("batch-size")
                    .intType()
                    .defaultValue(1)
                    .withDescription("Batch size for inference requests. Defaults to 1.");

    public static final ConfigOption<Boolean> FLATTEN_BATCH_DIM =
            ConfigOptions.key("flatten-batch-dim")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription(
                            "Whether to flatten the batch dimension for array inputs. "
                                    + "When true, shape [1,N] becomes [N]. Defaults to false.");

    public static final ConfigOption<Integer> PRIORITY =
            ConfigOptions.key("priority")
                    .intType()
                    .noDefaultValue()
                    .withDescription(
                            "Request priority level (0-255). Higher values indicate higher priority.");

    public static final ConfigOption<String> SEQUENCE_ID =
            ConfigOptions.key("sequence-id")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Sequence ID for stateful models.");

    public static final ConfigOption<Boolean> SEQUENCE_START =
            ConfigOptions.key("sequence-start")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription(
                            "Whether this is the start of a sequence for stateful models.");

    public static final ConfigOption<Boolean> SEQUENCE_END =
            ConfigOptions.key("sequence-end")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("Whether this is the end of a sequence for stateful models.");

    public static final ConfigOption<Boolean> BINARY_DATA =
            ConfigOptions.key("binary-data")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription(
                            "Whether to use binary data transfer. Defaults to false (JSON).");

    public static final ConfigOption<String> COMPRESSION =
            ConfigOptions.key("compression")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Compression algorithm to use (e.g., 'gzip').");

    public static final ConfigOption<String> AUTH_TOKEN =
            ConfigOptions.key("auth-token")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Authentication token for secured Triton servers.");

    public static final ConfigOption<String> CUSTOM_HEADERS =
            ConfigOptions.key("custom-headers")
                    .stringType()
                    .noDefaultValue()
                    .withDescription(
                            "Custom HTTP headers in JSON format, e.g., '{\"X-Custom-Header\":\"value\"}'.");

    protected transient OkHttpClient httpClient;

    private final String endpoint;
    private final String modelName;
    private final String modelVersion;
    private final long timeout;
    private final int maxRetries;
    private final int batchSize;
    private final boolean flattenBatchDim;
    private final Integer priority;
    private final String sequenceId;
    private final boolean sequenceStart;
    private final boolean sequenceEnd;
    private final boolean binaryData;
    private final String compression;
    private final String authToken;
    private final String customHeaders;

    public AbstractTritonModelFunction(
            ModelProviderFactory.Context factoryContext, ReadableConfig config) {
        this.endpoint = config.get(ENDPOINT);
        this.modelName = config.get(MODEL_NAME);
        this.modelVersion = config.get(MODEL_VERSION);
        this.timeout = config.get(TIMEOUT);
        this.maxRetries = config.get(MAX_RETRIES);
        this.batchSize = config.get(BATCH_SIZE);
        this.flattenBatchDim = config.get(FLATTEN_BATCH_DIM);
        this.priority = config.get(PRIORITY);
        this.sequenceId = config.get(SEQUENCE_ID);
        this.sequenceStart = config.get(SEQUENCE_START);
        this.sequenceEnd = config.get(SEQUENCE_END);
        this.binaryData = config.get(BINARY_DATA);
        this.compression = config.get(COMPRESSION);
        this.authToken = config.get(AUTH_TOKEN);
        this.customHeaders = config.get(CUSTOM_HEADERS);

        // Validate input schema - support multiple types
        validateInputSchema(factoryContext.getCatalogModel().getResolvedInputSchema());
    }

    @Override
    public void open(FunctionContext context) throws Exception {
        super.open(context);
        LOG.debug("Creating Triton HTTP client.");
        this.httpClient = TritonUtils.createHttpClient(timeout, maxRetries);
    }

    @Override
    public void close() throws Exception {
        super.close();
        if (this.httpClient != null) {
            LOG.debug("Releasing Triton HTTP client.");
            TritonUtils.releaseHttpClient(this.httpClient);
            httpClient = null;
        }
    }

    /**
     * Validates the input schema. Subclasses can override for custom validation.
     *
     * @param schema The input schema to validate
     */
    protected void validateInputSchema(ResolvedSchema schema) {
        validateSingleColumnSchema(schema, null, "input");
    }

    /**
     * Validates that the schema has exactly one physical column, optionally checking the type.
     *
     * @param schema The schema to validate
     * @param expectedType The expected type, or null to skip type checking
     * @param inputOrOutput Description of whether this is input or output schema
     */
    protected void validateSingleColumnSchema(
            ResolvedSchema schema, LogicalType expectedType, String inputOrOutput) {
        List<Column> columns = schema.getColumns();
        if (columns.size() != 1) {
            throw new IllegalArgumentException(
                    String.format(
                            "Model should have exactly one %s column, but actually has %s columns: %s",
                            inputOrOutput,
                            columns.size(),
                            columns.stream().map(Column::getName).collect(Collectors.toList())));
        }

        Column column = columns.get(0);
        if (!column.isPhysical()) {
            throw new IllegalArgumentException(
                    String.format(
                            "%s column %s should be a physical column, but is a %s.",
                            inputOrOutput, column.getName(), column.getClass()));
        }

        if (expectedType != null && !expectedType.equals(column.getDataType().getLogicalType())) {
            throw new IllegalArgumentException(
                    String.format(
                            "%s column %s should be %s, but is a %s.",
                            inputOrOutput,
                            column.getName(),
                            expectedType,
                            column.getDataType().getLogicalType()));
        }

        // Validate that the type is supported by Triton
        try {
            TritonTypeMapper.toTritonDataType(column.getDataType().getLogicalType());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format(
                            "%s column %s has unsupported type %s for Triton: %s",
                            inputOrOutput,
                            column.getName(),
                            column.getDataType().getLogicalType(),
                            e.getMessage()));
        }
    }

    // Getters for configuration values
    protected String getEndpoint() {
        return endpoint;
    }

    protected String getModelName() {
        return modelName;
    }

    protected String getModelVersion() {
        return modelVersion;
    }

    protected long getTimeout() {
        return timeout;
    }

    protected int getMaxRetries() {
        return maxRetries;
    }

    protected int getBatchSize() {
        return batchSize;
    }

    protected boolean isFlattenBatchDim() {
        return flattenBatchDim;
    }

    protected Integer getPriority() {
        return priority;
    }

    protected String getSequenceId() {
        return sequenceId;
    }

    protected boolean isSequenceStart() {
        return sequenceStart;
    }

    protected boolean isSequenceEnd() {
        return sequenceEnd;
    }

    protected boolean isBinaryData() {
        return binaryData;
    }

    protected String getCompression() {
        return compression;
    }

    protected String getAuthToken() {
        return authToken;
    }

    protected String getCustomHeaders() {
        return customHeaders;
    }
}
