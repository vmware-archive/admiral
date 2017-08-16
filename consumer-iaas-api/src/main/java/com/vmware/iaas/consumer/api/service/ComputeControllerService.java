/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.iaas.consumer.api.service;

import com.vmware.xenon.common.RequestRouter;

public class ComputeControllerService extends BaseControllerService {

    public static final String SELF_LINK = "/machine";

    @Override
    protected RequestRouter createControllerRouting() {
        RequestRouter requestRouter = new RequestRouter();

        requestRouter.register(
                Action.GET,
                new RequestRouter.RequestUriMatcher("type=long"),
                this::handleGet, "Long version");

        return requestRouter;
    }

}