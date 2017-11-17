/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.ui.pages;

public class AdmiralWebClientConfiguration {

    /**
     * Determines how long to wait in seconds for the landing page after login, default: 30 seconds
     */
    private static int loginTimeoutSeconds = 30;

    /**
     * Determines the polling interval when polling requests(e.g. provision a container request),
     * default: 500 miliseconds
     */
    private static int requestPollingIntervalMiliseconds = 500;

    /**
     * Determines how long to wait in seconds for a successful host addition, default: 10 seconds
     */
    private static int addHostTimeoutSeconds = 20;

    /**
     * Determines how long to wait in seconds for a successful deletion of a container host,
     * default: 10 seconds
     */
    private static int deleteHostTimeoutSeconds = 20;

    public static int getLoginTimeoutSeconds() {
        return loginTimeoutSeconds;
    }

    public static void setLoginTimeoutSeconds(int loginTimeoutSeconds) {
        AdmiralWebClientConfiguration.loginTimeoutSeconds = loginTimeoutSeconds;
    }

    public static int getRequestPollingIntervalMiliseconds() {
        return requestPollingIntervalMiliseconds;
    }

    public static void setRequestPollingIntervalMiliseconds(int requestPollingIntervalMiliseconds) {
        AdmiralWebClientConfiguration.requestPollingIntervalMiliseconds = requestPollingIntervalMiliseconds;
    }

    public static int getAddHostTimeoutSeconds() {
        return addHostTimeoutSeconds;
    }

    public static void setAddHostTimeoutSeconds(int addHostTimeoutSeconds) {
        AdmiralWebClientConfiguration.addHostTimeoutSeconds = addHostTimeoutSeconds;
    }

    public static int getDeleteHostTimeoutSeconds() {
        return deleteHostTimeoutSeconds;
    }

    public static void setDeleteHostTimeoutSeconds(int deleteHostTimeoutSeconds) {
        AdmiralWebClientConfiguration.deleteHostTimeoutSeconds = deleteHostTimeoutSeconds;
    }

}
