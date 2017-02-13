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

package com.vmware.admiral.compute.content.kubernetes.pods;

import java.util.List;

public class PodStatus {
    /*public String phase;
    public List<PodCondition> conditions;
    public String message;
    public String reason;
    public String hostIP;
    public String podIP;
    public String startTime;   //date-time*/
    public List<PodContainerStatus> containerStatuses;
}
