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

import org.apache.flink.runtime.clusterframework.types.ResourceID;

import java.time.Duration;
import java.util.Collection;

/**
 * Interface for managing node health status in the ResourceManager.
 *
 * <p>This interface provides methods to check node health, mark nodes as quarantined, remove
 * quarantine status, list all node health statuses, and clean up expired quarantine entries.
 */
public interface NodeHealthManager {

    /**
     * Check if a node is healthy.
     *
     * @param resourceID the resource ID of the node to check
     * @return true if the node is healthy (not quarantined or quarantine expired), false otherwise
     */
    boolean isHealthy(ResourceID resourceID);

    /**
     * Mark a node as quarantined for a specific duration.
     *
     * @param resourceID the resource ID of the node to quarantine
     * @param hostname the hostname of the node
     * @param reason the reason for quarantine
     * @param duration the duration of quarantine
     */
    void markQuarantined(ResourceID resourceID, String hostname, String reason, Duration duration);

    /**
     * Remove quarantine status from a node.
     *
     * @param resourceID the resource ID of the node to remove quarantine
     */
    void removeQuarantine(ResourceID resourceID);

    /**
     * List all node health statuses.
     *
     * @return a collection of all node health statuses
     */
    Collection<NodeHealthStatus> listAll();

    /** Clean up expired quarantine entries. */
    void cleanupExpired();
}
