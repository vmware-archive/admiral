/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.container.util;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask;

public class ResourceDescriptionUtil {

    public static void deleteResourceDescription(ServiceHost host, String resourceDescriptionLink) {
        if (resourceDescriptionLink == null || resourceDescriptionLink.isEmpty()) {
            host.log(Level.WARNING, "No description is provided.");
            return;
        }

        getResourceDescription(host, resourceDescriptionLink, (op) -> {
            if (op == null) {
                host.log(Level.INFO, String.format("Resource [%s] already deleted.", resourceDescriptionLink));
                return;
            }

            Object obj = op.getBodyRaw();

            if (obj instanceof ContainerDescription) {
                deleteResourceDescription(host, resourceDescriptionLink, ContainerState.class);
            } else if (obj instanceof ContainerNetworkDescription) {
                deleteResourceDescription(host, resourceDescriptionLink, ContainerNetworkState.class);
            } else if (obj instanceof ContainerVolumeDescription) {
                deleteResourceDescription(host, resourceDescriptionLink, ContainerVolumeState.class);
            }
        });
    }

    public static void deleteClonedCompositeDescription(ServiceHost host, String resourceDescriptionLink) {
        if (resourceDescriptionLink == null || resourceDescriptionLink.isEmpty()) {
            host.log(Level.WARNING, "No description is provided.");
            return;
        }

        getCompositeDescription(host, resourceDescriptionLink, (cd) -> {
            if (cd.parentDescriptionLink == null) {
                host.log(Level.INFO, String.format("Skipping the deletion of not cloned composite description - %s",
                        cd.documentSelfLink));
                return;
            }

            cd.descriptionLinks.stream().forEach(desc -> {
                deleteResourceDescription(host, desc);
            });

            host.log(Level.INFO, String.format("Sending delete request to [%s]", resourceDescriptionLink));
            host.sendRequest(Operation.createDelete(host, resourceDescriptionLink).setReferer(host.getUri()));
        });
    }

    private static void deleteResourceDescription(ServiceHost host, String resourceDescriptionLink, Class<? extends ServiceDocument> stateClass) {
        QueryTask resourceStateQueryTask = QueryUtil.buildQuery(stateClass, true);

        QueryUtil.addListValueClause(resourceStateQueryTask,
                ContainerState.FIELD_NAME_DESCRIPTION_LINK,
                Arrays.asList(resourceDescriptionLink));

        QueryUtil.addCountOption(resourceStateQueryTask);

        new ServiceDocumentQuery<>(host, ContainerState.class)
                .query(resourceStateQueryTask, (r) -> {
                    if (r.hasException()) {
                        host.log(Level.SEVERE, String.format("Failed to retrieve containers, sharing the same" + " description: %s -%s",
                                r.getDocumentSelfLink(), r.getException()));
                    } else if (r.hasResult() && r.getCount() != 0) {
                        host.log(Level.FINE, String.format("Containers, sharing the same description: %s = %s",
                                resourceDescriptionLink, r.getCount()));
                    } else {
                        host.log(Level.INFO, String.format("No other resources shares the same description. Deleting the description: %s", resourceDescriptionLink));
                        // delete the no longer used description
                        host.sendRequest(Operation.createDelete(host, resourceDescriptionLink).setReferer(host.getUri()));
                    }
                });
    }

    private static void getCompositeDescription(ServiceHost host, String resourceDescriptionLink,
            Consumer<CompositeDescription> callbackFunction) {

        host.sendRequest(Operation.createGet(host, resourceDescriptionLink)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        host.log(Level.WARNING, String.format("CompositeDescription not found %s", resourceDescriptionLink));
                        return;
                    }

                    if (e != null) {
                        String errMsg = String.format(
                                "Failure retrieving composite description state: %s ",
                                resourceDescriptionLink);
                        host.log(Level.WARNING, errMsg);
                        return;
                    }

                    CompositeDescription desc = o.getBody(CompositeDescription.class);
                    callbackFunction.accept(desc);
                }));
    }

    private static void getResourceDescription(ServiceHost host, String resourceDescriptionLink,
            Consumer<Operation> callbackFunction) {

        host.sendRequest(Operation.createGet(host, resourceDescriptionLink)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        String errMsg = String.format(
                                "Failure retrieving description: %s. Error: %s ",
                                resourceDescriptionLink, e.getMessage());
                        host.log(Level.WARNING, errMsg);
                        callbackFunction.accept(null);
                        return;
                    }

                    callbackFunction.accept(o);
                }));
    }
}
