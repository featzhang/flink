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

import org.apache.flink.annotation.VisibleForTesting;

import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Utility class for Triton Inference Server HTTP client management. */
public class TritonUtils {
    private static final Logger LOG = LoggerFactory.getLogger(TritonUtils.class);

    private static final Object LOCK = new Object();

    private static final Map<ClientKey, ClientValue> cache = new HashMap<>();

    public static OkHttpClient createHttpClient(long timeoutMs, int maxRetries) {
        synchronized (LOCK) {
            ClientKey key = new ClientKey(timeoutMs, maxRetries);
            ClientValue value = cache.get(key);
            if (value != null) {
                LOG.debug("Returning an existing Triton HTTP client.");
                value.referenceCount.incrementAndGet();
                return value.client;
            }

            LOG.debug("Building a new Triton HTTP client.");
            OkHttpClient client =
                    new OkHttpClient.Builder()
                            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                            .retryOnConnectionFailure(true)
                            .build();

            cache.put(key, new ClientValue(client));
            return client;
        }
    }

    public static void releaseHttpClient(OkHttpClient client) {
        synchronized (LOCK) {
            ClientKey keyToRemove = null;
            ClientValue valueToRemove = null;

            for (Map.Entry<ClientKey, ClientValue> entry : cache.entrySet()) {
                if (entry.getValue().client == client) {
                    keyToRemove = entry.getKey();
                    valueToRemove = entry.getValue();
                    break;
                }
            }

            if (valueToRemove != null) {
                int count = valueToRemove.referenceCount.decrementAndGet();
                if (count == 0) {
                    LOG.debug("Closing the Triton HTTP client.");
                    cache.remove(keyToRemove);
                    // OkHttpClient doesn't need explicit closing, but we can clean up resources
                    client.dispatcher().executorService().shutdown();
                    client.connectionPool().evictAll();
                }
            }
        }
    }

    /** Builds the inference URL for a specific model and version. */
    public static String buildInferenceUrl(String endpoint, String modelName, String modelVersion) {
        String baseUrl = endpoint.replaceAll("/*$", "");
        if (!baseUrl.endsWith("/v2/models")) {
            if (baseUrl.endsWith("/v2")) {
                baseUrl += "/models";
            } else {
                baseUrl += "/v2/models";
            }
        }
        return String.format("%s/%s/versions/%s/infer", baseUrl, modelName, modelVersion);
    }

    /** Builds the model metadata URL for a specific model and version. */
    public static String buildModelMetadataUrl(
            String endpoint, String modelName, String modelVersion) {
        String baseUrl = endpoint.replaceAll("/*$", "");
        if (!baseUrl.endsWith("/v2/models")) {
            if (baseUrl.endsWith("/v2")) {
                baseUrl += "/models";
            } else {
                baseUrl += "/v2/models";
            }
        }
        return String.format("%s/%s/versions/%s", baseUrl, modelName, modelVersion);
    }

    private static class ClientValue {
        private final OkHttpClient client;
        private final AtomicInteger referenceCount;

        private ClientValue(OkHttpClient client) {
            this.client = client;
            this.referenceCount = new AtomicInteger(1);
        }
    }

    private static class ClientKey {
        private final long timeoutMs;
        private final int maxRetries;

        private ClientKey(long timeoutMs, int maxRetries) {
            this.timeoutMs = timeoutMs;
            this.maxRetries = maxRetries;
        }

        @Override
        public int hashCode() {
            return Objects.hash(timeoutMs, maxRetries);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ClientKey
                    && timeoutMs == ((ClientKey) obj).timeoutMs
                    && maxRetries == ((ClientKey) obj).maxRetries;
        }
    }

    @VisibleForTesting
    static Map<ClientKey, ClientValue> getCache() {
        return cache;
    }
}
