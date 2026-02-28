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

package org.apache.flink.runtime.blocklist;

import org.apache.flink.runtime.clusterframework.types.ResourceID;
import org.apache.flink.runtime.resourcemanager.health.NodeHealthManager;

import javax.annotation.Nullable;

/**
 * Implementation of {@link BlockedTaskManagerChecker} that uses {@link NodeHealthManager} to check
 * if a task manager is blocked.
 *
 * <p>A task manager is considered blocked if the node it's running on is not healthy according to
 * the node health manager. This typically means the node has been quarantined due to health issues.
 */
public class NodeHealthBasedBlockedTaskManagerChecker implements BlockedTaskManagerChecker {

    /** The node health manager used to check task manager health. */
    @Nullable private final NodeHealthManager nodeHealthManager;

    /**
     * Creates a new NodeHealthBasedBlockedTaskManagerChecker.
     *
     * @param nodeHealthManager the node health manager to use, or null to use a no-op
     *     implementation
     */
    public NodeHealthBasedBlockedTaskManagerChecker(@Nullable NodeHealthManager nodeHealthManager) {
        this.nodeHealthManager = nodeHealthManager;
    }

    @Override
    public boolean isBlockedTaskManager(ResourceID resourceID) {
        if (nodeHealthManager == null) {
            return false;
        }
        // A task manager is blocked if the node it's running on is not healthy
        return !nodeHealthManager.isHealthy(resourceID);
    }
}
