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

package com.vmware.photon.controller.model.resources;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

import static com.vmware.xenon.common.UriUtils.buildUriPath;

import java.util.List;
import java.util.Map;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import io.netty.util.internal.StringUtil;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.TenantService;

/**
 * Represents an image.
 */
public class ImageService extends StatefulService {

    public static final String FACTORY_LINK = UriPaths.RESOURCES_IMAGES;

    /**
     * Represents the state of an image.
     */
    public static class ImageState extends ResourceState {

        public static final String FIELD_NAME_DESCRIPTION = "description";
        public static final String FIELD_NAME_OS_FAMILY = "osFamily";
        public static final String FIELD_NAME_ENDPOINT_TYPE = "endpointType";

        /**
         * Represents the properties of a data disk.
         */
        public static class DiskConfiguration {
            /**
             * Identifier of the disk.
             */
            public String id;

            /**
             * Size of the disk in Mega Bytes.
             */
            public Integer capacityMBytes;

            /**
             * Persistence capability of the disk across reboots.
             */
            public Boolean persistent;

            /**
             * Encryption status of the disk.
             */
            public Boolean encrypted;

            /**
             * Map to capture endpoint specific disk properties.
             */
            public Map<String,String> properties;
        }

        /**
         * Captures the properties of each disk specified in the image.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_17)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public List<DiskConfiguration> diskConfigs;

        /**
         * User-friendly description of the image.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(indexing = PropertyIndexingOption.CASE_INSENSITIVE)
        public String description;

        /**
         * The OS family of the image. The value, if provided, might be 'Linux', 'Windows', etc.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @PropertyOptions(indexing = PropertyIndexingOption.CASE_INSENSITIVE)
        public String osFamily;

        /**
         * Optional link to the {@code EndpointState} the image belongs to. Leave blank to indicate
         * the image is public/global for all end-points of the same type. Either this property or
         * {@code #endpointType} property should be set.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        @UsageOption(option = PropertyUsageOption.LINK)
        public String endpointLink;

        /**
         * Optional type of the end-points the image is publicly/globally available. Leave blank to
         * indicate the image is private/specific for this end-point. Either this property or
         * {@code #endpointLink} property should be set.
         */
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String endpointType;

        /**
         * Non-empty {@code #endpointType} indicates Public image.
         */
        public final boolean isPublicImage() {
            return !StringUtil.isNullOrEmpty(this.endpointType);
        }

        /**
         * Non-empty {@code #endpointLink} indicates Private image.
         */
        public final boolean isPrivateImage() {
            return !StringUtil.isNullOrEmpty(this.endpointLink);
        }
    }

    public ImageService() {
        super(ImageState.class);

        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);

        super.toggleOption(ServiceOption.ON_DEMAND_LOAD, true);
    }

    @Override
    public void handleCreate(Operation createOp) {
        if (checkForValid(createOp)) {
            super.handleCreate(createOp);
        }
    }

    @Override
    public void handlePut(Operation putOp) {
        if (checkForValid(putOp)) {
            super.handlePut(putOp);
        }
    }

    @Override
    public void handlePatch(Operation patchOp) {
        ResourceUtils.handlePatch(
                patchOp, getState(patchOp), getStateDescription(), ImageState.class, null);
    }

    /**
     * Common validation login.
     */
    private boolean checkForValid(Operation op) {
        if (checkForBody(op)) {
            try {
                ImageState imageState = op.getBody(ImageState.class);

                Utils.validateState(getStateDescription(), imageState);

                if (!imageState.isPrivateImage() && !imageState.isPublicImage()) {
                    throw new IllegalArgumentException(
                            "Either " + ImageState.class.getSimpleName()
                                    + "." + ImageState.FIELD_NAME_ENDPOINT_TYPE +
                                    " or " + ImageState.class.getSimpleName()
                                    + "." + ImageState.FIELD_NAME_ENDPOINT_LINK +
                                    " must be set.");
                }
                if (imageState.isPrivateImage() && imageState.isPublicImage()) {
                    throw new IllegalArgumentException(
                            "Both " + ImageState.class.getSimpleName()
                                    + "." + ImageState.FIELD_NAME_ENDPOINT_TYPE +
                                    " and " + ImageState.class.getSimpleName()
                                    + "." + ImageState.FIELD_NAME_ENDPOINT_LINK +
                                    " cannot be set.");
                }

                return true;

            } catch (Throwable t) {
                op.fail(t);
                return false;
            }
        }
        return false;
    }

    @Override
    public ImageState getDocumentTemplate() {

        ImageState image = (ImageState) super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(image);

        image.id = "endpoint-specific-image-id";
        image.name = "endpoint-specific-image-name";
        image.description = "user-friendly-image-description";
        image.osFamily = "Linux";
        image.regionId = "endpoint-specific-image-region-id";

        image.endpointLink = buildUriPath(EndpointService.FACTORY_LINK, "the-A-cloud");
        image.groupLinks = singleton(
                buildUriPath(ResourceGroupService.FACTORY_LINK, "the-A-folder"));
        image.tenantLinks = singletonList(buildUriPath(TenantService.FACTORY_LINK, "the-A-tenant"));

        return image;
    }
}
