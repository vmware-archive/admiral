/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.image.service;

import java.util.Objects;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;

public class FavoriteImagesService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.FAVORITE_IMAGES;

    public FavoriteImagesService() {
        super(FavoriteImage.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    public static class FavoriteImage extends ServiceDocument {
        public static final String FIELD_NAME_NAME = "name";
        public static final String FIELD_NAME_DESCRIPTION = "description";

        public static final String FIELD_NAME_REGISTRY = "registry";
        public String name;
        public String description;

        public String registry;

        @Override
        public int hashCode() {
            return Objects.hash(name, description, registry);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof FavoriteImage)) {
                return false;
            }
            FavoriteImage i = (FavoriteImage) obj;
            return i.name.equals(this.name) &&
                    i.description.equals(this.description) &&
                    i.registry.equals(this.registry);
        }

    }
}
