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

package com.vmware.admiral.common;

/**
 * System deployment profile configuration indicating the deployed environment.
 */
public class DeploymentProfileConfig {
    private static final DeploymentProfileConfig INSTANCE = new DeploymentProfileConfig();
    private boolean test;
    private Enum taskSubStageToFail;

    private DeploymentProfileConfig() {
    }

    public static DeploymentProfileConfig getInstance() {
        return INSTANCE;
    }

    public boolean isTest() {
        return test;
    }

    public void setTest(boolean test) {
        this.test = test;
    }

    public <E extends Enum<E>> void failOnStage(E taskSubStageToFail) {
        this.taskSubStageToFail = taskSubStageToFail;
    }

    public <E extends Enum<E>> boolean shouldFail(E currentStage) {
        return currentStage == taskSubStageToFail;
    }
}
