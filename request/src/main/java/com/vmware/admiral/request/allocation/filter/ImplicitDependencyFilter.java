/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.allocation.filter;

public interface ImplicitDependencyFilter {
    /**
     * Indicates if a given filter is active.
     *
     * @return true if the filter is active; false otherwise.
     */
    boolean isActive();

    /**
     * Get all container names that the current selection filter will based the criteria on.
     *
     * @return a map of {@link AffinityConstraint}s by container description name as key.
     */
    void apply();
}
