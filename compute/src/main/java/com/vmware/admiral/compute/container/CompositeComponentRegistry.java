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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.kubernetes.service.BaseKubernetesState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.UriUtils;

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
        return getEntry(descriptionFactoryPrefix(descriptionLink)).componentMeta;
    }

    /**
     * Retrieve factory link for a Component by component's description link.
     */
    public static String descriptionFactoryLinkByDescriptionLink(String descriptionLink) {
        return getEntry(descriptionFactoryPrefix(descriptionLink)).descriptionFactoryLink;
    }

    /**
     * Retrieve factory link for a Component by component's description link.
     */
    public static String descriptionFactoryLinkByType(String type) {
        return getEntry(equalsType(type)).descriptionFactoryLink;
    }

    /**
     * Retrieve meta data for a Component description by component's state(instance) Link.
     */
    public static ComponentMeta metaByStateLink(String stateLink) {
        return getEntry(stateFactoryPrefix(stateLink)).componentMeta;
    }

    /**
     * Retrieve meta data for a Component description by component's state(instance) Link.
     */
    public static ComponentMeta metaByType(String type) {
        return getEntry(equalsType(type)).componentMeta;
    }

    /**
     * Retrieve factory link for a Component state by component's state(instance) Link.
     */
    public static String stateFactoryLinkByStateLink(String stateLink) {
        return getEntry(stateFactoryPrefix(stateLink)).stateFactoryLink;
    }

    public static String stateFactoryLinkByType(String type) {
        return getEntry(equalsType(type)).stateFactoryLink;
    }

    private static RegistryEntry emptyEntry = new RegistryEntry(null, null, null, null, null, null);

    private static RegistryEntry getEntry(Predicate<RegistryEntry> predicate) {
        for (RegistryEntry entry : entries) {
            if (predicate.test(entry)) {
                return entry;
            }
        }
        return emptyEntry;
    }

    private static Predicate<RegistryEntry> descriptionFactoryPrefix(String descriptionLink) {
        return r -> r.descriptionFactoryLink.equals(descriptionLink) ||
                UriUtils.isChildPath(descriptionLink, r.descriptionFactoryLink);
    }

    private static Predicate<RegistryEntry> stateFactoryPrefix(String stateLink) {
        return r -> r.stateFactoryLink.equals(stateLink) ||
                UriUtils.isChildPath(stateLink, r.stateFactoryLink);
    }

    private static Predicate<RegistryEntry> equalsType(String type) {
        return r -> type != null && type.equals(r.componentMeta.resourceType);
    }

    public static Iterator<Class<? extends ResourceState>> getClasses() {
        List<Class<? extends ResourceState>> r = entries.stream()
                .map(entry -> entry.componentMeta.stateClass).collect(Collectors.toList());
        return r.iterator();
    }

    public static Collection<Class<? extends ResourceState>> getKubernetesClasses() {
        Set<Class<? extends ResourceState>> result = entries.stream()
                .map(e -> e.componentMeta.stateClass)
                .filter(c -> BaseKubernetesState.class.isAssignableFrom(c))
                .collect(Collectors.toSet());
        return result;
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
