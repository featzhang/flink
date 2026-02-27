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

package org.apache.flink.runtime.rest.messages.cluster;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.Objects;

/**
 * Request body for quarantining a node.
 */
public class NodeQuarantineRequestBody {

    public static final String FIELD_NAME_REASON = "reason";
    public static final String FIELD_NAME_DURATION_MS = "durationMs";

    @JsonProperty(FIELD_NAME_REASON)
    private final String reason;

    @JsonProperty(FIELD_NAME_DURATION_MS)
    private final long durationMs;

    @JsonCreator
    public NodeQuarantineRequestBody(
            @JsonProperty(FIELD_NAME_REASON) String reason,
            @JsonProperty(FIELD_NAME_DURATION_MS) long durationMs) {
        this.reason = reason;
        this.durationMs = durationMs;
    }

    @JsonProperty(FIELD_NAME_REASON)
    public String getReason() {
        return reason;
    }

    @JsonProperty(FIELD_NAME_DURATION_MS)
    public long getDurationMs() {
        return durationMs;
    }

    public Duration getDuration() {
        return Duration.ofMillis(durationMs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NodeQuarantineRequestBody that = (NodeQuarantineRequestBody) o;
        return durationMs == that.durationMs && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reason, durationMs);
    }

    @Override
    public String toString() {
        return "NodeQuarantineRequestBody{"
                + "reason='"
                + reason
                + '\''
                + ", durationMs="
                + durationMs
                + '}';
    }
}
