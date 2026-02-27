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

import org.apache.flink.runtime.rest.HttpMethodWrapper;
import org.apache.flink.runtime.rest.handler.async.AsynchronousOperationResult;
import org.apache.flink.runtime.rest.messages.EmptyResponseBody;
import org.apache.flink.runtime.rest.messages.MessageHeaders;

/**
 * Headers for quarantining a node.
 */
public class NodeQuarantineHeaders
        implements MessageHeaders<
                NodeQuarantineRequestBody, NodeQuarantineResponseBody, NodeQuarantineMessageParameters> {

    private static final NodeQuarantineHeaders INSTANCE = new NodeQuarantineHeaders();

    public static final String URL = "/cluster/nodes/:" + ResourceIdPathParameter.KEY + "/quarantine";

    private NodeQuarantineHeaders() {}

    public static NodeQuarantineHeaders getInstance() {
        return INSTANCE;
    }

    @Override
    public Class<NodeQuarantineRequestBody> getRequestClass() {
        return NodeQuarantineRequestBody.class;
    }

    @Override
    public Class<NodeQuarantineResponseBody> getResponseClass() {
        return NodeQuarantineResponseBody.class;
    }

    @Override
    public HttpMethodWrapper getHttpMethod() {
        return HttpMethodWrapper.POST;
    }

    @Override
    public String getTargetRestEndpointURL() {
        return URL;
    }

    @Override
    public Class<NodeQuarantineMessageParameters> getUnresolvedMessageParameters() {
        return NodeQuarantineMessageParameters.class;
    }

    @Override
    public String getDescription() {
        return "Quarantine a node to prevent slot allocation.";
    }
}
