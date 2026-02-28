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

import org.apache.flink.runtime.resourcemanager.health.NodeHealthStatus;
import org.apache.flink.runtime.rest.messages.ResponseBody;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

/**
 * Response containing a list of quarantined nodes.
 *
 * <p>Each node entry includes:
 *
 * <ul>
 *   <li>resourceId: The resource ID of the node
 *   <li>host: The host address of the node
 *   <li>quarantinedTime: When the node was quarantined
 *   <li>quarantineEndTime: When the quarantine will expire
 *   <li>reason: The reason for quarantine
 * </ul>
 */
public class NodeQuarantineListResponse implements ResponseBody {

    public static final String FIELD_NODES = "nodes";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"));

    @JsonProperty(FIELD_NODES)
    private final String nodes;

    @JsonCreator
    public NodeQuarantineListResponse(@JsonProperty(FIELD_NODES) String nodes) {
        this.nodes = nodes;
    }

    /**
     * Create response from a collection of NodeHealthStatus objects.
     *
     * @param quarantinedNodes The collection of quarantined nodes
     * @return The response object
     */
    public static NodeQuarantineListResponse from(Collection<NodeHealthStatus> quarantinedNodes) {
        ArrayNode nodesArray = OBJECT_MAPPER.createArrayNode();
        for (NodeHealthStatus status : quarantinedNodes) {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("resourceId", status.getResourceID().toString());
            node.put("host", status.getHostname());
            node.put("quarantinedTime", formatTimestamp(status.getQuarantineTimestamp()));
            node.put("quarantineEndTime", formatTimestamp(status.getExpirationTimestamp()));
            node.put("reason", status.getReason());
            nodesArray.add(node);
        }
        try {
            String nodesJson = OBJECT_MAPPER.writeValueAsString(nodesArray);
            return new NodeQuarantineListResponse(nodesJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize quarantined nodes", e);
        }
    }

    private static String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return null;
        }
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.of("UTC")).format(DATE_FORMATTER);
    }

    @JsonProperty(FIELD_NODES)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getNodes() {
        return nodes;
    }

    @Override
    public String toString() {
        return "NodeQuarantineListResponse{nodes='" + nodes + "'}";
    }
}
