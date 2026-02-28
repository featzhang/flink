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
import java.util.Collections;

/**
 * No-operation implementation of {@link NodeHealthManager}.
 *
 * <p>This implementation always considers all nodes as healthy and performs no actual quarantine
 * operations. It is useful when the node health management feature is disabled.
 */
public class NoOpNodeHealthManager implements NodeHealthManager {

    @Override
    public boolean isHealthy(ResourceID resourceID) {
        return true;
    }

    @Override
    public void markQuarantined(
            ResourceID resourceID, String hostname, String reason, Duration duration) {
        // No-op: do not quarantine nodes
    }

    @Override
    public void removeQuarantine(ResourceID resourceID) {
        // No-op: no quarantine to remove
    }

    @Override
    public Collection<NodeHealthStatus> listAll() {
        return Collections.emptyList();
    }

    @Override
    public void cleanupExpired() {
        // No-op: no entries to clean up
    }
}
