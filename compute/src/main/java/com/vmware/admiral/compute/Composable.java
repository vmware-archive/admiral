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

package com.vmware.admiral.compute;

import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.photon.controller.model.resources.ResourceState;

/**
 * Interface to be implemented by resources that support composing them in
 * {@link CompositeComponent}
 */
public interface Composable {

    /**
     * Returns the link of the {@link CompositeComponent}, which this resource is part of.
     */
    String retrieveCompositeComponentLink();

    /**
     * A static helper method to extract {@link CompositeComponent} link from
     * {@link ResourceState#customProperties}, store under
     * {@link ComputeConstants#FIELD_NAME_COMPOSITE_COMPONENT_LINK_KEY}.
     *
     * Returns {@code null} if the key is missing.
     */
    static String retrieveCompositeComponentLink(ResourceState resource) {
        if (resource.customProperties != null) {
            return resource.customProperties
                    .get(ComputeConstants.FIELD_NAME_COMPOSITE_COMPONENT_LINK_KEY);
        }
        return null;
    }
}
