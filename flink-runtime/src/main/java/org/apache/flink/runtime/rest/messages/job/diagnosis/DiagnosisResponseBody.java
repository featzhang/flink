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

package org.apache.flink.runtime.rest.messages.job.diagnosis;

import org.apache.flink.runtime.rest.messages.ResponseBody;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Response body containing diagnostic messages and suggestions.
 */
public class DiagnosisResponseBody implements ResponseBody {

    private final Collection<DiagnosticMessage> diagnostics;
    private final long timestamp;

    public DiagnosisResponseBody(
            Collection<DiagnosticMessage> diagnostics, long timestamp) {
        this.diagnostics = diagnostics;
        this.timestamp = timestamp;
    }

    public Collection<DiagnosticMessage> getDiagnostics() {
        return diagnostics;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DiagnosisResponseBody that = (DiagnosisResponseBody) o;
        return timestamp == that.timestamp && Objects.equals(diagnostics, that.diagnostics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(diagnostics, timestamp);
    }

    /**
     * Represents a single diagnostic message with severity, title, description, and suggested
     * actions.
     */
    public static class DiagnosticMessage {
        private final String severity;
        private final String title;
        private final String message;
        private final Map<String, Object> metrics;
        private final List<String> suggestedActions;

        private DiagnosticMessage(Builder builder) {
            this.severity = builder.severity;
            this.title = builder.title;
            this.message = builder.message;
            this.metrics = builder.metrics;
            this.suggestedActions = builder.suggestedActions;
        }

        public String getSeverity() {
            return severity;
        }

        public String getTitle() {
            return title;
        }

        public String getMessage() {
            return message;
        }

        public Map<String, Object> getMetrics() {
            return metrics;
        }

        public List<String> getSuggestedActions() {
            return suggestedActions;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DiagnosticMessage that = (DiagnosticMessage) o;
            return Objects.equals(severity, that.severity)
                    && Objects.equals(title, that.title)
                    && Objects.equals(message, that.message)
                    && Objects.equals(metrics, that.metrics)
                    && Objects.equals(suggestedActions, that.suggestedActions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(severity, title, message, metrics, suggestedActions);
        }

        /**
         * Builder for creating DiagnosticMessage instances.
         */
        public static class Builder {
            private String severity;
            private String title;
            private String message;
            private Map<String, Object> metrics;
            private List<String> suggestedActions;

            public Builder setSeverity(String severity) {
                this.severity = severity;
                return this;
            }

            public Builder setTitle(String title) {
                this.title = title;
                return this;
            }

            public Builder setMessage(String message) {
                this.message = message;
                return this;
            }

            public Builder setMetrics(Map<String, Object> metrics) {
                this.metrics = metrics;
                return this;
            }

            public Builder addAction(String action) {
                if (this.suggestedActions == null) {
                    this.suggestedActions = new ArrayList<>();
                }
                this.suggestedActions.add(action);
                return this;
            }

            public Builder setSuggestedActions(List<String> actions) {
                this.suggestedActions = new ArrayList<>(actions);
                return this;
            }

            public DiagnosticMessage build() {
                return new DiagnosticMessage(this);
            }
        }
    }
}
