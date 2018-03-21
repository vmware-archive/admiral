/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.ui.pages.containers.create;

import org.openqa.selenium.By;

public class PolicyTabLocators extends CreateContainerPageLocators {

    private final By CLUSTER_SIZE_INPUT = By
            .cssSelector("#policy .container-cluster-size-input .form-control");
    private final By RESTART_POLICY_SELECT = By
            .cssSelector("#policy .container-restart-policy-input .form-control");
    private final By MAX_RESTARTS_INPUT = By
            .cssSelector("#policy .container-max-restarts-input .form-control");
    private final By CPU_SHARES_INPUT = By
            .cssSelector("#policy .container-cpu-shares-input .form-control");
    private final By MEMORY_LIMIT_INPUT = By
            .cssSelector("#policy .container-memory-limit-input input");
    private final By MEMORY_LIMIT_UNIT_SELECT = By
            .cssSelector("#policy .container-memory-limit-input select");
    private final By MEMORY_SWAP_LIMIT_INPUT = By
            .cssSelector("#policy .container-memory-swap-input input");
    private final By MEMORY_SWAP_LIMIT_UNIT_SELECT = By
            .cssSelector("#policy .container-memory-swap-input select");
    private final String LAST_AFFINITY_BASE = "#policy .container-affinity-constraints-input .multicolumn-input:last-child";
    private final By ADD_AFFINITY_CONSTRAINT_BUTTON = By
            .cssSelector(LAST_AFFINITY_BASE + " .fa-plus");
    private final By LAST_ANTI_AFFINITY_CHECKBOX = By
            .cssSelector(LAST_AFFINITY_BASE + " input[name='antiaffinity']");
    private final By LAST_AFFINITY_SERVICE_NAME_SELECT = By
            .cssSelector(LAST_AFFINITY_BASE + " select[name='servicename']");
    private final By LAST_AFFINITY_CONSTRAINT_SELECT = By
            .cssSelector(LAST_AFFINITY_BASE + " select[name='constraint']");

    public By clusterSizeInput() {
        return CLUSTER_SIZE_INPUT;
    }

    public By restartPolicySelect() {
        return RESTART_POLICY_SELECT;
    }

    public By maxRestartsInput() {
        return MAX_RESTARTS_INPUT;
    }

    public By cpuSharesInput() {
        return CPU_SHARES_INPUT;
    }

    public By memoryLimitInput() {
        return MEMORY_LIMIT_INPUT;
    }

    public By memoryLimitUnitSelect() {
        return MEMORY_LIMIT_UNIT_SELECT;
    }

    public By memorySwapLimitInput() {
        return MEMORY_SWAP_LIMIT_INPUT;
    }

    public By memorySwapLimitUnitSelect() {
        return MEMORY_SWAP_LIMIT_UNIT_SELECT;
    }

    public By addAffinityConstraintButton() {
        return ADD_AFFINITY_CONSTRAINT_BUTTON;
    }

    public By lastAntiAffinityCheckbox() {
        return LAST_ANTI_AFFINITY_CHECKBOX;
    }

    public By lastAffinityServiceNameSelect() {
        return LAST_AFFINITY_SERVICE_NAME_SELECT;
    }

    public By lastAffinityConstraintSelect() {
        return LAST_AFFINITY_CONSTRAINT_SELECT;
    }

}
