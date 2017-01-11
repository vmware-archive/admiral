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

package com.vmware.admiral.compute.content;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;

/**
 * Since xenon model classes don't have "real java" references manipulating an object with its whole
 * hierarchy is difficult. The purpose of this class is to store everything in one place and provide
 * methods to retrieve/store the hierarchy recursively.
 */
public class NestedState {

    /**
     * class -> fieldName -> class (e.g ResourceState -> tagLinks -> TagState)
     */
    private static Map<Class<?>, Map<String, Class<? extends ServiceDocument>>> serviceDocuments = new HashMap<>();

    static {
        Map<String, Class<? extends ServiceDocument>> resourceStateMap = new HashMap<>();
        //some fields are added with an "alias" because they are renamed in the template
        resourceStateMap.put("tags", TagService.TagState.class);
        resourceStateMap.put("tagLinks", TagService.TagState.class);
        serviceDocuments.put(ResourceState.class, resourceStateMap);

        Map<String, Class<? extends ServiceDocument>> computeDescriptionMap = new HashMap<>();
        computeDescriptionMap.put("networks", TemplateNetworkInterfaceDescription.class);
        computeDescriptionMap.put("networkInterfaceDescLinks",
                TemplateNetworkInterfaceDescription.class);
        serviceDocuments
                .put(ComputeDescriptionService.ComputeDescription.class, computeDescriptionMap);

        Map<String, Class<? extends ServiceDocument>> computeStateMap = new HashMap<>();
        computeStateMap.put("networks", NetworkInterfaceService.NetworkInterfaceState.class);
        computeStateMap
                .put("networkInterfaceLinks", NetworkInterfaceService.NetworkInterfaceState.class);
        serviceDocuments.put(ComputeState.class, computeStateMap);
    }

    public ServiceDocument object;
    public Map<String /** self link */, NestedState> children = new HashMap<>();
    public String factoryLink;

    public NestedState() {
    }

    public NestedState(ServiceDocument serviceDocument) {
        this.object = serviceDocument;
    }

    public NestedState(ServiceDocument serviceDocument, Map<String, NestedState> children) {
        this.object = serviceDocument;
        this.children = children;
    }

    /**
     * Get the fields that are links and their respective types
     */
    public static Map<String, Class<? extends ServiceDocument>> getLinkFields(Class<?> type) {

        return serviceDocuments.entrySet().stream()
                .filter(entry -> entry.getKey().isAssignableFrom(type))
                .map(entry -> entry.getValue()).reduce((m1, m2) -> {
                    Map<String, Class<? extends ServiceDocument>> all = new HashMap<>();
                    all.putAll(m1);
                    all.putAll(m2);
                    return all;
                }).orElse(null);

    }

    public static Class<? extends ServiceDocument> getNestedObjectType(Class<?> type,
            String field) {

        return serviceDocuments.entrySet().stream()
                .filter(entry -> entry.getKey().isAssignableFrom(type) && entry.getValue()
                        .containsKey(field))
                .map(entry -> entry.getValue().get(field)).findFirst().orElse(null);

    }

