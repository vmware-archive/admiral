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

package com.vmware.admiral.common.util;

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNullOrEmpty;

import java.util.Collections;

import com.vmware.admiral.service.common.UniquePropertiesService;
import com.vmware.admiral.service.common.UniquePropertiesService.UniquePropertiesRequest;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;

public class UniquePropertiesUtil {

    /**
     * @param service Service that is using the util.
     * @param propertiesId The ID of UniquePropertiesService's child which hold the unique elements.
     * @param propertyName The property should be added in the list of unique elements.
     * @return Deferred result of boolean which is true if the property is already used and
     * claiming it failed or false if the claim was successful.
     */
    public static DeferredResult<Boolean> claimProperty(Service service, String propertiesId,
            String propertyName) {

        assertNotNull(service, "service");
        assertNotNullOrEmpty(propertiesId, "propertiesId");
        assertNotNullOrEmpty(propertyName, "propertyName");

        UniquePropertiesRequest request = new UniquePropertiesRequest();
        request.toAdd = Collections.singletonList(propertyName);

        DeferredResult<Boolean> result = new DeferredResult<>();

        String uriPath = UriUtils.buildUriPath(UniquePropertiesService.FACTORY_LINK, propertiesId);

        Operation patch = Operation.createPatch(service, uriPath)
                .setBody(request)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        if (o.getStatusCode() == Operation.STATUS_CODE_CONFLICT) {
                            result.complete(true);
                            return;
                        }
                        result.fail(ex);
                        return;
                    }
                    result.complete(false);
                });

        service.sendRequest(patch);
        return result;
    }

    public static DeferredResult<Void> freeProperty(Service service, String propertiesId, String
            propertyName) {

        assertNotNull(service, "service");
        assertNotNullOrEmpty(propertiesId, "propertiesId");
        assertNotNullOrEmpty(propertyName, "propertyName");

        UniquePropertiesRequest request = new UniquePropertiesRequest();
        request.toRemove = Collections.singletonList(propertyName);

        String uriPath = UriUtils.buildUriPath(UniquePropertiesService.FACTORY_LINK, propertiesId);

        DeferredResult<Void> result = new DeferredResult<>();

        Operation patch = Operation.createPatch(service, uriPath)
                .setBody(request)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        result.fail(ex);
                        return;
                    }
                    result.complete(null);
                });

        service.sendRequest(patch);

        return result;
    }

    public static DeferredResult<Boolean> updateClaimedProperty(Service service, String
            propertiesId, String newPropertyName, String oldPropertyName) {
        assertNotNull(service, "service");
        assertNotNullOrEmpty(propertiesId, "propertiesId");
        assertNotNullOrEmpty(newPropertyName, "newPropertyName");
        assertNotNullOrEmpty(oldPropertyName, "oldPropertyName");

        UniquePropertiesRequest request = new UniquePropertiesRequest();
        request.toRemove = Collections.singletonList(oldPropertyName);
        request.toAdd = Collections.singletonList(newPropertyName);

        String uriPath = UriUtils.buildUriPath(UniquePropertiesService.FACTORY_LINK, propertiesId);

        DeferredResult<Boolean> result = new DeferredResult<>();

        Operation patch = Operation.createPatch(service, uriPath)
                .setBody(request)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        if (o.getStatusCode() == Operation.STATUS_CODE_CONFLICT) {
                            result.complete(true);
                            return;
                        }
                        result.fail(ex);
                        return;
                    }
                    result.complete(false);
                });

        service.sendRequest(patch);
        return result;
    }
}
