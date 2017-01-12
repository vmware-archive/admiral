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

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.CloneableResource;
import com.vmware.admiral.compute.ComponentDescription;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescriptionExpanded;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

/**
 * Clone a composite description with a new copy of the container descriptions inside it.
 */
public class CompositeDescriptionCloneService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.COMPOSITE_DESC_CLONE;

    @Override
    public void handlePost(Operation post) {
        if (!post.hasBody()) {
            post.fail(new IllegalArgumentException("body is required"));
            return;
        }

        try {
            CompositeDescription cd = post.getBody(CompositeDescription.class);
            validateStateOnStart(cd);

            String requestURL = cd.documentSelfLink + ManagementUriParts.EXPAND_SUFFIX;
            cloneCompositeDescription(requestURL, null, (cp) -> post.setBody(cp).complete());
        } catch (Throwable e) {
            logSevere(e);
            post.fail(e);
        }
    }

    private void validateStateOnStart(CompositeDescription state) {
        assertNotNull(state.documentSelfLink, "documentSelfLink");
    }

    private void cloneCompositeDescription(String compDescLink,
            CompositeDescriptionExpanded cdExpanded,
            Consumer<CompositeDescription> callbackFunction) {
        if (cdExpanded == null) {
            getCompositeDesc(compDescLink, (compDesc) -> cloneCompositeDescription(compDescLink,
                    compDesc, callbackFunction));
            return;
        }

        CompositeDescription cd = prepareCompositeDescriptionForClone(cdExpanded);

        List<Operation> cloneOperations = new ArrayList<Operation>();

        for (ComponentDescription desc : cdExpanded.componentDescriptions) {
            if (desc.getServiceDocument() instanceof CloneableResource) {
                cloneOperations
                        .add(((CloneableResource) desc.getServiceDocument())
                                .createCloneOperation(this));
            }  else {
                cloneOperations.add(createCloneOperation(desc.type, desc.getServiceDocument()));
            }
        }

        Operation cloneCompositeDesc = Operation
                .createPost(this, ManagementUriParts.COMPOSITE_DESC)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failed to create a composite description", Utils.toString(e));
                        return;
                    }
                });

        if (!cloneOperations.isEmpty()) {
            OperationJoin cloneComponents = OperationJoin
                    .create(cloneOperations);

            cloneComponents.setCompletion((cloneOps, failures) -> {
                for (Operation cloneOp : cloneOps.values()) {
                    if (failures != null) {
                        logSevere("Failed to clone description",
                                Utils.toString(failures));
                        return;
                    }

                    ServiceDocument clonedDescription = cloneOp
                            .getBody(ServiceDocument.class);

                    cd.descriptionLinks.add(clonedDescription.documentSelfLink);
                }

                cloneCompositeDesc.setBody(cd);
            });

            OperationSequence
                    .create(cloneComponents)
                    .next(cloneCompositeDesc)
                    .setCompletion((ops, failures) -> {
                        if (failures != null) {
                            logSevere("Failed to clone a composite description",
                                    Utils.toString(failures));
                            return;
                        }

                        Operation o = ops.get(cloneCompositeDesc.getId());
                        CompositeDescription clonedCompositeDesc = o
                                .getBody(CompositeDescription.class);

                        callbackFunction.accept(clonedCompositeDesc);
                    }).sendWith(this);

            return;
        }

        cloneCompositeDesc
                .setBody(cd)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failed to clone a composite description", Utils.toString(e));
                        return;
                    }
                    CompositeDescription clonedCompositeDesc = o
                            .getBody(CompositeDescription.class);

                    callbackFunction.accept(clonedCompositeDesc);
                }).sendWith(this);

    }

    private Operation createCloneOperation(String resourceType, ServiceDocument component) {
        if (component instanceof ResourceState) {
            String factoryLink = CompositeComponentRegistry
                    .factoryLinkByDescriptionLink(component.documentSelfLink);
            if (factoryLink == null) {
                throw new LocalizableValidationException("Cannot clone unsupported type " + resourceType,
                        "compute.clone.unsupported.type", resourceType);
            }
            ResourceState cloned = Utils.clone((ResourceState) component);
            if (cloned.customProperties == null) {
                cloned.customProperties = new HashMap<>();
            }
            cloned.customProperties.put(CloneableResource.PARENT_RESOURCE_LINK_PROPERTY_NAME,
                    cloned.documentSelfLink);
            cloned.documentSelfLink = null;
            return Operation.createPost(this, factoryLink).setBody(cloned);
        }
        throw new LocalizableValidationException("Cannot clone unsupported type " + resourceType,
                "compute.clone.unsupported.type", resourceType);
    }

    private void getCompositeDesc(String compDescLink,
            Consumer<CompositeDescriptionExpanded> callback) {
        sendRequest(Operation
                .createGet(this, compDescLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failed to clone a composite description", Utils.toString(e));
                        return;
                    }

                    CompositeDescriptionExpanded compositeDescription = o
                            .getBody(CompositeDescriptionExpanded.class);
                    callback.accept(compositeDescription);
                }));
    }

    private CompositeDescription prepareCompositeDescriptionForClone(
            CompositeDescriptionExpanded cdExpanded) {

        CompositeDescription cd = new CompositeDescription();
        cd.name = cdExpanded.name;
        cd.status = cdExpanded.status;
        cd.lastPublished = null;
        cd.parentDescriptionLink = cdExpanded.documentSelfLink;
        cd.descriptionLinks = new ArrayList<String>();
        cd.documentSelfLink = null;
        cd.customProperties = cdExpanded.customProperties;
        cd.tenantLinks = cdExpanded.tenantLinks;
        cd.bindings = cdExpanded.bindings;

        return cd;
    }
}
