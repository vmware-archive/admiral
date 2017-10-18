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
import static com.vmware.admiral.common.util.ServiceUtils.addServiceRequestRoute;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.compute.ComponentDescription;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescriptionExpanded;
import com.vmware.admiral.service.common.CloneableResource;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ReflectionUtils;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Clone a composite description with a new copy of the container descriptions inside it.
 */
public class CompositeDescriptionCloneService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.COMPOSITE_DESC_CLONE;
    public static final String REVERSE_PARENT_LINKS_PARAM = "reverseParentLinks";
    public static final String CUSTOM_PROPERTIES_CLONED_DESCRIPTION = "__cloned_description";

    @Override
    public void handlePost(Operation post) {
        if (!post.hasBody()) {
            post.fail(new IllegalArgumentException("body is required"));
            return;
        }

        Map<String, String> queryParams = UriUtils.parseUriQueryParams(post.getUri());

        String reverseParam = queryParams.remove(REVERSE_PARENT_LINKS_PARAM);
        boolean reverse = Boolean.TRUE.equals(Boolean.parseBoolean(reverseParam));

        String projectLink = OperationUtil.extractProjectFromHeader(post);

        try {
            CompositeDescription cd = post.getBody(CompositeDescription.class);
            validateStateOnStart(cd);

            String requestURL = cd.documentSelfLink + ManagementUriParts.EXPAND_SUFFIX;
            cloneCompositeDescription(requestURL, reverse, null, projectLink,
                    (cdClone) -> post.setBody(cdClone).complete());
        } catch (Throwable e) {
            logSevere(e);
            post.fail(e);
        }
    }

    private void validateStateOnStart(CompositeDescription state) {
        assertNotNull(state.documentSelfLink, "documentSelfLink");
    }

    private void cloneCompositeDescription(String compDescLink, boolean reverse,
            CompositeDescriptionExpanded cdExpanded, String projectLink,
            Consumer<CompositeDescription> callbackFunction) {
        if (cdExpanded == null) {
            getCompositeDesc(compDescLink, projectLink, (compDesc) -> cloneCompositeDescription(
                    compDescLink, reverse, compDesc, projectLink, callbackFunction));
            return;
        }

        CompositeDescription cd = prepareCompositeDescriptionForClone(cdExpanded, reverse);

        List<Operation> cloneOperations = new ArrayList<Operation>();

        for (ComponentDescription desc : cdExpanded.componentDescriptions) {
            if (desc.getServiceDocument() instanceof CloneableResource) {
                Operation clonedOperation = ((CloneableResource) desc.getServiceDocument()).createCloneOperation
                        (this);
                clonedOperation.addRequestHeader(CUSTOM_PROPERTIES_CLONED_DESCRIPTION, Boolean
                        .toString(true));
                cloneOperations.add(clonedOperation);
            } else {
                cloneOperations.add(createCloneOperation(desc.type, desc.getServiceDocument(),
                        projectLink));
            }
        }

        Operation cloneCompositeDescOp = Operation
                .createPost(this, ManagementUriParts.COMPOSITE_DESC)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failed to create a composite description: %s",
                                Utils.toString(e));
                        return;
                    }
                });

        if (projectLink != null && !projectLink.isEmpty()) {
            cloneCompositeDescOp.addRequestHeader(OperationUtil.PROJECT_ADMIRAL_HEADER,
                    projectLink);
        }
        cloneCompositeDescOp.addRequestHeader(CUSTOM_PROPERTIES_CLONED_DESCRIPTION, Boolean
                .toString(true));

        if (!cloneOperations.isEmpty()) {
            OperationJoin cloneComponentsJoin = OperationJoin.create(cloneOperations);

            cloneComponentsJoin.setCompletion((cloneOps, failures) -> {
                for (Operation cloneOp : cloneOps.values()) {
                    if (failures != null) {
                        logSevere("Failed to clone description: %s",
                                Utils.toString(failures));
                        return;
                    }

                    ServiceDocument clonedDescription = cloneOp
                            .getBody(ServiceDocument.class);

                    cd.descriptionLinks.add(clonedDescription.documentSelfLink);
                }

                cloneCompositeDescOp.setBody(cd);
            });

            OperationSequence
                    .create(cloneComponentsJoin)
                    .next(cloneCompositeDescOp)
                    .setCompletion((ops, failures) -> {
                        if (failures != null) {
                            logSevere("Failed to clone a composite description: %s",
                                    Utils.toString(failures));
                            return;
                        }
                        Operation o = ops.get(cloneCompositeDescOp.getId());
                        CompositeDescription cdCloned = o.getBody(CompositeDescription.class);
                        patchDescriptionsAndAccept(cdExpanded, cdCloned, reverse, projectLink,
                                callbackFunction);
                    }).sendWith(this);

            return;
        }

        cloneCompositeDescOp
                .setBody(cd)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failed to clone a composite description: %s", Utils.toString(e));
                        return;
                    }
                    CompositeDescription cdCloned = o.getBody(CompositeDescription.class);
                    patchDescriptionsAndAccept(cdExpanded, cdCloned, reverse, projectLink,
                            callbackFunction);
                }).sendWith(this);
    }

    private void patchDescriptionsAndAccept(CompositeDescription cd, CompositeDescription cdCloned,
            boolean reverse, String projectLink, Consumer<CompositeDescription> callbackFunction) {

        if (!reverse) {
            // standard clone
            callbackFunction.accept(cdCloned);
            return;
        }

        CompositeDescription cdPatch = new CompositeDescription();
        cdPatch.documentSelfLink = cd.documentSelfLink;
        cdPatch.parentDescriptionLink = cdCloned.documentSelfLink;

        Operation patchOriginalDescriptionOp = Operation
                .createPatch(this, cdPatch.documentSelfLink)
                .setBody(cdPatch)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failed to reverse composite description parent link: %s",
                                Utils.toString(e));
                        return;
                    }
                    callbackFunction.accept(cdCloned);
                });

        if (projectLink != null && !projectLink.isEmpty()) {
            patchOriginalDescriptionOp.addRequestHeader(OperationUtil.PROJECT_ADMIRAL_HEADER,
                    projectLink);
        }

        if ((cdCloned.descriptionLinks == null) || cdCloned.descriptionLinks.isEmpty()) {
            // no components to reverse, only the composite description itself
            sendRequest(patchOriginalDescriptionOp);
            return;
        }

        List<Operation> prepareReverseOperations = new ArrayList<Operation>();
        List<Operation> doReverseOperations = new ArrayList<Operation>();

        for (String descriptionLink : cdCloned.descriptionLinks) {

            Operation op = Operation.createGet(this, descriptionLink).setCompletion((o, e) -> {
                if (e != null) {
                    logSevere("Failed to reverse component description parent link: %s",
                            Utils.toString(e));
                    return;
                }

                Class<?> clazz = CompositeComponentRegistry
                        .metaByDescriptionLink(descriptionLink).descriptionClass;

                Object body = o.getBody(clazz);

                try {
                    Field parentField = ReflectionUtils.getFieldIfExists(clazz,
                            "parentDescriptionLink");

                    String clonedParentDescriptionLink = (String) parentField.get(body);

                    doReverseOperations.add(createPatchParentDescriptionLinkOperation(clazz,
                            parentField, descriptionLink, clonedParentDescriptionLink, projectLink));

                    doReverseOperations.add(createPatchParentDescriptionLinkOperation(clazz,
                            parentField, "", descriptionLink, projectLink));

                } catch (Exception ex) {
                    logSevere("Failed to reverse component description parent link: %s",
                            Utils.toString(ex));
                    return;
                }
            });

            prepareReverseOperations.add(op);
        }

        OperationSequence
                .create(OperationJoin.create(prepareReverseOperations))
                .setCompletion((ops, failures) -> {
                    if (failures != null) {
                        logSevere("Failed to clone a composite description: %s",
                                Utils.toString(failures));
                        return;
                    }

                    OperationSequence
                            .create(OperationJoin.create(doReverseOperations))
                            .next(patchOriginalDescriptionOp)
                            .setCompletion((ops2, failures2) -> {
                                if (failures2 != null) {
                                    logSevere("Failed to clone a composite description: %s",
                                            Utils.toString(failures2));
                                    return;
                                }
                            }).sendWith(this);
                }).sendWith(this);
    }

    private Operation createPatchParentDescriptionLinkOperation(Class<?> clazz, Field parentField,
            String parentDescriptionLink, String descriptionLink, String projectLink)
            throws Exception {

        Object patchBody = clazz.newInstance();
        parentField.set(patchBody, parentDescriptionLink);

        Operation patch = Operation.createPatch(this, descriptionLink)
                .setBody(patchBody)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere(
                                "Failed to reverse component description parent link: %s",
                                Utils.toString(e));
                        return;
                    }
                });

        if (projectLink != null && projectLink.isEmpty()) {
            patch.addRequestHeader(OperationUtil.PROJECT_ADMIRAL_HEADER, projectLink);
        }

        return patch;
    }

    private Operation createCloneOperation(String resourceType, ServiceDocument component,
            String projectLink) {
        if (component instanceof ResourceState) {
            String factoryLink = CompositeComponentRegistry
                    .descriptionFactoryLinkByDescriptionLink(component.documentSelfLink);
            if (factoryLink == null) {
                throw new LocalizableValidationException(
                        "Cannot clone unsupported type " + resourceType,
                        "compute.clone.unsupported.type", resourceType);
            }
            ResourceState cloned = Utils.clone((ResourceState) component);
            if (cloned.customProperties == null) {
                cloned.customProperties = new HashMap<>();
            }
            cloned.customProperties.put(CloneableResource.PARENT_RESOURCE_LINK_PROPERTY_NAME,
                    cloned.documentSelfLink);
            cloned.documentSelfLink = null;

            Operation post = Operation.createPost(this, factoryLink)
                    .setBody(cloned);

            if (projectLink != null && projectLink.isEmpty()) {
                post.addRequestHeader(OperationUtil.PROJECT_ADMIRAL_HEADER, projectLink);
            }

            return post;
        }
        throw new LocalizableValidationException("Cannot clone unsupported type " + resourceType,
                "compute.clone.unsupported.type", resourceType);
    }

    private void getCompositeDesc(String compDescLink, String projectLink,
            Consumer<CompositeDescriptionExpanded> callback) {

        Operation get = Operation
                .createGet(this, compDescLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failed to clone a composite description", Utils.toString(e));
                        return;
                    }

                    CompositeDescriptionExpanded compositeDescription = o
                            .getBody(CompositeDescriptionExpanded.class);
                    callback.accept(compositeDescription);
                });

        if (projectLink != null && !projectLink.isEmpty()) {
            get.addRequestHeader(OperationUtil.PROJECT_ADMIRAL_HEADER, projectLink);
        }

        sendRequest(get);
    }

    private CompositeDescription prepareCompositeDescriptionForClone(
            CompositeDescriptionExpanded cdExpanded, boolean reverse) {

        CompositeDescription cd = new CompositeDescription();
        cd.name = cdExpanded.name;
        cd.status = cdExpanded.status;
        cd.lastPublished = null;
        if (reverse) {
            cd.parentDescriptionLink = null;
        } else {
            cd.parentDescriptionLink = cdExpanded.documentSelfLink;
        }
        cd.descriptionLinks = new ArrayList<String>();
        cd.documentSelfLink = null;
        cd.customProperties = cdExpanded.customProperties;
        cd.tenantLinks = cdExpanded.tenantLinks;
        cd.bindings = cdExpanded.bindings;

        return cd;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        addServiceRequestRoute(d, Action.POST,
                "Post a composite description to clone it with a new copy of the "
                        + "container descriptions inside it.", CompositeDescription.class);
        return d;
    }
}
