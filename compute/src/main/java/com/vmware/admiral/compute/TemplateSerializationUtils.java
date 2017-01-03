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

package com.vmware.admiral.compute;

import static com.vmware.admiral.common.util.YamlMapper.objectMapper;
import static com.vmware.admiral.common.util.YamlMapper.objectWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.content.ComponentTemplate;
import com.vmware.admiral.compute.content.CompositeTemplate;
import com.vmware.admiral.compute.content.NestedState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.ServiceDocument;

/**
 * Serialize/deserialize templates.
 */
public class TemplateSerializationUtils {

    @JsonFilter(YamlMapper.SERVICE_DOCUMENT_FILTER)
    public static class ServiceDocumentMixin {

    }

    @JsonIgnoreProperties({ "id" })
    public abstract static class ResourceStateMixin {

    }

    static {
        YamlMapper.objectMapper().addMixIn(ServiceDocument.class, ServiceDocumentMixin.class);
        YamlMapper.objectMapper().addMixIn(ResourceState.class, ResourceStateMixin.class);
    }

    public static CompositeTemplate deserializeTemplate(Map<String, Object> initialMap) {

        Map<String, ComponentTemplate<?>> compositeTemplateComponents = new HashMap<>();

        Map<String, Object> components = (Map<String, Object>) initialMap
                .remove(BindingUtils.COMPONENTS);
        for (Map.Entry<String, Object> ce : components.entrySet()) {
            ComponentTemplate componentTemplate = deserializeComponent(
                    (Map<String, Object>) ce.getValue());
            compositeTemplateComponents.put(ce.getKey(), componentTemplate);
        }

        CompositeTemplate compositeTemplate = objectMapper()
                .convertValue(initialMap, CompositeTemplate.class);

        compositeTemplate.components = compositeTemplateComponents;

        return compositeTemplate;
    }

    public static Map<String, Object> serializeTemplate(CompositeTemplate template)
            throws IOException {

        //TODO figure out why directly deserializing to Map doesn't work
        Map result = objectMapper().readValue(
                objectWriter().writeValueAsString(template), Map.class);

        Map<String, Object> components = new HashMap<>();
        result.put("components", components);

        for (Map.Entry<String, ComponentTemplate<?>> entry : template.components.entrySet()) {
            String componentKey = entry.getKey();
            ComponentTemplate<?> componentTemplate = entry.getValue();

            //We have a special deserializer for the ComponentTemplate
            Map serializedComponentTemplate = objectMapper()
                    .readValue(objectWriter().writeValueAsString(componentTemplate), Map.class);
            serializedComponentTemplate.remove("children");

            components.put(componentKey, serializedComponentTemplate);

            if (componentTemplate.children == null || componentTemplate.children.isEmpty()) {
                continue;
            }

            NestedState state = new NestedState();
            state.object = (ServiceDocument) componentTemplate.data;
            state.children = componentTemplate.children;

            Map<String, Object> serializedDataComponentTemplate = serializeNestedState(state);

            serializedComponentTemplate.put("data", serializedDataComponentTemplate);
        }

        return result;
    }

    public static Map<String, Object> serializeNestedState(NestedState nestedState)
            throws IOException {
        Map<String, NestedState> children = nestedState.children;

        // if there are no children we can serialize right away
        if (children == null || children.isEmpty()) {
            return objectMapper()
                    .readValue(objectWriter().writeValueAsString(nestedState.object), Map.class);
        }

        // serialize the children recursively
        Map<String, Map<String, Object>> serializedChildren = new HashMap<>();
        for (Map.Entry<String, NestedState> entry : children.entrySet()) {
            Map<String, Object> serializedChild = serializeNestedState(entry.getValue());
            serializedChildren.put(entry.getKey(), serializedChild);
        }

        // replace the links to the child with the serialized child
        Map<String, Class<? extends ServiceDocument>> fields = NestedState.getLinkFields(
                nestedState.object.getClass());

        Map converted = objectMapper()
                .readValue(objectWriter().writeValueAsString(nestedState.object), Map.class);

        for (String fieldName : fields.keySet()) {
            Object fieldValue = converted.get(fieldName);

            if (fieldValue == null) {
                continue;
            }

            if (fieldValue instanceof String) {
                converted.put(fieldName, serializedChildren.get(fieldValue));
            }

            if (fieldValue instanceof List) {

                ListIterator listIterator = ((List) converted.get(fieldName)).listIterator();
                while (listIterator.hasNext()) {
                    String link = (String) listIterator.next();
                    listIterator.set(serializedChildren.get(link));
                }
            }
        }

        return converted;
    }

    private static ComponentTemplate deserializeComponent(Map<String, Object> obj) {

        String contentType = (String) obj.get("type");

        Map<String, Object> data = (Map<String, Object>) obj.get("data");

        ResourceType resourceType = ResourceType.fromContentType(contentType);
        Class<? extends ResourceState> descriptionClass = CompositeComponentRegistry
                .metaByType(resourceType.getName()).descriptionClass;

        Map<String, NestedState> children = deserializeChildren(data, descriptionClass);

        ComponentTemplate componentTemplate = objectMapper()
                .convertValue(obj, ComponentTemplate.class);

        componentTemplate.children = children;

        return componentTemplate;
    }

    private static NestedState deserializeTemplate(
            Map<String, Object> value, Class<? extends ServiceDocument> type) {

        Map<String, NestedState> children = deserializeChildren(value, type);

        ServiceDocument serviceDocument = objectMapper().convertValue(value, type);

        if (serviceDocument.documentSelfLink == null) {
            //set some documentSelfLink so that we can wire the objects together, we will remove it
            //before persisting
            serviceDocument.documentSelfLink = UUID.randomUUID().toString();
        }

        NestedState state = new NestedState();
        state.object = serviceDocument;
        state.children = children;

        return state;
    }

    private static Map<String, NestedState> deserializeChildren(Map<String, Object> value,
            Class<? extends ServiceDocument> type) {
        Map<String, NestedState> children = new HashMap<>();

        Iterator<Map.Entry<String, Object>> iterator = value.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();

            String fieldName = entry.getKey();
            Object nestedValue = entry.getValue();

            //handle just top level fields: String field = link and Collection<String> field = links
            Class<? extends ServiceDocument> childType = NestedState
                    .getNestedObjectType(type, fieldName);
            if (childType != null) {

                if (nestedValue instanceof Map) {
                    NestedState nestedState = deserializeTemplate(
                            (Map<String, Object>) nestedValue, childType);
                    children.put(nestedState.object.documentSelfLink, nestedState);
                    entry.setValue(nestedState.object.documentSelfLink);
                }

                if (nestedValue instanceof List) {
                    ListIterator listIterator = ((List) nestedValue).listIterator();

                    while (listIterator.hasNext()) {
                        Object next = listIterator.next();
                        NestedState nestedState = deserializeTemplate(
                                (Map<String, Object>) next, childType);
                        children.put(nestedState.object.documentSelfLink, nestedState);
                        listIterator.set(nestedState.object.documentSelfLink);
                    }
                }
            }
        }
        return children;
    }

}
