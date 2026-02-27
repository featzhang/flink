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
import org.apache.flink.runtime.rest.handler.AbstractRestHandler;
import org.apache.flink.runtime.rest.handler.HandlerRequest;
import org.apache.flink.runtime.rest.handler.RestHandlerException;
import org.apache.flink.runtime.rest.messages.EmptyRequestBody;
import org.apache.flink.runtime.rest.messages.EmptyResponseBody;
import org.apache.flink.runtime.rest.messages.MessageHeaders;
import org.apache.flink.runtime.rest.messages.cluster.NodeQuarantineMessageParameters;
import org.apache.flink.runtime.rest.messages.cluster.ResourceIdPathParameter;
import org.apache.flink.runtime.webmonitor.RestfulGateway;
import org.apache.flink.runtime.webmonitor.retriever.GatewayRetriever;

import javax.annotation.Nonnull;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** REST handler which allows to remove quarantine from a node by its resource ID. */
public class NodeUnquarantineHandler
        extends AbstractRestHandler<
                RestfulGateway,
                EmptyRequestBody,
                EmptyResponseBody,
                NodeQuarantineMessageParameters> {

    public NodeUnquarantineHandler(
            final GatewayRetriever<? extends RestfulGateway> leaderRetriever,
            final Duration timeout,
            final Map<String, String> responseHeaders,
            final MessageHeaders<
                            EmptyRequestBody, EmptyResponseBody, NodeQuarantineMessageParameters>
                    messageHeaders) {
        super(leaderRetriever, timeout, responseHeaders, messageHeaders);
    }

    @Override
    protected CompletableFuture<EmptyResponseBody> handleRequest(
            @Nonnull final HandlerRequest<EmptyRequestBody> request,
            @Nonnull final RestfulGateway gateway)
            throws RestHandlerException {
        if (!(gateway instanceof ResourceManagerGateway)) {
            return CompletableFuture.failedFuture(
                    new RestHandlerException(
                            "Gateway is not a ResourceManagerGateway",
                            org.apache.flink.shaded.netty4.io.netty.handler.codec.http
                                    .HttpResponseStatus.INTERNAL_SERVER_ERROR));
        }

        final String resourceId = request.getPathParameter(ResourceIdPathParameter.class);

        final ResourceID nodeResourceID = new ResourceID(resourceId);

        final ResourceManagerGateway rmGateway = (ResourceManagerGateway) gateway;
        return rmGateway
                .removeNodeQuarantine(nodeResourceID, timeout)
                .thenApply(acknowledge -> EmptyResponseBody.getInstance());
    }
}
