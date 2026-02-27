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

import org.apache.flink.runtime.rest.messages.ResponseBody;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/** Response body for node quarantine operation. */
public class NodeQuarantineResponseBody implements ResponseBody {

    public static final String FIELD_NAME_NODE_ID = "nodeId";
    public static final String FIELD_NAME_REASON = "reason";
    public static final String FIELD_NAME_TIMESTAMP = "timestamp";

    @JsonProperty(FIELD_NAME_NODE_ID)
    private final String nodeId;

    @JsonProperty(FIELD_NAME_REASON)
    private final String reason;

    @JsonProperty(FIELD_NAME_TIMESTAMP)
    private final long timestamp;

    @JsonCreator
    public NodeQuarantineResponseBody(
            @JsonProperty(FIELD_NAME_NODE_ID) String nodeId,
            @JsonProperty(FIELD_NAME_REASON) String reason,
            @JsonProperty(FIELD_NAME_TIMESTAMP) long timestamp) {
        this.nodeId = nodeId;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    @JsonProperty(FIELD_NAME_NODE_ID)
    public String getNodeId() {
        return nodeId;
    }

    @JsonProperty(FIELD_NAME_REASON)
    public String getReason() {
        return reason;
    }

    @JsonProperty(FIELD_NAME_TIMESTAMP)
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
        NodeQuarantineResponseBody that = (NodeQuarantineResponseBody) o;
        return timestamp == that.timestamp
                && Objects.equals(nodeId, that.nodeId)
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, reason, timestamp);
    }

    @Override
    public String toString() {
        return "NodeQuarantineResponseBody{"
                + "nodeId='"
                + nodeId
                + '\''
                + ", reason='"
                + reason
                + '\''
                + ", timestamp="
                + timestamp
                + '}';
    }
}
