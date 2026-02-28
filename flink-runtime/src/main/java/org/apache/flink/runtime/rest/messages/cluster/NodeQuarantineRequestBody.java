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

import org.apache.flink.runtime.rest.messages.RequestBody;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.Objects;

/**
 * Request body for quarantining a node.
 *
 * <p>This request contains:
 *
 * <ul>
 *   <li>reason: The reason for quarantining the node
 *   <li>duration: The duration of the quarantine
 * </ul>
 */
public class NodeQuarantineRequestBody implements RequestBody {

    public static final String FIELD_REASON = "reason";
    public static final String FIELD_DURATION = "duration";

    @JsonProperty(FIELD_REASON)
    private final String reason;

    @JsonProperty(FIELD_DURATION)
    private final String duration;

    @JsonCreator
    public NodeQuarantineRequestBody(
            @JsonProperty(FIELD_REASON) String reason,
            @JsonProperty(FIELD_DURATION) String duration) {
        this.reason = reason;
        this.duration = duration;
    }

    @JsonProperty(FIELD_REASON)
    public String getReason() {
        return reason;
    }

    @JsonProperty(FIELD_DURATION)
    public String getDuration() {
        return duration;
    }

    /** Parse the duration string to a Duration object. Returns default duration if null. */
    public Duration parseDuration(Duration defaultDuration) {
        if (duration == null || duration.isEmpty()) {
            return defaultDuration;
        }
        // Parse duration in format like "30m", "1h", "2d", etc.
        long value = Long.parseLong(duration.replaceAll("[^0-9]", ""));
        String unit = duration.replaceAll("[^a-zA-Z]", "").toLowerCase();

        switch (unit) {
            case "s":
                return Duration.ofSeconds(value);
            case "m":
                return Duration.ofMinutes(value);
            case "h":
                return Duration.ofHours(value);
            case "d":
                return Duration.ofDays(value);
            default:
                throw new IllegalArgumentException(
                        "Invalid duration format: " + duration + ". Use format like 30m, 1h, 2d");
        }
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
        return Objects.equals(reason, that.reason) && Objects.equals(duration, that.duration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reason, duration);
    }

    @Override
    public String toString() {
        return "NodeQuarantineRequestBody{"
                + "reason='"
                + reason
                + '\''
                + ", duration='"
                + duration
                + '\''
                + '}';
    }
}
