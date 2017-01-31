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

package com.vmware.admiral.closures.drivers;

public final class DriverConstants {

    public static final String VMWARE_RUNTIME = "vmware/photon-closure-runner_";

    // nodejs
    public static final String RUNTIME_NODEJS_4 = "nodejs";
    // python
    public static final String RUNTIME_PYTHON_3 = "python";
    // bash
    public static final String RUNTIME_BASH = "bash";
    public static final String RUNTIME_NASHORN = "nashorn";

    public static final String NODEJS_4_IMAGE = VMWARE_RUNTIME + RUNTIME_NODEJS_4;
    public static final String PYTHON_3_IMAGE = VMWARE_RUNTIME + RUNTIME_PYTHON_3;

    public static final String DOCKER_IMAGE_DATA_FOLDER_NAME =
            "com/vmware/admiral/closures/drivers/client/docker/image/";

    private DriverConstants() {
    }

}
