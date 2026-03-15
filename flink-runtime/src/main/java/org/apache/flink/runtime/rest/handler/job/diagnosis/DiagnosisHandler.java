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

package org.apache.flink.runtime.rest.handler.job.diagnosis;

import org.apache.flink.api.common.JobID;
import org.apache.flink.runtime.rest.handler.AbstractRestHandler;
import org.apache.flink.runtime.rest.handler.HandlerRequest;
import org.apache.flink.runtime.rest.handler.RestHandlerException;
import org.apache.flink.runtime.rest.messages.EmptyRequestBody;
import org.apache.flink.runtime.rest.messages.JobIDPathParameter;
import org.apache.flink.runtime.rest.messages.MessageHeaders;
import org.apache.flink.runtime.rest.messages.job.diagnosis.DiagnosisHeaders;
import org.apache.flink.runtime.rest.messages.job.diagnosis.DiagnosisResponseBody;
import org.apache.flink.runtime.webmonitor.RestfulGateway;
import org.apache.flink.runtime.webmonitor.retriever.GatewayRetriever;

import org.apache.flink.shaded.netty4.io.netty.handler.codec.http.HttpResponseStatus;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler that provides automated diagnostic suggestions based on metrics analysis.
 *
 * <p>This handler analyzes various job metrics (CPU, memory, GC, backpressure) and applies
 * rule-based logic to identify common performance issues and provide actionable suggestions.
 */
