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

package com.vmware.admiral.compute.container.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;

import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class CompositeComponentNotifier {

    public static void notifyCompositionComponent(StatefulService service,
            String compositeComponentLink, Action action) {
        if (compositeComponentLink == null || compositeComponentLink.isEmpty()) {
            return;
        }

        service.sendRequest(Operation.createGet(service, compositeComponentLink)
                .setCompletion((o, e) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        service.logFine("CompositeComponent not found %s", compositeComponentLink);
                        return;
                    }
                    if (e != null) {
                        service.logWarning("Can't find composite component. Error: %s",
                                Utils.toString(e));
                        return;
                    }

                    notify(service, compositeComponentLink, action);
                }));

    }

    private static void notify(StatefulService service, String compositeComponentLink, Action action) {
        CompositeComponent body = new CompositeComponent();
        body.documentSelfLink = compositeComponentLink;
        body.componentLinks = new ArrayList<>(1);
        body.componentLinks.add(service.getSelfLink());

        URI uri = UriUtils.buildUri(service.getHost(), compositeComponentLink);
        if (Action.DELETE == action) {
            uri = UriUtils.extendUriWithQuery(uri, UriUtils.URI_PARAM_INCLUDE_DELETED,
                    Boolean.TRUE.toString());
        }

        service.sendRequest(Operation.createPatch(uri)
                .setBody(body)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        service.logWarning("Error notifying CompositeContainer: %s. Exception: %s",
                                compositeComponentLink, ex instanceof CancellationException
                                        ? "CancellationException" : Utils.toString(ex));
                    }
                }));
    }

    public static void notifyCompositionComponentOnChange(StatefulService service, Action action,
            String newCompositeComponentLink,
            String currentCompositeComponentLink) {
        if (currentCompositeComponentLink != null && newCompositeComponentLink == null) {
            notifyCompositionComponent(service, currentCompositeComponentLink, Action.DELETE);
        } else if ((currentCompositeComponentLink == null
                && newCompositeComponentLink != null)
                || (currentCompositeComponentLink != null
                && !currentCompositeComponentLink
                        .equals(newCompositeComponentLink))) {
            notifyCompositionComponent(service, newCompositeComponentLink, action);
        }
    }
}
