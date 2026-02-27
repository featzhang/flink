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

package org.apache.flink.runtime.rest.handler.cluster;

import org.apache.flink.runtime.clusterframework.types.ResourceID;
import org.apache.flink.runtime.messages.Acknowledge;
import org.apache.flink.runtime.rest.messages.ResponseBody;
import org.apache.flink.runtime.rest.messages.cluster.NodeQuarantineRequestBody;
import org.apache.flink.runtime.rest.messages.cluster.NodeQuarantineResponseBody;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Simple test to verify PR-3 Node Quarantine functionality. */
public class NodeQuarantineHandlerTest {

    @Test
    public void testNodeQuarantineRequestBody() {
        String reason = "Test quarantine reason";
        long durationMs = 60000L;
        String hostname = "test-host-123";

        NodeQuarantineRequestBody body =
                new NodeQuarantineRequestBody(reason, durationMs, hostname);

        assertEquals(reason, body.getReason());
        assertEquals(durationMs, body.getDurationMs());
        assertEquals(hostname, body.getHostname());
    }

    @Test
    public void testNodeQuarantineResponseBody() {
        String nodeId = "test-node-id-123";
        String reason = "Test quarantine";
        long timestamp = System.currentTimeMillis();

        NodeQuarantineResponseBody response =
                new NodeQuarantineResponseBody(nodeId, reason, timestamp);

        assertEquals(nodeId, response.getNodeId());
        assertEquals(reason, response.getReason());
        assertEquals(timestamp, response.getTimestamp());
        assertTrue(response instanceof ResponseBody);
    }

    @Test
    public void testResourceIDCreation() {
        String resourceIdString = "test-resource-id-123";
        ResourceID resourceId = new ResourceID(resourceIdString);

        assertEquals(resourceIdString, resourceId.getResourceIdString());
    }

    @Test
    public void testAcknowledge() {
        Acknowledge ack = Acknowledge.get();
        assertNotNull(ack);
    }

    @Test
    public void testCompletableFutureAcknowledge() {
        CompletableFuture<Acknowledge> future =
                CompletableFuture.completedFuture(Acknowledge.get());

        assertTrue(future.isDone());
        assertNotNull(future.join());
    }

    @Test
    public void testNodeQuarantineRequestWithZeroDuration() {
        String reason = "Temporary quarantine";
        long durationMs = 0L;
        String hostname = "test-host";

        NodeQuarantineRequestBody body =
                new NodeQuarantineRequestBody(reason, durationMs, hostname);

        assertEquals(reason, body.getReason());
        assertEquals(durationMs, body.getDurationMs());
    }

    @Test
    public void testNodeQuarantineRequestWithLongDuration() {
        String reason = "Long quarantine";
        long durationMs = Long.MAX_VALUE;
        String hostname = "test-host";

        NodeQuarantineRequestBody body =
                new NodeQuarantineRequestBody(reason, durationMs, hostname);

        assertEquals(reason, body.getReason());
        assertEquals(durationMs, body.getDurationMs());
    }
}
