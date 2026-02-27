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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * A composite BlockedTaskManagerChecker that combines multiple checkers.
 *
 * <p>A task manager is considered blocked if any of the underlying checkers report it as blocked.
 */
public class CompositeBlockedTaskManagerChecker implements BlockedTaskManagerChecker {

    private final Collection<BlockedTaskManagerChecker> checkers;

    /**
     * Creates a composite checker with the given checkers.
     *
     * @param checkers the checkers to combine
     */
    public CompositeBlockedTaskManagerChecker(Collection<BlockedTaskManagerChecker> checkers) {
        this.checkers = Collections.unmodifiableCollection(checkers);
    }

    /**
     * Creates a composite checker with the given checkers.
     *
     * @param checkers the checkers to combine
     */
    public CompositeBlockedTaskManagerChecker(BlockedTaskManagerChecker... checkers) {
        this(Arrays.asList(checkers));
    }

    @Override
    public boolean isBlockedTaskManager(ResourceID resourceID) {
        // A task manager is blocked if any of the checkers report it as blocked
        for (BlockedTaskManagerChecker checker : checkers) {
            if (checker.isBlockedTaskManager(resourceID)) {
                return true;
            }
        }
        return false;
    }
}
