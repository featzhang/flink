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

import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.binary.BinaryStringData;
import org.apache.flink.table.factories.ModelProviderFactory;
import org.apache.flink.table.functions.AsyncPredictFunction;
import org.apache.flink.table.types.logical.VarCharType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** {@link AsyncPredictFunction} for Triton Inference Server generic inference task. */
public class TritonInferenceModelFunction extends AbstractTritonModelFunction {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(TritonInferenceModelFunction.class);

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public TritonInferenceModelFunction(
            ModelProviderFactory.Context factoryContext, ReadableConfig config) {
        super(factoryContext, config);
        validateSingleColumnSchema(
                factoryContext.getCatalogModel().getResolvedOutputSchema(),
                new VarCharType(VarCharType.MAX_LENGTH),
                "output");
    }

    @Override
    public CompletableFuture<Collection<RowData>> asyncPredict(RowData rowData) {
        CompletableFuture<Collection<RowData>> future = new CompletableFuture<>();

        try {
            String requestBody = buildInferenceRequest(rowData.getString(0).toString());
            String url =
                    TritonUtils.buildInferenceUrl(getEndpoint(), getModelName(), getModelVersion());

            Request.Builder requestBuilder =
                    new Request.Builder()
                            .url(url)
                            .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE));

            // Add authentication header if provided
            if (getAuthToken() != null) {
                requestBuilder.addHeader("Authorization", "Bearer " + getAuthToken());
            }

            // Add custom headers if provided
            if (getCustomHeaders() != null) {
                try {
                    JsonNode headersNode = objectMapper.readTree(getCustomHeaders());
                    headersNode
                            .fields()
                            .forEachRemaining(
                                    entry ->
                                            requestBuilder.addHeader(
                                                    entry.getKey(), entry.getValue().asText()));
                } catch (JsonProcessingException e) {
                    LOG.warn("Failed to parse custom headers: {}", getCustomHeaders(), e);
                }
            }

            // Add compression header if specified
            if (getCompression() != null) {
                requestBuilder.addHeader("Content-Encoding", getCompression());
            }

            Request request = requestBuilder.build();

            httpClient
                    .newCall(request)
                    .enqueue(
                            new Callback() {
                                @Override
                                public void onFailure(Call call, IOException e) {
                                    LOG.error("Triton inference request failed", e);
                                    future.completeExceptionally(e);
                                }

                                @Override
                                public void onResponse(Call call, Response response)
                                        throws IOException {
                                    try {
                                        if (!response.isSuccessful()) {
                                            String errorBody =
                                                    response.body() != null
                                                            ? response.body().string()
                                                            : "Unknown error";
                                            future.completeExceptionally(
                                                    new RuntimeException(
                                                            "Triton inference failed with status "
                                                                    + response.code()
                                                                    + ": "
                                                                    + errorBody));
                                            return;
                                        }

                                        String responseBody = response.body().string();
                                        Collection<RowData> result =
                                                parseInferenceResponse(responseBody);
                                        future.complete(result);
                                    } catch (Exception e) {
                                        LOG.error("Failed to process Triton inference response", e);
                                        future.completeExceptionally(e);
                                    } finally {
                                        response.close();
                                    }
                                }
                            });

        } catch (Exception e) {
            LOG.error("Failed to build Triton inference request", e);
            future.completeExceptionally(e);
        }

        return future;
    }

    private String buildInferenceRequest(String inputText) throws JsonProcessingException {
        ObjectNode requestNode = objectMapper.createObjectNode();

        // Add request ID if sequence ID is provided
        if (getSequenceId() != null) {
            requestNode.put("id", getSequenceId());
        }

        // Add parameters
        ObjectNode parametersNode = objectMapper.createObjectNode();
        if (getPriority() != null) {
            parametersNode.put("priority", getPriority());
        }
        if (isSequenceStart()) {
            parametersNode.put("sequence_start", true);
        }
        if (isSequenceEnd()) {
            parametersNode.put("sequence_end", true);
        }
        if (parametersNode.size() > 0) {
            requestNode.set("parameters", parametersNode);
        }

        // Add inputs
        ArrayNode inputsArray = objectMapper.createArrayNode();
        ObjectNode inputNode = objectMapper.createObjectNode();
        inputNode.put("name", "INPUT_TEXT"); // This might need to be configurable
        inputNode.put("datatype", "BYTES");

        ArrayNode shapeArray = objectMapper.createArrayNode();
        shapeArray.add(1); // Batch size
        inputNode.set("shape", shapeArray);

        ArrayNode dataArray = objectMapper.createArrayNode();
        dataArray.add(inputText);
        inputNode.set("data", dataArray);

        inputsArray.add(inputNode);
        requestNode.set("inputs", inputsArray);

        // Add outputs (request all outputs)
        ArrayNode outputsArray = objectMapper.createArrayNode();
        ObjectNode outputNode = objectMapper.createObjectNode();
        outputNode.put("name", "OUTPUT_TEXT"); // This might need to be configurable
        outputsArray.add(outputNode);
        requestNode.set("outputs", outputsArray);

        return objectMapper.writeValueAsString(requestNode);
    }

    private Collection<RowData> parseInferenceResponse(String responseBody)
            throws JsonProcessingException {
        JsonNode responseNode = objectMapper.readTree(responseBody);
        List<RowData> results = new ArrayList<>();

        JsonNode outputsNode = responseNode.get("outputs");
        if (outputsNode != null && outputsNode.isArray()) {
            for (JsonNode outputNode : outputsNode) {
                JsonNode dataNode = outputNode.get("data");
                if (dataNode != null && dataNode.isArray()) {
                    for (JsonNode dataItem : dataNode) {
                        String outputText = dataItem.asText();
                        results.add(GenericRowData.of(BinaryStringData.fromString(outputText)));
                    }
                }
            }
        }

        // If no outputs found, return empty result
        if (results.isEmpty()) {
            results.add(GenericRowData.of(BinaryStringData.fromString("")));
        }

        return results;
    }
}
