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

package com.vmware.admiral.request.compute.enhancer;

import static com.vmware.admiral.compute.ComputeConstants.COMPUTE_CONFIG_CONTENT_PROP_NAME;
import static com.vmware.admiral.request.compute.enhancer.EnhancerUtils.getCustomProperty;

import java.net.URI;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceHost;

public class ComputeStateDiskEnhancer extends ComputeEnhancer {

    private ServiceHost host;
    private URI referer;

    public ComputeStateDiskEnhancer(ServiceHost host, URI referer) {
        this.host = host;
        this.referer = referer;
    }

    @Override
    public DeferredResult<ComputeState> enhance(EnhanceContext context, ComputeState cs) {
        String cloudInit = getCustomProperty(cs.customProperties, COMPUTE_CONFIG_CONTENT_PROP_NAME);

        DeferredResult<ComputeState> result = new DeferredResult<>();

        host.log(Level.INFO, "Cloud config file to use [%s]", cloudInit);
        if (cloudInit != null) {
            cs.customProperties.put(COMPUTE_CONFIG_CONTENT_PROP_NAME, cloudInit);
            updateDisks(cs, cloudInit, result);
        } else {
            result.complete(cs);
        }
        return result;
    }

    private void updateDisks(ComputeState cs, String cloudInit,
            DeferredResult<ComputeState> result) {
        if (cs.diskLinks == null || cs.diskLinks.isEmpty()) {
            result.complete(cs);
            return;
        }

        OperationJoin.JoinedCompletionHandler getDisksCompletion = (opsGetDisks,
                exsGetDisks) -> {
            if (exsGetDisks != null && !exsGetDisks.isEmpty()) {
                result.fail(exsGetDisks.values().iterator().next());
                return;
            }

            List<Operation> updateOperations = opsGetDisks.values().stream()
                    .map(op -> op.getBody(DiskService.DiskState.class))
                    .filter(diskState -> diskState.type == DiskService.DiskType.HDD)
                    .filter(diskState -> diskState.bootConfig != null
                            && diskState.bootConfig.files.length > 0)
                    .map(diskState -> {
                        diskState.bootConfig.files[0].contents = cloudInit;
                        return Operation.createPut(host, diskState.documentSelfLink)
                                .setReferer(referer)
                                .setBody(diskState);
                    }).collect(Collectors.toList());

            if (updateOperations.isEmpty()) {
                result.complete(cs);
                return;
            }
            OperationJoin.create(updateOperations).setCompletion((opsUpdDisks, exsUpdDisks) -> {
                if (exsUpdDisks != null && !exsUpdDisks.isEmpty()) {
                    result.fail(exsGetDisks.values().iterator().next());
                    return;
                }
                result.complete(cs);
            }).sendWith(host);
        };

        List<Operation> getDisksOperations = cs.diskLinks.stream()
                .map(link -> Operation.createGet(host, link).setReferer(referer))
                .collect(Collectors.toList());

        OperationJoin.create(getDisksOperations).setCompletion(getDisksCompletion)
                .sendWith(host);

    }

}
