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
import org.apache.flink.runtime.resourcemanager.ResourceManagerGateway;
import org.apache.flink.runtime.rest.handler.HandlerRequest;
import org.apache.flink.runtime.rest.handler.RestHandlerException;
import org.apache.flink.runtime.rest.handler.resourcemanager.AbstractResourceManagerHandler;
import org.apache.flink.runtime.rest.messages.MessageHeaders;
import org.apache.flink.runtime.rest.messages.cluster.NodeQuarantineMessageParameters;
import org.apache.flink.runtime.rest.messages.cluster.NodeQuarantineRequestBody;
import org.apache.flink.runtime.rest.messages.cluster.NodeQuarantineResponseBody;
import org.apache.flink.runtime.webmonitor.RestfulGateway;
import org.apache.flink.runtime.webmonitor.retriever.GatewayRetriever;

import javax.annotation.Nonnull;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handler for quarantining a node.
 *
 * <p>Handles POST requests to quarantine a specific node by its resource ID.
 */
public class NodeQuarantineHandler
        extends AbstractResourceManagerHandler<
                RestfulGateway,
                NodeQuarantineRequestBody,
                NodeQuarantineResponseBody,
                NodeQuarantineMessageParameters> {

    /** Default quarantine duration if not specified in the request (30 minutes). */
    private static final Duration DEFAULT_QUARANTINE_DURATION = Duration.ofMinutes(30);

    public NodeQuarantineHandler(
            GatewayRetriever<? extends RestfulGateway> leaderRetriever,
            Duration timeout,
            Map<String, String> responseHeaders,
            MessageHeaders<
                            NodeQuarantineRequestBody,
                            NodeQuarantineResponseBody,
                            NodeQuarantineMessageParameters>
                    messageHeaders,
            GatewayRetriever<ResourceManagerGateway> resourceManagerGatewayRetriever) {
        super(
                leaderRetriever,
                timeout,
                responseHeaders,
                messageHeaders,
                resourceManagerGatewayRetriever);
    }

    @Override
    protected CompletableFuture<NodeQuarantineResponseBody> handleRequest(
            @Nonnull HandlerRequest<NodeQuarantineRequestBody> request,
            @Nonnull ResourceManagerGateway resourceManagerGateway)
            throws RestHandlerException {

        NodeQuarantineRequestBody requestBody = request.getRequestBody();
        ResourceID nodeId =
                request.getPathParameter(
                        org.apache.flink.runtime.rest.messages.cluster.NodeQuarantineIdPathParameter
                                .class);

        String reason = requestBody.getReason();
        Duration duration = requestBody.parseDuration(DEFAULT_QUARANTINE_DURATION);

        // Call the ResourceManager to quarantine the node
        return resourceManagerGateway
                .quarantineNode(nodeId, reason, duration, timeout)
                .thenApply(acknowledge -> NodeQuarantineResponseBody.getInstance());
    }
}
