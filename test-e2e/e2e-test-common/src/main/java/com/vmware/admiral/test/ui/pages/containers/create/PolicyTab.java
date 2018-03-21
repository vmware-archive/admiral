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

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class PolicyTab extends BasicPage<PolicyTabValidator, PolicyTabLocators> {

    public PolicyTab(By[] iFrameLocators, PolicyTabValidator validator,
            PolicyTabLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void setClusterSize(String clusterSize) {
        LOG.info(String.format("Setting cluster size [%s]", clusterSize));
        pageActions().clear(locators().clusterSizeInput());
        pageActions().sendKeys(clusterSize, locators().clusterSizeInput());
    }

    public void setRestartPolicy(RestartPolicy policy) {
        LOG.info(String.format("Setting restart policy [%s]", policy.toString()));
        String value = null;
        if (policy == RestartPolicy.ALWAYS) {
            value = "always";
        } else if (policy == RestartPolicy.ON_FAILURE) {
            value = "onFailure";
        } else {
            value = "no";
        }
        pageActions().selectOptionByValue(value, locators().restartPolicySelect());
    }

    public void setMaxRestarts(String maxRestarts) {
        LOG.info(String.format("Setting max restarts [%s]", maxRestarts));
        pageActions().clear(locators().maxRestartsInput());
        pageActions().sendKeys(maxRestarts, locators().maxRestartsInput());
    }

    public void setCpuShares(String cpuShares) {
        LOG.info(String.format("Setting cpu shares [%s]", cpuShares));
        pageActions().clear(locators().cpuSharesInput());
        pageActions().sendKeys(cpuShares, locators().cpuSharesInput());
    }

    public void setMemoryLimit(String limit, MemoryUnit unit) {
        LOG.info(String.format("Setting memory limit [%s%s]", limit, unit.toString()));
        pageActions().clear(locators().memoryLimitInput());
        pageActions().sendKeys(limit, locators().memoryLimitInput());
        pageActions().selectOptionByText(unit.toString(), locators().memoryLimitUnitSelect());
    }

    public void setMemorySwapLimit(String limit, MemoryUnit unit) {
        LOG.info(String.format("Setting memory swap limit [%s%s]", limit, unit.toString()));
        pageActions().clear(locators().memorySwapLimitInput());
        pageActions().sendKeys(limit, locators().memorySwapLimitInput());
        pageActions().selectOptionByText(unit.toString(), locators().memorySwapLimitUnitSelect());
    }

    public void addAffinityConstraint(boolean antiAffinity, String serviceName,
            ConstraintType type) {
        String affinity;
        if (antiAffinity) {
            affinity = "anti affinity";
        } else {
            affinity = "affinity";
        }
        LOG.info(String.format("Setting %s constraint for service [%s] and type [%s]", affinity,
                serviceName, type.toString()));
        if (!pageActions().getText(locators().lastAffinityServiceNameSelect()).trim().isEmpty()) {
            pageActions().click(locators().addAffinityConstraintButton());
        }
        pageActions().setCheckbox(antiAffinity, locators().lastAntiAffinityCheckbox());
        pageActions().selectOptionByValue(serviceName, locators().lastAffinityServiceNameSelect());
        if (type == ConstraintType.HARD) {
            pageActions().selectOptionByValue("hard", locators().lastAffinityConstraintSelect());
        } else if (type == ConstraintType.SOFT) {
            pageActions().selectOptionByValue("soft", locators().lastAffinityConstraintSelect());
        }

    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
    }

    public static enum RestartPolicy {
        NONE, ON_FAILURE, ALWAYS;
    }

    public static enum MemoryUnit {
        kB, MB, GB;
    }

    public static enum ConstraintType {
        NONE, SOFT, HARD
    }

}
