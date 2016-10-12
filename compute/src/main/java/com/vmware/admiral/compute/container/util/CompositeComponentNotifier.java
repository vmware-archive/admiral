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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;

import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class CompositeComponentNotifier {

    public static void notifyCompositionComponents(Service service,
            List<String> compositeComponentLinks, Action action) {
        if (compositeComponentLinks == null || compositeComponentLinks.isEmpty()) {
            return;
        }

        for (String compositeComponentLink : compositeComponentLinks) {
            notifyCompositionComponent(service, compositeComponentLink, action);
        }
    }

    public static void notifyCompositionComponent(Service service,
            String compositeComponentLink, Action action) {
        if (compositeComponentLink == null || compositeComponentLink.isEmpty()) {
            return;
        }

        service.sendRequest(Operation.createGet(service, compositeComponentLink)
                .setCompletion((o, e) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        Utils.log(service.getClass(), service.getUri().toString(), Level.FINE,
                                "CompositeComponent not found %s", compositeComponentLink);
                        return;
                    }
                    if (e != null) {
                        Utils.log(service.getClass(), service.getUri().toString(), Level.WARNING,
                                "Can't find composite component. Error: %s", Utils.toString(e));
                        return;
                    }

                    notify(service, compositeComponentLink, action);
                }));

    }

    private static void notify(Service service, String compositeComponentLink, Action action) {
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
                        Utils.log(service.getClass(), service.getUri().toString(), Level.WARNING,
                                "Error notifying CompositeContainer: %s. Exception: %s",
                                compositeComponentLink, ex instanceof CancellationException
                                        ? "CancellationException" : Utils.toString(ex));
                    }
                }));
    }

    public static void notifyCompositionComponentsOnChange(StatefulService service, Action action,
            List<String> newCompositeComponentLinks, List<String> currentCompositeComponentLink) {

        if (newCompositeComponentLinks == null) {
            newCompositeComponentLinks = Collections.emptyList();
        }

        if (currentCompositeComponentLink == null) {
            currentCompositeComponentLink = Collections.emptyList();
        }

        Set<String> toDelete = new HashSet<>(currentCompositeComponentLink);
        toDelete.removeAll(newCompositeComponentLinks);
        for (String componentLink : toDelete) {
            notifyCompositionComponent(service, componentLink, Action.DELETE);
        }

        Set<String> toNotify = new HashSet<>(newCompositeComponentLinks);
        toNotify.removeAll(currentCompositeComponentLink);
        for (String componentLink : toNotify) {
            notifyCompositionComponent(service, componentLink, action);
        }
    }

    public static void notifyCompositionComponentOnChange(StatefulService service, Action action,
            String newCompositeComponentLink, String currentCompositeComponentLink) {
        if (currentCompositeComponentLink != null && newCompositeComponentLink == null) {
            notifyCompositionComponent(service, currentCompositeComponentLink, Action.DELETE);
        } else if ((currentCompositeComponentLink == null && newCompositeComponentLink != null)
                || (currentCompositeComponentLink != null
                        && !currentCompositeComponentLink.equals(newCompositeComponentLink))) {
            notifyCompositionComponent(service, newCompositeComponentLink, action);
        }
    }

    public static void notifyCompositionComponent(Service service, ResourceState state,
            String compositeComponentLink, Action action) {
        if (compositeComponentLink == null || compositeComponentLink.isEmpty()) {
            return;
        }

        service.sendRequest(Operation.createGet(service, compositeComponentLink)
                .setCompletion((o, e) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        Utils.log(service.getClass(), service.getUri().toString(), Level.FINE,
                                "CompositeComponent not found %s", compositeComponentLink);
                        return;
                    }
                    if (e != null) {
                        Utils.log(service.getClass(), service.getUri().toString(), Level.WARNING,
                                "Can't find composite component. Error: %s", Utils.toString(e));
                        return;
                    }

                    notify(service, state, compositeComponentLink, action);
                }));

    }

    private static void notify(Service service, ResourceState state, String compositeComponentLink,
            Action action) {
        CompositeComponent body = new CompositeComponent();
        body.documentSelfLink = compositeComponentLink;
        body.componentLinks = new ArrayList<>(1);
        body.componentLinks.add(state.documentSelfLink);

        URI uri = UriUtils.buildUri(service.getHost(), compositeComponentLink);
        if (Action.DELETE == action) {
            uri = UriUtils.extendUriWithQuery(uri, UriUtils.URI_PARAM_INCLUDE_DELETED,
                    Boolean.TRUE.toString());
        }

        service.sendRequest(Operation.createPatch(uri)
                .setBody(body)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        Utils.log(service.getClass(), service.getUri().toString(), Level.WARNING,
                                "Error notifying CompositeContainer: %s. Exception: %s",
                                compositeComponentLink, ex instanceof CancellationException
                                        ? "CancellationException" : Utils.toString(ex));
                    }
                }));
    }

    public static void notifyCompositionComponentOnChange(StatefulService service,
            ResourceState state, Action action,
            String newCompositeComponentLink,
            String currentCompositeComponentLink) {
        if (currentCompositeComponentLink != null && newCompositeComponentLink == null) {
            notifyCompositionComponent(service, state, currentCompositeComponentLink,
                    Action.DELETE);
        } else if ((currentCompositeComponentLink == null
                && newCompositeComponentLink != null)
                || (currentCompositeComponentLink != null
                        && !currentCompositeComponentLink
                                .equals(newCompositeComponentLink))) {
            notifyCompositionComponent(service, state, newCompositeComponentLink, action);
        }
    }
}
