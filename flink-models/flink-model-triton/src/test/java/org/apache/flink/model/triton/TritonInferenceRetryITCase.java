/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.model.triton;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.internal.TableEnvironmentImpl;
import org.apache.flink.table.catalog.CatalogManager;
import org.apache.flink.table.catalog.CatalogModel;
import org.apache.flink.table.catalog.ObjectIdentifier;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end integration tests for retry and default-value fallback in {@link
 * TritonInferenceModelFunction}.
 *
 * <p>These tests drive the full {@code ML_PREDICT} pipeline against a {@link MockWebServer} and
 * verify the externally-observable HTTP behaviour: retry attempt counts, terminal fallback, and
 * non-retryable status codes. Unit-level coverage of individual helpers (backoff math, breaker
 * accounting, default-value parsing) lives in their dedicated test classes; this class exists to
 * prove that the helpers are actually wired into the request pipeline correctly.
 */
class TritonInferenceRetryITCase {

    private static final String MODEL_NAME = "triton_retry_model";

    private static final Schema INPUT_SCHEMA =
            Schema.newBuilder().column("input", DataTypes.STRING()).build();

    private static final Schema OUTPUT_SCHEMA =
            Schema.newBuilder().column("prediction", DataTypes.STRING()).build();

    private static final String SUCCESS_RESPONSE =
            "{\"outputs\":[{\"name\":\"RESULT\",\"datatype\":\"BYTES\","
                    + "\"shape\":[1,1],\"data\":[\"ok\"]}]}";

    private static MockWebServer server;

    private TableEnvironment tEnv;
    private Map<String, String> modelOptions;
    private int requestCountBefore;

    @BeforeAll
    static void beforeAll() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterAll
    static void afterAll() throws IOException {
        if (server != null) {
            server.close();
        }
    }

    @BeforeEach
    void setup() {
        tEnv = TableEnvironment.create(new Configuration());
        tEnv.executeSql(
                "CREATE TABLE input_table(input STRING) WITH ("
                        + "'connector' = 'datagen',"
                        + "'number-of-rows' = '1')");

        modelOptions = new HashMap<>();
        modelOptions.put("provider", "triton");
        modelOptions.put("endpoint", server.url("/v2/models").toString());
        modelOptions.put("model-name", "test-model");
        // Keep backoff tiny so tests stay fast; RETRY_INITIAL_BACKOFF must be > 0.
        modelOptions.put("retry-initial-backoff", "1ms");
        modelOptions.put("retry-max-backoff", "10ms");

        requestCountBefore = server.getRequestCount();
    }

