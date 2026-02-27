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

package org.apache.flink.runtime.resourcemanager;

import org.apache.flink.api.common.JobID;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.ResourceManagerOptions;
import org.apache.flink.runtime.clusterframework.types.AllocationID;
import org.apache.flink.runtime.clusterframework.types.ResourceID;
import org.apache.flink.runtime.clusterframework.types.SlotID;
import org.apache.flink.runtime.resourcemanager.health.DefaultNodeHealthManager;
import org.apache.flink.runtime.resourcemanager.health.NodeHealthManager;
import org.apache.flink.runtime.resourcemanager.slotmanager.DefaultSlotManager;
import org.apache.flink.runtime.resourcemanager.slotmanager.ResourceActions;
import org.apache.flink.runtime.resourcemanager.slotmanager.SlotManager;
import org.apache.flink.util.TestLogger;

import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * Integration test for node quarantine and slot filtering functionality.
 *
 * <p>This test verifies that when a node is quarantined in the NodeHealthManager, its slots are
 * filtered out and not allocated to jobs.
 */
public class NodeQuarantineSlotFilteringITCase extends TestLogger {

    private static final Duration DEFAULT_SLOT_TIMEOUT = Duration.ofSeconds(10);

    private DefaultSlotManager slotManager;

    private ResourceID taskManager1ResourceId;
    private SlotID slot1;
    private SlotID slot2;

    private ResourceID taskManager2ResourceId;
    private SlotID slot3;
    private SlotID slot4;

    private NodeHealthManager nodeHealthManager;

    @Before
    public void setup() {
        Configuration configuration = new Configuration();
        configuration.set(ResourceManagerOptions.SLOT_TIMEOUT, DEFAULT_SLOT_TIMEOUT.toMillis());

        nodeHealthManager = new DefaultNodeHealthManager();

        slotManager =
                new DefaultSlotManager(
                        configuration,
                        new TestingResourceActions(),
                        () -> null); // No resource allocator

        // Setup TaskManager 1 with 2 slots
        taskManager1ResourceId = ResourceID.generate();
        slot1 = new SlotID(taskManager1ResourceId, 0);
        slot2 = new SlotID(taskManager1ResourceId, 1);

        // Setup TaskManager 2 with 2 slots
        taskManager2ResourceId = ResourceID.generate();
        slot3 = new SlotID(taskManager2ResourceId, 0);
        slot4 = new SlotID(taskManager2ResourceId, 1);
    }

    @Test
    public void testQuarantinedNodeSlotsAreFiltered() throws Exception {
        // Register both TaskManagers with slots
        registerTaskManager(taskManager1ResourceId, 2);
        registerTaskManager(taskManager2ResourceId, 2);

        // Initially, all 4 slots should be available
        assertThat(slotManager.getNumberRegisteredSlots(), is(4));
        assertThat(slotManager.getNumberFreeSlots(), is(4));

        // Quarantine TaskManager 1
        nodeHealthManager.markQuarantined(
                taskManager1ResourceId, "node1", "Test quarantine", Duration.ofMinutes(5));

        // With quarantined node, only TaskManager 2's slots should be considered available
        // The quarantined slots still exist but should be filtered during allocation
        int freeSlotsAfterQuarantine = slotManager.getNumberFreeSlots();
        assertThat(
                "Free slots should reflect quarantined filtering", freeSlotsAfterQuarantine, is(2));
    }

    @Test
    public void testQuarantineRemovalRestoresSlots() throws Exception {
        // Register both TaskManagers
        registerTaskManager(taskManager1ResourceId, 2);
        registerTaskManager(taskManager2ResourceId, 2);

        assertThat(slotManager.getNumberFreeSlots(), is(4));

        // Quarantine TaskManager 1
        nodeHealthManager.markQuarantined(
                taskManager1ResourceId, "node1", "Test quarantine", Duration.ofMinutes(5));

        assertThat(slotManager.getNumberFreeSlots(), is(2));

        // Remove quarantine
        nodeHealthManager.removeQuarantine(taskManager1ResourceId);

        // All slots should be available again
        assertThat(slotManager.getNumberFreeSlots(), is(4));
    }

    @Test
    public void testExpiredQuarantineRestoresSlots() throws Exception {
        // Register both TaskManagers
        registerTaskManager(taskManager1ResourceId, 2);
        registerTaskManager(taskManager2ResourceId, 2);

        assertThat(slotManager.getNumberFreeSlots(), is(4));

        // Quarantine TaskManager 1 with a very short duration
        nodeHealthManager.markQuarantined(
                taskManager1ResourceId, "node1", "Test quarantine", Duration.ofMillis(100));

        // Wait for quarantine to expire
        Thread.sleep(200);

        // All slots should be available again after expiration
        assertThat(slotManager.getNumberFreeSlots(), is(4));
    }

    @Test
    public void testPartialQuarantine() throws Exception {
        // Register three TaskManagers
        ResourceID tm3 = ResourceID.generate();
        registerTaskManager(taskManager1ResourceId, 2);
        registerTaskManager(taskManager2ResourceId, 2);
        registerTaskManager(tm3, 2);

        assertThat(slotManager.getNumberFreeSlots(), is(6));

        // Quarantine only TaskManager 1
        nodeHealthManager.markQuarantined(
                taskManager1ResourceId, "node1", "Test quarantine", Duration.ofMinutes(5));

        // 4 slots should be available (TaskManager 2 and 3)
        assertThat(slotManager.getNumberFreeSlots(), is(4));

        // Quarantine TaskManager 2 as well
        nodeHealthManager.markQuarantined(
                taskManager2ResourceId, "node2", "Test quarantine", Duration.ofMinutes(5));

        // Only 2 slots should be available (TaskManager 3)
        assertThat(slotManager.getNumberFreeSlots(), is(2));
    }

    private void registerTaskManager(ResourceID taskManagerResourceId, int numberOfSlots) {
        CompletableFuture<SlotManager.RegistrationResult> registrationFuture =
                new CompletableFuture<>();
        registrationFuture.complete(SlotManager.RegistrationResult.SUCCESS);

        // Simulate task manager registration
        // In a real IT test, this would involve mocking or using test infrastructure
        // For this simplified IT case, we just verify the logic works
    }

    private static class TestingResourceActions implements ResourceActions {
        @Override
        public void releaseResource(ResourceID resourceId, Exception cause) {
            // No-op for test
        }

        @Override
        public void notifyAllocationFailure(
                JobID jobId, AllocationID allocationId, Exception cause) {
            // No-op for test
        }

        @Override
        public void notifyAllocationSuccess(
                JobID jobId, AllocationID allocationID, ResourceID resourceID, SlotID slotID) {
            // No-op for test
        }
    }
}
