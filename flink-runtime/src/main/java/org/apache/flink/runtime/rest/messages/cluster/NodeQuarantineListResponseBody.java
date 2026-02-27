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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Response body for listing all quarantined nodes.
 */
public class NodeQuarantineListResponseBody {

    public static final String FIELD_NAME_QUARANTINED_NODES = "quarantinedNodes";

    @JsonProperty(FIELD_NAME_QUARANTINED_NODES)
    private final Collection<QuarantinedNodeInfo> quarantinedNodes;

    @JsonCreator
    public NodeQuarantineListResponseBody(
            @JsonProperty(FIELD_NAME_QUARANTINED_NODES) Collection<QuarantinedNodeInfo> quarantinedNodes) {
        this.quarantinedNodes =
                quarantinedNodes != null
                        ? Collections.unmodifiableCollection(new ArrayList<>(quarantinedNodes))
                        : Collections.emptyList();
    }

    @JsonProperty(FIELD_NAME_QUARANTINED_NODES)
    public Collection<QuarantinedNodeInfo> getQuarantinedNodes() {
        return quarantinedNodes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NodeQuarantineListResponseBody that = (NodeQuarantineListResponseBody) o;
        return Objects.equals(quarantinedNodes, that.quarantinedNodes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(quarantinedNodes);
    }

    @Override
    public String toString() {
        return "NodeQuarantineListResponseBody{" + "quarantinedNodes=" + quarantinedNodes + '}';
    }

    /** Information about a quarantined node. */
    public static class QuarantinedNodeInfo {

        public static final String FIELD_NAME_NODE_ID = "nodeId";
        public static final String FIELD_NAME_REASON = "reason";
        public static final String FIELD_NAME_QUARANTINE_TIMESTAMP = "quarantineTimestamp";
        public static final String FIELD_NAME_EXPIRATION_TIMESTAMP = "expirationTimestamp";

        @JsonProperty(FIELD_NAME_NODE_ID)
        private final String nodeId;

        @JsonProperty(FIELD_NAME_REASON)
        private final String reason;

        @JsonProperty(FIELD_NAME_QUARANTINE_TIMESTAMP)
        private final long quarantineTimestamp;

        @JsonProperty(FIELD_NAME_EXPIRATION_TIMESTAMP)
        private final long expirationTimestamp;

        @JsonCreator
        public QuarantinedNodeInfo(
                @JsonProperty(FIELD_NAME_NODE_ID) String nodeId,
                @JsonProperty(FIELD_NAME_REASON) String reason,
                @JsonProperty(FIELD_NAME_QUARANTINE_TIMESTAMP) long quarantineTimestamp,
                @JsonProperty(FIELD_NAME_EXPIRATION_TIMESTAMP) long expirationTimestamp) {
            this.nodeId = nodeId;
            this.reason = reason;
            this.quarantineTimestamp = quarantineTimestamp;
            this.expirationTimestamp = expirationTimestamp;
        }

        @JsonProperty(FIELD_NAME_NODE_ID)
        public String getNodeId() {
            return nodeId;
        }

        @JsonProperty(FIELD_NAME_REASON)
        public String getReason() {
            return reason;
        }

        @JsonProperty(FIELD_NAME_QUARANTINE_TIMESTAMP)
        public long getQuarantineTimestamp() {
            return quarantineTimestamp;
        }

        @JsonProperty(FIELD_NAME_EXPIRATION_TIMESTAMP)
        public long getExpirationTimestamp() {
            return expirationTimestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            QuarantinedNodeInfo that = (QuarantinedNodeInfo) o;
            return quarantineTimestamp == that.quarantineTimestamp
                    && expirationTimestamp == that.expirationTimestamp
                    && Objects.equals(nodeId, that.nodeId)
                    && Objects.equals(reason, that.reason);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nodeId, reason, quarantineTimestamp, expirationTimestamp);
        }

        @Override
        public String toString() {
            return "QuarantinedNodeInfo{"
                    + "nodeId='"
                    + nodeId
                    + '\''
                    + ", reason='"
                    + reason
                    + '\''
                    + ", quarantineTimestamp="
                    + quarantineTimestamp
                    + ", expirationTimestamp="
                    + expirationTimestamp
                    + '}';
        }
    }
}