    /** First attempt fails with 503, second attempt succeeds — retry is effective. */
    @Test
    void testRetrySucceedsBeforeMaxAttempts() throws Exception {
        modelOptions.put("max-retries", "2");
        createModel(MODEL_NAME, OUTPUT_SCHEMA, modelOptions);

        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "application/json")
                        .setBody(SUCCESS_RESPONSE));

        TableResult tableResult =
                tEnv.executeSql(
                        "SELECT prediction FROM ML_PREDICT("
                                + "TABLE input_table, MODEL "
                                + MODEL_NAME
                                + ", DESCRIPTOR(`input`))");

        List<Row> rows = collectRows(tableResult);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).<String>getFieldAs(0)).isEqualTo("ok");
        assertThat(server.getRequestCount() - requestCountBefore).isEqualTo(2);
    }

    /** All attempts fail with 503 and default-value is configured — returns default value. */
    @Test
    void testDefaultValueReturnedAfterAllRetriesFail() throws Exception {
        modelOptions.put("max-retries", "2");
        modelOptions.put("default-value", "FAILED");
        createModel(MODEL_NAME, OUTPUT_SCHEMA, modelOptions);

        // max-retries=2 means 3 total attempts (initial + 2 retries).
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));

        TableResult tableResult =
                tEnv.executeSql(
                        "SELECT prediction FROM ML_PREDICT("
                                + "TABLE input_table, MODEL "
                                + MODEL_NAME
                                + ", DESCRIPTOR(`input`))");

        List<Row> rows = collectRows(tableResult);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).<String>getFieldAs(0)).isEqualTo("FAILED");
        assertThat(server.getRequestCount() - requestCountBefore).isEqualTo(3);
    }

    /**
     * All attempts fail with 503 and numeric default-value is configured — returns numeric
     * default. Exercises the {@code parseDefaultPayload} path for non-string logical types.
     */
    @Test
    void testNumericDefaultValueReturnedAfterAllRetriesFail() throws Exception {
        String intModelName = "triton_retry_model_int";
        Schema intOutputSchema = Schema.newBuilder().column("prediction", DataTypes.INT()).build();

        Map<String, String> intModelOptions = new HashMap<>(modelOptions);
        intModelOptions.put("max-retries", "1");
        intModelOptions.put("default-value", "-1");

        createModel(intModelName, intOutputSchema, intModelOptions);

        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));

        TableResult tableResult =
                tEnv.executeSql(
                        "SELECT prediction FROM ML_PREDICT("
                                + "TABLE input_table, MODEL "
                                + intModelName
                                + ", DESCRIPTOR(`input`))");

        List<Row> rows = collectRows(tableResult);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).<Integer>getFieldAs(0)).isEqualTo(-1);
    }

    /** All attempts fail with 503 and no default-value — exception propagates. */
    @Test
    void testExceptionThrownWhenNoDefaultValueAfterAllRetriesFail() {
        modelOptions.put("max-retries", "1");
        createModel(MODEL_NAME, OUTPUT_SCHEMA, modelOptions);

        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));

        TableResult tableResult =
                tEnv.executeSql(
                        "SELECT prediction FROM ML_PREDICT("
                                + "TABLE input_table, MODEL "
                                + MODEL_NAME
                                + ", DESCRIPTOR(`input`))");

        assertThatThrownBy(() -> collectRows(tableResult)).isInstanceOf(Exception.class);
    }

    /**
     * A 4xx client error is not retried — pipeline fails after exactly one HTTP request, proving
     * that {@code isRetryableStatus} correctly excludes 4xx.
     */
    @Test
    void test4xxErrorNotRetried() {
        modelOptions.put("max-retries", "3");
        createModel(MODEL_NAME, OUTPUT_SCHEMA, modelOptions);

        server.enqueue(
                new MockResponse().setResponseCode(400).setBody("{\"error\":\"bad request\"}"));

        TableResult tableResult =
                tEnv.executeSql(
                        "SELECT prediction FROM ML_PREDICT("
                                + "TABLE input_table, MODEL "
                                + MODEL_NAME
                                + ", DESCRIPTOR(`input`))");

        assertThatThrownBy(() -> collectRows(tableResult)).isInstanceOf(Exception.class);

        assertThat(server.getRequestCount() - requestCountBefore).isEqualTo(1);
    }

    /**
     * A 4xx client error with a configured default-value — no retry, but the default value is
     * returned instead of failing. This guards the "default-value wins over hard-fail" contract on
     * non-retryable errors.
     */
    @Test
    void test4xxErrorUsesDefaultValueWithoutRetry() throws Exception {
        modelOptions.put("max-retries", "3");
        modelOptions.put("default-value", "FALLBACK_4XX");
        createModel(MODEL_NAME, OUTPUT_SCHEMA, modelOptions);

        server.enqueue(
                new MockResponse().setResponseCode(400).setBody("{\"error\":\"bad request\"}"));

        TableResult tableResult =
                tEnv.executeSql(
                        "SELECT prediction FROM ML_PREDICT("
                                + "TABLE input_table, MODEL "
                                + MODEL_NAME
                                + ", DESCRIPTOR(`input`))");

        List<Row> rows = collectRows(tableResult);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).<String>getFieldAs(0)).isEqualTo("FALLBACK_4XX");
        // Still exactly one request — 4xx must not trigger retry even when default-value is set.
        assertThat(server.getRequestCount() - requestCountBefore).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void createModel(String name, Schema outputSchema, Map<String, String> options) {
        CatalogManager catalogManager = ((TableEnvironmentImpl) tEnv).getCatalogManager();
        ObjectIdentifier modelIdentifier =
                ObjectIdentifier.of(
                        Objects.requireNonNull(catalogManager.getCurrentCatalog()),
                        Objects.requireNonNull(catalogManager.getCurrentDatabase()),
                        name);
        catalogManager.createModel(
                CatalogModel.of(INPUT_SCHEMA, outputSchema, options, ""), modelIdentifier, false);
    }

    private static List<Row> collectRows(TableResult tableResult) throws Exception {
        List<Row> rows = new ArrayList<>();
        try (CloseableIterator<Row> it = tableResult.collect()) {
            it.forEachRemaining(rows::add);
        }
        return rows;
    }
}
