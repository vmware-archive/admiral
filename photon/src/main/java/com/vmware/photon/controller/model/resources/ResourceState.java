/*
 * Copyright (c) 2018-2019 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.services.common.ResourceGroupService;

/**
 * Base PODO for all photon model resource services.
 * <p/>
 * Services serving {@link ResourceState} descendants may want to use {@code copyTenantLinks} method
 * in handlePut to avoid loosing tenantLinks field.
 */
public class ResourceState extends ServiceDocument {

    public static final String FIELD_NAME_ID = "id";
    public static final String FIELD_NAME_NAME = "name";
    public static final String FIELD_NAME_DESC = "desc";
    public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";
    public static final String FIELD_NAME_TENANT_LINKS = "tenantLinks";
    public static final String FIELD_NAME_GROUP_LINKS = "groupLinks";
    public static final String FIELD_NAME_TAG_LINKS = "tagLinks";
    public static final String FIELD_NAME_ENDPOINT_LINK = PhotonModelConstants.FIELD_NAME_ENDPOINT_LINK;
    public static final String FIELD_NAME_REGION_ID = "regionId";

    /**
     * Identifier of this resource instance
     */
    @UsageOption(option = PropertyUsageOption.ID)
    @UsageOption(option = PropertyUsageOption.REQUIRED)
    @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
    public String id;

    /**
     * Name of the resource instance
     */
    @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
    @PropertyOptions(indexing = { PropertyIndexingOption.CASE_INSENSITIVE,
            PropertyIndexingOption.SORT })
    public String name;

    /**
     * Description of the resource instance
     */
    @PropertyOptions(usage = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL, indexing = {
            PropertyIndexingOption.CASE_INSENSITIVE,
            PropertyIndexingOption.SORT
            })
    @Since(ReleaseConstants.RELEASE_VERSION_0_6_12)
    public String desc;

    /**
     * Custom property bag that can be used to store resource specific properties.
     */
    @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
    @PropertyOptions(indexing = { PropertyIndexingOption.CASE_INSENSITIVE,
            PropertyIndexingOption.EXPAND,
            PropertyIndexingOption.FIXED_ITEM_NAME, PropertyIndexingOption.SORT })
    public Map<String, String> customProperties;

    /**
     * A list of tenant links that can access this resource.
     */
    @UsageOption(option = PropertyUsageOption.LINKS)
    public List<String> tenantLinks;

    /**
     * Set of groups the resource belongs to
     *
     * @see ResourceGroupService
     */
    @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
    @UsageOption(option = PropertyUsageOption.LINKS)
    public Set<String> groupLinks;

    /**
     * Set of tags set on this resource.
     *
     * @see TagService
     */
    @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
    @UsageOption(option = PropertyUsageOption.LINKS)
    public Set<String> tagLinks;

    /**
     * Resource creation time in micros since epoch.
     */
    @UsageOption(option = PropertyUsageOption.OPTIONAL)
    @Since(ReleaseConstants.RELEASE_VERSION_0_6_17)
    @PropertyOptions(indexing = { PropertyIndexingOption.SORT })
    public Long creationTimeMicros;

    /**
     * Identifier of the region associated with this resource instance.
     */
    @UsageOption(option = PropertyUsageOption.OPTIONAL)
    @Since(ReleaseConstants.RELEASE_VERSION_0_6_17)
    @PropertyOptions(indexing = { PropertyIndexingOption.SORT })
    public String regionId;

    public void copyTo(ResourceState target) {
        super.copyTo(target);

        target.id = this.id;
        target.name = this.name;
        target.customProperties = this.customProperties;
        target.tenantLinks = this.tenantLinks;
        target.groupLinks = this.groupLinks;
        target.tagLinks = this.tagLinks;
        target.creationTimeMicros = this.creationTimeMicros;
        target.regionId = this.regionId;
    }

    /**
     * Utility method to keep the tenant links when overwriting the whole resource state document
     * using PUT operation. In case currentState is null or does not have tenantLinks this method
     * does nothing.
     *
     * @param currentState
     *         current resource state to get the tenant links from
     *
     * @see ResourceState#copyTenantLinks
     */
    public void copyTenantLinks(ResourceState currentState) {
        ResourceState.copyTenantLinks(currentState, this);
    }

    /**
     * Utility method to keep the tenant links when overwriting the whole resource state document
     * using PUT operation. In case currentState is null or does not have tenantLinks this method
     * does nothing.
     *
     * @param currentState
     *         existing state with tenant links
     * @param newState
     *         new state overwriting the existing one
     */
    public static void copyTenantLinks(ResourceState currentState, ResourceState newState) {
        if (newState == null) {
            throw new IllegalArgumentException("new state cannot be null");
        }
        if (currentState == null || currentState.tenantLinks == null
                || currentState.tenantLinks.isEmpty()) {
            return;
        }
        if (newState.tenantLinks == null || newState.tenantLinks.isEmpty()) {
            newState.tenantLinks = new ArrayList<>(currentState.tenantLinks);
        }
    }

}