public class DiagnosisHandler
        extends AbstractRestHandler<
                RestfulGateway,
                EmptyRequestBody,
                DiagnosisResponseBody,
                DiagnosisHeaders.DiagnosisMessageParameters> {

    private final double cpuWarningThreshold;
    private final double cpuCriticalThreshold;
    private final double memoryWarningThreshold;
    private final double memoryCriticalThreshold;
    private final double backpressureWarningThreshold;

    public DiagnosisHandler(
            final GatewayRetriever<? extends RestfulGateway> leaderRetriever,
            final Duration timeout,
            final Map<String, String> headers,
            final MessageHeaders<
                            EmptyRequestBody,
                            DiagnosisResponseBody,
                            DiagnosisHeaders.DiagnosisMessageParameters>
                    messageHeaders) {
        super(leaderRetriever, timeout, headers, messageHeaders);
        this.cpuWarningThreshold = 0.7;
        this.cpuCriticalThreshold = 0.9;
        this.memoryWarningThreshold = 0.7;
        this.memoryCriticalThreshold = 0.9;
        this.backpressureWarningThreshold = 0.5;
    }

    @Override
    protected CompletableFuture<DiagnosisResponseBody> handleRequest(
            final HandlerRequest<EmptyRequestBody> request, final RestfulGateway gateway)
            throws RestHandlerException {

        try {
            final JobID jobId = request.getPathParameter(JobIDPathParameter.class);

            // Collect metrics from the job
            Map<String, Object> jobMetrics = collectJobMetrics(jobId, gateway);

            // Analyze metrics and generate diagnostic messages
            Collection<DiagnosisResponseBody.DiagnosticMessage> diagnostics =
                    analyzeMetrics(jobMetrics);

            // Return the diagnosis response
            DiagnosisResponseBody response =
                    new DiagnosisResponseBody(diagnostics, System.currentTimeMillis());

            return CompletableFuture.completedFuture(response);

        } catch (Exception e) {
            throw new RestHandlerException(
                    "Failed to generate diagnosis: " + e.getMessage(),
                    HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    e);
        }
    }

    /**
     * Collects relevant metrics from the job for diagnosis.
     *
     * @return Map of metric names to their values
     */
    private Map<String, Object> collectJobMetrics(JobID jobId, RestfulGateway gateway)
            throws Exception {
        Map<String, Object> metrics = new HashMap<>();

        // TODO: Implement actual metrics collection from the job
        // This is a placeholder that would normally:
        // 1. Query CPU metrics: taskmanager.cpu.time, taskmanager.cpu.usage
        // 2. Query memory metrics: taskmanager.memory.heap.used, taskmanager.memory.heap.max
        // 3. Query GC metrics: taskmanager.GarbageCollector.time, taskmanager.GarbageCollector.count
        // 4. Query backpressure metrics: taskmanager.backpressure.ratio

        // For now, return empty metrics - the analysis will handle missing data gracefully
        metrics.put("cpuUsage", 0.0);
        metrics.put("heapUsageRatio", 0.0);
        metrics.put("gcCount", 0L);
        metrics.put("gcTime", 0L);
        metrics.put("backpressureRatio", 0.0);

        return metrics;
    }

    /**
     * Analyzes collected metrics and generates diagnostic messages based on rule-based logic.
     *
     * @param metrics Map of metric names to their values
     * @return Collection of diagnostic messages
     */
    private Collection<DiagnosisResponseBody.DiagnosticMessage> analyzeMetrics(
            Map<String, Object> metrics) {
        List<DiagnosisResponseBody.DiagnosticMessage> diagnostics = new ArrayList<>();

        double cpuUsage = getDoubleMetric(metrics, "cpuUsage", 0.0);
        double heapUsageRatio = getDoubleMetric(metrics, "heapUsageRatio", 0.0);
        long gcCount = getLongMetric(metrics, "gcCount", 0L);
        long gcTime = getLongMetric(metrics, "gcTime", 0L);
        double backpressureRatio = getDoubleMetric(metrics, "backpressureRatio", 0.0);

        // Rule 1: High CPU + High Memory = Possible GC issue
        if (cpuUsage >= cpuWarningThreshold && heapUsageRatio >= memoryWarningThreshold) {
            Map<String, Object> ruleMetrics = new HashMap<>();
            ruleMetrics.put("cpuUsage", cpuUsage);
            ruleMetrics.put("heapUsageRatio", heapUsageRatio);
            ruleMetrics.put("gcCount", gcCount);
            ruleMetrics.put("gcTime", gcTime);

            String severity = cpuUsage >= cpuCriticalThreshold ? "critical" : "warning";
            String title = "High CPU Usage Detected";
            String message =
                    "High CPU usage combined with high heap memory usage may be caused by frequent Garbage Collection. "
                            + "This often occurs when objects are created rapidly and collected frequently.";

            DiagnosisResponseBody.DiagnosticMessage.Builder builder =
                    new DiagnosisResponseBody.DiagnosticMessage.Builder()
                            .setSeverity(severity)
                            .setTitle(title)
                            .setMessage(message)
                            .setMetrics(ruleMetrics)
                            .addAction("Check GarbageCollectorTime metrics")
                            .addAction("Review heap size configuration")
                            .addAction("Analyze GC logs for object allocation patterns")
                            .addAction("Consider increasing young generation size")
                            .addAction("Optimize code to reduce object creation");

            diagnostics.add(builder.build());
        }
        // Rule 2: High CPU + Normal Memory = Computation heavy
        else if (cpuUsage >= cpuWarningThreshold && heapUsageRatio < memoryWarningThreshold) {
            Map<String, Object> ruleMetrics = new HashMap<>();
            ruleMetrics.put("cpuUsage", cpuUsage);
            ruleMetrics.put("heapUsageRatio", heapUsageRatio);

            String severity = cpuUsage >= cpuCriticalThreshold ? "critical" : "warning";
            String title = "High Computation Load Detected";
            String message =
                    "High CPU usage with normal memory usage suggests the job is computation-intensive. "
                            + "This could be due to heavy user code processing, complex transformations, or CPU-bound operations.";

            DiagnosisResponseBody.DiagnosticMessage.Builder builder =
                    new DiagnosisResponseBody.DiagnosticMessage.Builder()
                            .setSeverity(severity)
                            .setTitle(title)
                            .setMessage(message)
                            .setMetrics(ruleMetrics)
                            .addAction("Check backpressure metrics for bottlenecks")
                            .addAction("Review operator implementation for optimization opportunities")
                            .addAction("Consider parallelism adjustments")
                            .addAction("Profile user code for CPU hotspots");

            diagnostics.add(builder.build());
        }

        // Rule 3: Low CPU + High Backpressure = I/O bottleneck
        if (cpuUsage < cpuWarningThreshold && backpressureRatio >= backpressureWarningThreshold) {
            Map<String, Object> ruleMetrics = new HashMap<>();
            ruleMetrics.put("cpuUsage", cpuUsage);
            ruleMetrics.put("backpressureRatio", backpressureRatio);

            String severity = backpressureRatio >= 0.8 ? "critical" : "warning";
            String title = "Backpressure Detected";
            String message =
                    "Low CPU usage combined with high backpressure indicates an I/O bottleneck or external dependency delay. "
                            + "This often occurs when operators are waiting for data from slow sources, blocking I/O operations, or network delays.";

            DiagnosisResponseBody.DiagnosticMessage.Builder builder =
                    new DiagnosisResponseBody.DiagnosticMessage.Builder()
                            .setSeverity(severity)
                            .setTitle(title)
                            .setMessage(message)
                            .setMetrics(ruleMetrics)
                            .addAction("Check source connector performance")
                            .addAction("Review network configurations")
                            .addAction("Investigate external system dependencies")
                            .addAction("Consider increasing source parallelism")
                            .addAction("Check for blocking operations in user code");

            diagnostics.add(builder.build());
        }

        // Rule 4: High GC count
        if (gcCount > 10000) {
            Map<String, Object> ruleMetrics = new HashMap<>();
            ruleMetrics.put("gcCount", gcCount);
            ruleMetrics.put("gcTime", gcTime);

            String severity = gcCount > 50000 ? "critical" : "warning";
            String title = "High Garbage Collection Frequency";
            String message =
                    "The job has performed an unusually high number of GC collections. "
                            + "This indicates excessive object allocation and can significantly impact performance.";

            DiagnosisResponseBody.DiagnosticMessage.Builder builder =
                    new DiagnosisResponseBody.DiagnosticMessage.Builder()
                            .setSeverity(severity)
                            .setTitle(title)
                            .setMessage(message)
                            .setMetrics(ruleMetrics)
                            .addAction("Review object creation patterns in user code")
                            .addAction("Consider using object pooling")
                            .addAction("Increase heap size or adjust GC settings")
                            .addAction("Analyze GC logs for allocation hotspots");

            diagnostics.add(builder.build());
        }

        // If no issues detected, provide an informational message
        if (diagnostics.isEmpty()) {
            DiagnosisResponseBody.DiagnosticMessage infoMessage =
                    new DiagnosisResponseBody.DiagnosticMessage.Builder()
                            .setSeverity("info")
                            .setTitle("Job Status: Healthy")
                            .setMessage("No significant performance issues detected based on current metrics.")
                            .addAction("Continue monitoring job performance")
                            .build();
            diagnostics.add(infoMessage);
        }

        return diagnostics;
    }

    private double getDoubleMetric(Map<String, Object> metrics, String key, double defaultValue) {
        Object value = metrics.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    private long getLongMetric(Map<String, Object> metrics, String key, long defaultValue) {
        Object value = metrics.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }
}
