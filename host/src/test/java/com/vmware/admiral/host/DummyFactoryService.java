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

package com.vmware.admiral.host;

import com.vmware.admiral.host.DummyService.DummyServiceTaskState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;

public class DummyFactoryService extends FactoryService {

    public static final String SELF_LINK = "dummy-service";

    public DummyFactoryService() {
        super(DummyServiceTaskState.class);
        this.setUseBodyForSelfLink(true);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new DummyService();
    }

}