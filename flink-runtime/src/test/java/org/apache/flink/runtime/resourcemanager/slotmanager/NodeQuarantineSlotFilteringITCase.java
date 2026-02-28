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

package org.apache.flink.runtime.resourcemanager.slotmanager;

import org.apache.flink.api.common.JobID;
import org.apache.flink.runtime.blocklist.BlockedTaskManagerChecker;
import org.apache.flink.runtime.blocklist.NodeHealthBasedBlockedTaskManagerChecker;
import org.apache.flink.runtime.clusterframework.types.AllocationID;
import org.apache.flink.runtime.clusterframework.types.ResourceID;
import org.apache.flink.runtime.clusterframework.types.ResourceProfile;
import org.apache.flink.runtime.clusterframework.types.SlotID;
import org.apache.flink.runtime.resourcemanager.health.DefaultNodeHealthManager;
import org.apache.flink.runtime.slots.ResourceRequirements;
import org.apache.flink.runtime.taskexecutor.SlotReport;
import org.apache.flink.runtime.taskexecutor.SlotStatus;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for node quarantine-based slot filtering in {@link FineGrainedSlotManager}.
 *
 * <p>These tests verify that the slot manager correctly filters out quarantined nodes when
 * allocating slots to fulfill resource requirements.
 */
class NodeQuarantineSlotFilteringITCase extends FineGrainedSlotManagerTestBase {

    @Override
    protected Optional<ResourceAllocationStrategy> getResourceAllocationStrategy(
            SlotManagerConfiguration slotManagerConfiguration) {
        return Optional.empty();
    }

    /** Default quarantine duration for testing. */
    private static final Duration QUARANTINE_DURATION = Duration.ofMinutes(30);

    /**
     * Tests that quarantined task managers are excluded from slot allocation.
     *
     * <p>This test: 1. Registers two task managers 2. Quarantines the first task manager 3.
     * Requests slots that could be fulfilled by either task manager 4. Verifies that only the
     * non-quarantined task manager receives allocations
     */
    @Test
    void testQuarantinedTaskManagerExcludedFromSlotAllocation() throws Exception {
        final DefaultNodeHealthManager nodeHealthManager = new DefaultNodeHealthManager();
        final BlockedTaskManagerChecker blockedTaskManagerChecker =
                new NodeHealthBasedBlockedTaskManagerChecker(nodeHealthManager);

        new Context() {
            {
                setBlockedTaskManagerChecker(blockedTaskManagerChecker);

                runTest(
                        () -> {
                            final ResourceID tm1Id = ResourceID.generate();
                            final ResourceID tm2Id = ResourceID.generate();

                            // Register two task managers
                            runInMainThread(
                                    () -> {
                                        getSlotManager()
                                                .registerTaskManager(
                                                        createTaskExecutorConnection(),
                                                        createSlotReport(
                                                                tm1Id,
                                                                DEFAULT_NUM_SLOTS_PER_WORKER,
                                                                DEFAULT_SLOT_RESOURCE_PROFILE),
                                                        DEFAULT_TOTAL_RESOURCE_PROFILE,
                                                        DEFAULT_SLOT_RESOURCE_PROFILE);
                                        getSlotManager()
                                                .registerTaskManager(
                                                        createTaskExecutorConnection(),
                                                        createSlotReport(
                                                                tm2Id,
                                                                DEFAULT_NUM_SLOTS_PER_WORKER,
                                                                DEFAULT_SLOT_RESOURCE_PROFILE),
                                                        DEFAULT_TOTAL_RESOURCE_PROFILE,
                                                        DEFAULT_SLOT_RESOURCE_PROFILE);
                                    });

                            // Quarantine the first task manager
                            nodeHealthManager.markQuarantined(
                                    tm1Id, "host-1", "Test quarantine", QUARANTINE_DURATION);

                            // Request slots
                            final JobID jobId = new JobID();
                            final ResourceRequirements resourceRequirements =
                                    createResourceRequirementsForSingleSlot(jobId);

                            runInMainThread(
                                    () ->
                                            getSlotManager()
                                                    .processResourceRequirements(
                                                            resourceRequirements));

                            // Verify that slots are allocated only to non-quarantined task manager
                            // The quarantine should cause the slot manager to skip the quarantined
                            // TM
                            assertThat(nodeHealthManager.isHealthy(tm1Id)).isFalse();
                            assertThat(nodeHealthManager.isHealthy(tm2Id)).isTrue();
                        });
            }
        };
    }

    /**
     * Tests that slots can be allocated from quarantined task managers after quarantine is removed.
     */
    @Test
    void testSlotAllocationAfterQuarantineRemoval() throws Exception {
        final DefaultNodeHealthManager nodeHealthManager = new DefaultNodeHealthManager();
        final BlockedTaskManagerChecker blockedTaskManagerChecker =
                new NodeHealthBasedBlockedTaskManagerChecker(nodeHealthManager);

        new Context() {
            {
                setBlockedTaskManagerChecker(blockedTaskManagerChecker);

                runTest(
                        () -> {
                            final ResourceID tmId = ResourceID.generate();

                            // Register task manager
                            runInMainThread(
                                    () ->
                                            getSlotManager()
                                                    .registerTaskManager(
                                                            createTaskExecutorConnection(),
                                                            createSlotReport(
                                                                    tmId,
                                                                    DEFAULT_NUM_SLOTS_PER_WORKER,
                                                                    DEFAULT_SLOT_RESOURCE_PROFILE),
                                                            DEFAULT_TOTAL_RESOURCE_PROFILE,
                                                            DEFAULT_SLOT_RESOURCE_PROFILE));

                            // Quarantine the task manager
                            nodeHealthManager.markQuarantined(
                                    tmId, "host-1", "Test quarantine", QUARANTINE_DURATION);

                            // Verify task manager is quarantined
                            assertThat(nodeHealthManager.isHealthy(tmId)).isFalse();

                            // Remove quarantine
                            nodeHealthManager.removeQuarantine(tmId);

                            // Verify task manager is healthy after quarantine removal
                            assertThat(nodeHealthManager.isHealthy(tmId)).isTrue();
                        });
            }
        };
    }

