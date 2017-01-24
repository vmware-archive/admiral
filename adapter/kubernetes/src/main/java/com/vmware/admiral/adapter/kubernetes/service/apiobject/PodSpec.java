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

package com.vmware.admiral.adapter.kubernetes.service.apiobject;

import java.util.List;

public class PodSpec {
    // public Volume[] volumes;
    public List<Container> containers; // Required
    // public String restartPolicy;
    // public int terminationGracePeriodSeconds;
    // public int activeDeadlineSeconds;
    // public String dnsPolicy;
    // public Object nodeSelector;
    // public String serviceAccountName;
    // public String serviceAccount;
    // public String nodeName;
    // public boolean hostNetwork;
    // public boolean hostPID;
    // public boolean hostIPC;
    // public PodSecurityContext securityContext;
    // public LocalObjectReference imagePullSecrets;
    public String hostname;
    // public String subdomain;
}
