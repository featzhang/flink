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

package org.apache.flink.runtime.resourcemanager.health;

import org.apache.flink.runtime.blocklist.BlockedTaskManagerChecker;
import org.apache.flink.runtime.clusterframework.types.ResourceID;

/**
 * Adapter that integrates NodeHealthManager with BlockedTaskManagerChecker.
 *
 * <p>This adapter allows the NodeHealthManager to be used as a BlockedTaskManagerChecker, enabling
 * SlotManager to filter out unhealthy task managers during slot allocation.
 */
public class NodeHealthManagerBlockedTaskManagerChecker implements BlockedTaskManagerChecker {

    private final NodeHealthManager nodeHealthManager;

    public NodeHealthManagerBlockedTaskManagerChecker(NodeHealthManager nodeHealthManager) {
        this.nodeHealthManager = nodeHealthManager;
    }

    @Override
    public boolean isBlockedTaskManager(ResourceID resourceID) {
        // A task manager is considered blocked if it is not healthy according to NodeHealthManager
        return !nodeHealthManager.isHealthy(resourceID);
    }
}
