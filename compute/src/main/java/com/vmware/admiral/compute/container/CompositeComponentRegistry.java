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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.photon.controller.model.resources.ResourceState;

/**
 * This class acts as simple(not thread safe) registry of meta data related to Components supported
 * by {@link CompositeDescription}. The recommended way to register a component meta data is during
 * boot time of the host, as this class is not thread safe.
 */
public class CompositeComponentRegistry {

    private static final List<RegistryEntry> entries = new ArrayList<>();

    private CompositeComponentRegistry() {
    }

    /**
     * Register new component meta data.
     */
    public static void registerComponent(String resourceType, String descriptionFactoryLink,
            Class<? extends ResourceState> descriptionClass, String stateFactoryLink,
            Class<? extends ResourceState> stateClass) {
        registerComponent(resourceType, descriptionFactoryLink, descriptionClass, stateFactoryLink,
                stateClass, stateClass);
    }

    /**
     * Register new component meta data.
     */
    public static void registerComponent(String resourceType, String descriptionFactoryLink,
            Class<? extends ResourceState> descriptionClass, String stateFactoryLink,
            Class<? extends ResourceState> stateClass,
            Class<? extends ResourceState> stateTemplateClass) {
        entries.add(new RegistryEntry(resourceType, descriptionFactoryLink, descriptionClass,
                stateFactoryLink, stateClass, stateTemplateClass));
    }

    /**
     * Retrieve meta data for a Component by component's description link.
     */
    public static ComponentMeta metaByDescriptionLink(String descriptionLink) {
        if (null == descriptionLink) {
            return null;
        }
        List<RegistryEntry> list = entries.stream()
                .filter(m -> descriptionLink.startsWith(m.descriptionFactoryLink))
                .collect(Collectors.toList());
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0).componentMeta;
    }

    /**
     * Retrieve factory link for a Component by component's description link.
     */
    public static String factoryLinkByDescriptionLink(String descriptionLink) {
        if (null == descriptionLink) {
            return null;
        }
        List<RegistryEntry> list = entries.stream()
                .filter(m -> descriptionLink.startsWith(m.descriptionFactoryLink))
                .collect(Collectors.toList());
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0).descriptionFactoryLink;
    }

    /**
     * Retrieve factory link for a Component by component's description link.
     */
    public static String factoryLinkByType(String type) {
        if (null == type) {
            return null;
        }
        List<RegistryEntry> list = entries.stream()
                .filter(m -> type.equals(m.componentMeta.resourceType))
                .collect(Collectors.toList());
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0).descriptionFactoryLink;
    }

    /**
     * Retrieve meta data for a Component description by component's state(instance) Link.
     */
    public static ComponentMeta metaByStateLink(String stateLink) {
        if (null == stateLink) {
            return null;
        }
        List<RegistryEntry> list = entries.stream()
                .filter(m -> stateLink.startsWith(m.stateFactoryLink))
                .collect(Collectors.toList());
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0).componentMeta;
    }

    /**
     * Retrieve meta data for a Component description by component's state(instance) Link.
     */
    public static ComponentMeta metaByType(String type) {
        if (null == type) {
            return null;
        }
        List<RegistryEntry> list = entries.stream()
                .filter(m -> type.startsWith(m.componentMeta.resourceType))
                .collect(Collectors.toList());
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0).componentMeta;
    }

    /**
     * Retrieve factory link for a Component state by component's state(instance) Link.
     */
    public static String factoryLinkByStateLink(String stateLink) {
        if (null == stateLink) {
            return null;
        }
        List<RegistryEntry> list = entries.stream()
                .filter(m -> stateLink.startsWith(m.stateFactoryLink))
                .collect(Collectors.toList());
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0).stateFactoryLink;
    }

    public static class ComponentMeta {
        public final Class<? extends ResourceState> descriptionClass;
        public final Class<? extends ResourceState> stateClass;
        public final String resourceType;
        public Class<? extends ResourceState> stateTemplateClass;

        private ComponentMeta(String resourceType, Class<? extends ResourceState> descriptionClass,
                Class<? extends ResourceState> stateClass,
                Class<? extends ResourceState> stateTemplateClass) {
            this.resourceType = resourceType;
            this.descriptionClass = descriptionClass;
            this.stateClass = stateClass;
            this.stateTemplateClass = stateTemplateClass;
        }
    }

    private static class RegistryEntry {

        private final String descriptionFactoryLink;
        private final String stateFactoryLink;
        private final ComponentMeta componentMeta;

        public RegistryEntry(String resourceType, String descriptionFactoryLink,
                Class<? extends ResourceState> descriptionClass, String stateFactoryLink,
                Class<? extends ResourceState> stateClass,
                Class<? extends ResourceState> stateTemplateClass) {
            this.descriptionFactoryLink = descriptionFactoryLink;
            this.stateFactoryLink = stateFactoryLink;
            this.componentMeta = new ComponentMeta(resourceType, descriptionClass, stateClass,
                    stateTemplateClass);
        }
    }
}
