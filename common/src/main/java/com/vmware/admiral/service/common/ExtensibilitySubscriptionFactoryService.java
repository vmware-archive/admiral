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

package com.vmware.admiral.service.common;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionService.ExtensibilitySubscription;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;

public class ExtensibilitySubscriptionFactoryService extends FactoryService {

    public static final String SELF_LINK = ManagementUriParts.EXTENSIBILITY_SUBSCRIPTION;

    public ExtensibilitySubscriptionFactoryService() {
        super(ExtensibilitySubscription.class);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new ExtensibilitySubscriptionService();
    }

    @Override
    public void handlePost(Operation post) {
        if (!post.hasBody()) {
            throw new IllegalArgumentException("Body is required");
        }

        ExtensibilitySubscription body = post.getBody(ExtensibilitySubscription.class);

        body.documentSelfLink = UriUtils.buildUriPath(SELF_LINK,
                ExtensibilitySubscriptionService.constructKey(body));
        post.setBodyNoCloning(body);

        super.handlePost(post);
    }

}