    /**
     * Send a request recursively through all objects in the hierarchy
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public DeferredResult<Operation> sendRequest(Service sender, Service.Action action) {

        Map<String, Class<? extends ServiceDocument>> fields = getLinkFields(object.getClass());

        List<DeferredResult<Void>> allChildren = new ArrayList<>();

        if (fields != null) {
            for (String fieldName : fields.keySet()) {
                try {
                    Field field = object.getClass().getField(fieldName);
                    Object fieldValue = field.get(object);

                    if (fieldValue == null) {
                        continue;
                    }

                    //the field is a simple field
                    if (fieldValue instanceof String) {
                        String documentLink = (String) fieldValue;

                        NestedState child = children.get(documentLink);

                        DeferredResult<Operation> childPersistDeferredResult = child
                                .sendRequest(sender, action);

                        allChildren.add(childPersistDeferredResult
                                .thenAccept(operation -> {
                                    try {
                                        ServiceDocument document = operation
                                                .getBody(child.object.getClass());
                                        field.set(object, document.documentSelfLink);
                                        NestedState current = children.remove(documentLink);
                                        children.put(document.documentSelfLink, current);
                                    } catch (IllegalAccessException e) {
                                    }
                                }));
                    }

                    // The field is a collection
                    if (Collection.class.isAssignableFrom(field.getType())) {

                        List<DeferredResult<String>> documentLinks = new ArrayList<>();

                        ((Collection<String>) fieldValue).forEach(link -> {

                            NestedState child = children.get(link);

                            DeferredResult<Operation> childPersistDeferredResult = child
                                    .sendRequest(sender, action);

                            documentLinks.add(childPersistDeferredResult.thenApply(operation -> {
                                ServiceDocument childDocument = operation
                                        .getBody(child.object.getClass());
                                NestedState current = children.remove(link);
                                children.put(childDocument.documentSelfLink, current);
                                return childDocument.documentSelfLink;
                            }));
                        });

                        // Order of lists will be lost. This can be fixed if there is a use-case
                        allChildren.add(DeferredResult.allOf(documentLinks).thenAccept(links -> {
                            ((Collection) fieldValue).clear();
                            ((Collection) fieldValue).addAll(links);
                        }));
                    }

                } catch (NoSuchFieldException e) {
                    //do nothing. This may happen as there are aliases
                } catch (IllegalAccessException e) {
                    //this shouldn't happen. All fields are public
                }
            }
        }
        String link = getLink(action);

        Operation op = Operation.createPost(sender, link);
        op.setAction(action);

        return DeferredResult.allOf(allChildren)
                .thenCompose(voids -> {
                    op.setBody(object);
                    return sender.sendWithDeferredResult(op);
                });
    }

    //TODO figure out a better way to get the factory link
    private String getLink(Service.Action action) {
        if (action == Service.Action.POST) {

            if (factoryLink != null) {
                return factoryLink;
            }

            Class<?> searchType = object.getClass();
            while (ServiceDocument.class != searchType && searchType != null) {
                Class<?> enclosingClass = searchType.getEnclosingClass();
                if (enclosingClass != null) {
                    try {
                        Field field = enclosingClass.getField("FACTORY_LINK");
                        if (field != null) {
                            return (String) field.get(null);
                        }
                    } catch (IllegalAccessException | NoSuchFieldException e) {
                    }
                }
                searchType = searchType.getSuperclass();
            }
            return null;
        }
        return object.documentSelfLink;
    }

    @SuppressWarnings("unchecked")
    public static DeferredResult<NestedState> get(Service sender, String documentLink,
            Class<? extends ServiceDocument> type) {
        //get the document
        DeferredResult<? extends ServiceDocument> deferredResult = sender
                .sendWithDeferredResult(
                        Operation.createGet(sender, documentLink),
                        type);

        //check if there are links in the document and retrieve them too
        return deferredResult.thenCompose(document -> {
            Map<String, Class<? extends ServiceDocument>> fields = getLinkFields(
                    document.getClass());

            NestedState result = new NestedState();
            result.object = document;

            List<DeferredResult<Void>> childrenDeferredResult = new ArrayList<>();

            if (fields != null) {
                for (Map.Entry<String, Class<? extends ServiceDocument>> entry : fields
                        .entrySet()) {
                    String fieldName = entry.getKey();
                    try {
                        Field field = document.getClass().getField(fieldName);
                        Object fieldValue = field.get(document);

                        if (fieldValue == null) {
                            continue;
                        }

                        Class<? extends ServiceDocument> fieldClass = entry.getValue();
                        if (fieldValue instanceof String) {
                            DeferredResult<NestedState> nestedStateDeferredResult = get(sender,
                                    (String) fieldValue, fieldClass);

                            DeferredResult<Void> childDeferredResult = nestedStateDeferredResult
                                    .thenAccept(
                                            child -> result.children
                                                    .put(child.object.documentSelfLink, child));
                            childrenDeferredResult.add(childDeferredResult);

                        }

                        if (Collection.class.isAssignableFrom(field.getType())) {
                            ((Collection<String>) fieldValue).forEach(link -> {
                                DeferredResult<Void> childDeferredResult = get(sender, link,
                                        fieldClass)
                                        .thenAccept(child -> result.children
                                                .put(child.object.documentSelfLink, child));

                                childrenDeferredResult.add(childDeferredResult);
                            });
                        }
                    } catch (NoSuchFieldException e) {
                        //do nothing. This may happen as there are aliases
                    } catch (IllegalAccessException e) {
                        //this shouldn't happen. All fields are public
                    }
                }
            }

            return DeferredResult.allOf(childrenDeferredResult).thenApply(o -> result);
        });
    }
}
