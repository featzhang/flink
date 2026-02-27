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

import java.util.Collection;
import java.util.Collections;

/** Response body containing the list of quarantined nodes. */
public class NodeQuarantineListResponseBody implements ResponseBody {

    private final Collection<QuarantinedNodeInfo> quarantinedNodes;

    public NodeQuarantineListResponseBody(Collection<QuarantinedNodeInfo> quarantinedNodes) {
        this.quarantinedNodes = Collections.unmodifiableCollection(quarantinedNodes);
    }

    public Collection<QuarantinedNodeInfo> getQuarantinedNodes() {
        return quarantinedNodes;
    }

    /** Information about a quarantined node. */
    public static class QuarantinedNodeInfo {
        private final String nodeId;
        private final String reason;
        private final long quarantineTimestamp;
        private final Long expirationTimestamp;

        public QuarantinedNodeInfo(
                String nodeId, String reason, long quarantineTimestamp, Long expirationTimestamp) {
            this.nodeId = nodeId;
            this.reason = reason;
            this.quarantineTimestamp = quarantineTimestamp;
            this.expirationTimestamp = expirationTimestamp;
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getReason() {
            return reason;
        }

        public long getQuarantineTimestamp() {
            return quarantineTimestamp;
        }

        public Long getExpirationTimestamp() {
            return expirationTimestamp;
        }
    }
}