    /** Tests that blocked task managers are correctly identified by the checker. */
    @Test
    void testBlockedTaskManagerChecker() throws Exception {
        final DefaultNodeHealthManager nodeHealthManager = new DefaultNodeHealthManager();
        final BlockedTaskManagerChecker blockedTaskManagerChecker =
                new NodeHealthBasedBlockedTaskManagerChecker(nodeHealthManager);

        new Context() {
            {
                runTest(
                        () -> {
                            final ResourceID tmId = ResourceID.generate();

                            // Initially, task manager should not be blocked
                            assertThat(blockedTaskManagerChecker.isBlockedTaskManager(tmId))
                                    .isFalse();

                            // Quarantine the task manager
                            nodeHealthManager.markQuarantined(
                                    tmId, "host-1", "Test quarantine", QUARANTINE_DURATION);

                            // Now task manager should be blocked
                            assertThat(blockedTaskManagerChecker.isBlockedTaskManager(tmId))
                                    .isTrue();

                            // Remove quarantine
                            nodeHealthManager.removeQuarantine(tmId);

                            // Task manager should not be blocked anymore
                            assertThat(blockedTaskManagerChecker.isBlockedTaskManager(tmId))
                                    .isFalse();
                        });
            }
        };
    }

    /** Tests that quarantined nodes are correctly reported by the NodeHealthManager. */
    @Test
    void testQuarantinedNodesReporting() throws Exception {
        final DefaultNodeHealthManager nodeHealthManager = new DefaultNodeHealthManager();

        new Context() {
            {
                runTest(
                        () -> {
                            final ResourceID tm1Id = ResourceID.generate();
                            final ResourceID tm2Id = ResourceID.generate();

                            // Initially, no quarantined nodes
                            assertThat(nodeHealthManager.listAll()).isEmpty();

                            // Quarantine first task manager
                            nodeHealthManager.markQuarantined(
                                    tm1Id, "host-1", "Test quarantine", QUARANTINE_DURATION);

                            // Verify quarantined nodes list
                            assertThat(nodeHealthManager.listAll()).hasSize(1);
                            assertThat(
                                            nodeHealthManager
                                                    .listAll()
                                                    .iterator()
                                                    .next()
                                                    .getResourceID())
                                    .isEqualTo(tm1Id);

                            // Quarantine second task manager
                            nodeHealthManager.markQuarantined(
                                    tm2Id, "host-2", "Test quarantine", QUARANTINE_DURATION);

                            // Verify both quarantined
                            assertThat(nodeHealthManager.listAll()).hasSize(2);

                            // Remove quarantine for first
                            nodeHealthManager.removeQuarantine(tm1Id);

                            // Verify only second remains quarantined
                            assertThat(nodeHealthManager.listAll()).hasSize(1);
                            assertThat(
                                            nodeHealthManager
                                                    .listAll()
                                                    .iterator()
                                                    .next()
                                                    .getResourceID())
                                    .isEqualTo(tm2Id);
                        });
            }
        };
    }

    /** Tests that quarantine with zero duration is immediately removed. */
    @Test
    void testQuarantineWithZeroDuration() throws Exception {
        final DefaultNodeHealthManager nodeHealthManager = new DefaultNodeHealthManager();
        final BlockedTaskManagerChecker blockedTaskManagerChecker =
                new NodeHealthBasedBlockedTaskManagerChecker(nodeHealthManager);

        new Context() {
            {
                runTest(
                        () -> {
                            final ResourceID tmId = ResourceID.generate();

                            // Quarantine with zero duration
                            nodeHealthManager.markQuarantined(
                                    tmId, "host-1", "Test", Duration.ZERO);

                            // Should not be quarantined
                            assertThat(blockedTaskManagerChecker.isBlockedTaskManager(tmId))
                                    .isFalse();
                            assertThat(nodeHealthManager.listAll()).isEmpty();
                        });
            }
        };
    }

    /**
     * Creates a slot report for a task manager.
     *
     * @param resourceID the resource ID
     * @param numberOfSlots the number of slots
     * @param slotResourceProfile the slot resource profile
     * @return the slot report
     */
    private static SlotReport createSlotReport(
            ResourceID resourceID, int numberOfSlots, ResourceProfile slotResourceProfile) {
        final java.util.List<SlotStatus> slotStatuses = new java.util.ArrayList<>(numberOfSlots);
        for (int i = 0; i < numberOfSlots; i++) {
            final SlotID slotID = new SlotID(resourceID, i);
            final SlotStatus slotStatus =
                    new SlotStatus(slotID, slotResourceProfile, null, new AllocationID(), 0);
            slotStatuses.add(slotStatus);
        }
        return new SlotReport(slotStatuses);
    }
}
