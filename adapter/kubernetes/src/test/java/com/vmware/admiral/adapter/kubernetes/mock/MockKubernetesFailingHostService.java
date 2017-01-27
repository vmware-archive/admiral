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

package com.vmware.admiral.adapter.kubernetes.mock;

import static com.vmware.admiral.adapter.kubernetes.mock.MockKubernetesPathConstants.BASE_FAILING_PATH;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

public class MockKubernetesFailingHostService extends StatefulService {
    public static final String SELF_LINK = BASE_FAILING_PATH;

    public MockKubernetesFailingHostService() {
        super(ServiceDocument.class);
    }

    @Override
    public void handleGet(Operation get) {
        get.fail(500);
    }
}
