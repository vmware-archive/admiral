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

package com.vmware.admiral.compute.container;

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Describes grouping of multiple container instances deployed at the same time. It represents a
 * template definition of related services or an application.
 */
public class CompositeComponentService extends StatefulService {

    public static class CompositeComponent extends
            com.vmware.admiral.service.common.MultiTenantDocument {

        /** Name of composite description */
        @Documentation(description = "Name of composite description.")
        public String name;

        /** (Optional) CompositeDescription link */
        @Documentation(description = "CompositeDescription link.")
        @PropertyOptions(usage = { PropertyUsageOption.LINK, PropertyUsageOption.OPTIONAL })
        public String compositeDescriptionLink;

        @Documentation(description = "Component links.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public List<String> componentLinks;

        /** Composite component creation time in milliseconds */
        @Documentation(description = "Container created time in milliseconds")
        @UsageOption(option = PropertyUsageOption.SINGLE_ASSIGNMENT)
        public long created;
    }

    public CompositeComponentService() {
        super(CompositeComponent.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleCreate(Operation startPost) {
        if (!checkForBody(startPost)) {
            return;
        }

        try {
            CompositeComponent state = startPost.getBody(CompositeComponent.class);
            state.created = System.currentTimeMillis();
            logFine("Composite created: %s. Refer: %s", state.documentSelfLink,
                    startPost.getReferer());
            validateStateOnStart(state);
            startPost.complete();
        } catch (Throwable e) {
            logSevere(e);
            startPost.fail(e);
        }
    }

    private void validateStateOnStart(CompositeComponent state) {
        assertNotEmpty(state.name, "name");
    }

    @Override
    public void handlePut(Operation put) {
        if (!checkForBody(put)) {
            return;
        }

        CompositeComponent putBody = put.getBody(CompositeComponent.class);

        try {
            validateStateOnStart(putBody);
            this.setState(put, putBody);
            put.setBody(null).complete();
        } catch (Throwable e) {
            put.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        CompositeComponent currentState = getState(patch);
        CompositeComponent patchBody = patch.getBody(CompositeComponent.class);

        ServiceDocumentDescription docDesc = getDocumentTemplate().documentDescription;
        String currentSignature = Utils.computeSignature(currentState, docDesc);

        currentState.name = PropertyUtils.mergeProperty(currentState.name, patchBody.name);
        currentState.compositeDescriptionLink = PropertyUtils.mergeProperty(
                currentState.compositeDescriptionLink, patchBody.compositeDescriptionLink);

        boolean deletePatch = patch.getUri().getQuery() != null
                && patch.getUri().getQuery().contains(UriUtils.URI_PARAM_INCLUDE_DELETED);
        boolean deleteDocument = false;
        if (deletePatch && patchBody.componentLinks != null && currentState.componentLinks != null) {
            for (String componentLink : patchBody.componentLinks) {
                currentState.componentLinks.remove(componentLink);
            }
            if (currentState.componentLinks.isEmpty()) {
                deleteDocument = true;
            }
        } else {
            currentState.componentLinks = PropertyUtils.mergeLists(currentState.componentLinks,
                    patchBody.componentLinks);
        }

        String newSignature = Utils.computeSignature(currentState, docDesc);

        // if the signature hasn't change we shouldn't modify the state
        if (currentSignature.equals(newSignature)) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        }

        patch.complete();
        if (deleteDocument) {
            deleteCompositeDescription(currentState.compositeDescriptionLink);
            ServiceUtils.sendSelfDelete(this);
        }
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        CompositeComponent template = (CompositeComponent) super.getDocumentTemplate();
        template.name = "name (string)";
        template.compositeDescriptionLink = "compositeDescriptionLink (string) (optional)";
        template.componentLinks = new ArrayList<String>(1);
        template.componentLinks.add("componentLink (string)");

        return template;
    }

    private void deleteCompositeDescription(String compositeDescriptionLink) {
        if (compositeDescriptionLink == null || compositeDescriptionLink.isEmpty()) {
            return;
        }

        sendRequest(Operation.createGet(this, compositeDescriptionLink)
                .setCompletion((o, e) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        logFine("CompositeDescription not found %s", compositeDescriptionLink);
                        return;
                    }
                    if (e != null) {
                        logWarning("Can't find composite description. Error: %s", Utils.toString(e));
                        return;
                    }

                    CompositeDescription cd = o.getBody(CompositeDescription.class);

                    if (cd.parentDescriptionLink == null) {
                        return;
                    }

                    URI uri = UriUtils.buildUri(getHost(), cd.documentSelfLink);
                    sendRequest(Operation
                            .createDelete(uri)
                            .setBody(new ServiceDocument())
                            .setCompletion((op, ex) -> {
                                if (ex != null) {
                                    logWarning("Error deleting CompositeDescription: %s. Exception: %s",
                                            cd.documentSelfLink, ex instanceof CancellationException
                                                    ? "CancellationException" : Utils.toString(ex));
                                }
                            }));
                }));

    }
}
