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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link NodeHealthManager}.
 *
 * <p>This implementation uses a {@link ConcurrentHashMap} to store node health statuses. A node is
 * considered healthy if it is not in the quarantine map or if its quarantine has expired.
 */
public class DefaultNodeHealthManager implements NodeHealthManager {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultNodeHealthManager.class);

    private final Map<ResourceID, NodeHealthStatus> quarantinedNodes;

    public DefaultNodeHealthManager() {
        this.quarantinedNodes = new ConcurrentHashMap<>();
    }

    @Override
    public boolean isHealthy(ResourceID resourceID) {
        NodeHealthStatus status = quarantinedNodes.get(resourceID);
        if (status == null) {
            // Node not quarantined
            return true;
        }

        if (status.isExpired(System.currentTimeMillis())) {
            // Quarantine expired, consider as healthy and clean up
            quarantinedNodes.remove(resourceID);
            return true;
        }

        // Node is quarantined and not expired
        return false;
    }

    @Override
    public void markQuarantined(
            ResourceID resourceID, String hostname, String reason, Duration duration) {
        long now = System.currentTimeMillis();
        long expirationTime = now + duration.toMillis();

        NodeHealthStatus status =
                new NodeHealthStatus(resourceID, hostname, reason, now, expirationTime);

        quarantinedNodes.put(resourceID, status);
        LOG.info(
                "Quarantined node {} ({}) for reason: {}, expires at {}",
                resourceID,
                hostname,
                reason,
                expirationTime);
    }

    @Override
    public void removeQuarantine(ResourceID resourceID) {
        NodeHealthStatus removed = quarantinedNodes.remove(resourceID);
        if (removed != null) {
            LOG.info("Removed quarantine from node {}", resourceID);
        }
    }

    @Override
    public Collection<NodeHealthStatus> listAll() {
        return Collections.unmodifiableCollection(quarantinedNodes.values());
    }

    @Override
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        quarantinedNodes
                .entrySet()
                .removeIf(
                        entry -> {
                            if (entry.getValue().isExpired(now)) {
                                LOG.info(
                                        "Cleaning up expired quarantine for node {}",
                                        entry.getKey());
                                return true;
                            }
                            return false;
                        });
    }
}
