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

package com.vmware.photon.controller.model.adapterapi;

import java.util.List;
import java.util.Map;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.xenon.common.ServiceDocument.PropertyOptions;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;

/**
 * Request to validate and/or enhance an Endpoint. The {@link ResourceRequest#resourceReference}
 * field is reference to the Endpoint to enhance.
 * <p>
 * If the value of {@link EndpointConfigRequest#requestType} is {@code RequestType#VALIDATE} then
 * value of {@link ResourceRequest#resourceReference} won't be set.
 */
public class EndpointConfigRequest extends ResourceRequest {

    public static final String USER_LINK_KEY = "userLink";
    public static final String USER_EMAIL_KEY = "userEmail";
    public static final String PRIVATE_KEY_KEY = "privateKey";
    public static final String PRIVATE_KEYID_KEY = "privateKeyId";
    public static final String PUBLIC_KEY_KEY = "publicKey";
    public static final String TOKEN_REFERENCE_KEY = "tokenReference";
    public static final String REGION_KEY = "regionId";
    public static final String ZONE_KEY = "zoneId";
    /**
     * A key for the property of {@link #endpointProperties} which specifies trusted certificate
     * for the endpoint
     */
    public static final String CERTIFICATE_PROP_NAME = "certificate";

    /**
     * A key for the property of {@link #endpointProperties} which specifies whether to accept or
     * not the certificate (if self-signed) for the endpoint
     */
    public static final String ACCEPT_SELFSIGNED_CERTIFICATE = "acceptSelfSignedCertificate";

    /**
     * Set this property to true if the end-point support public/global images enumeration.
     */
    public static final String SUPPORT_PUBLIC_IMAGES = "supportPublicImages";

    /**
     * Set this property to true if the end-point supports explicit datastores concept.
     */
    public static final String SUPPORT_DATASTORES = "supportDatastores";

    /**
     * Endpoint request type.
     */
    public enum RequestType {
        VALIDATE,
        ENHANCE
    }

    /**
     * Request type.
     */
    public RequestType requestType;

    /**
     * A map of value to use to validate and enhance Endpoint.
     */
    public Map<String, String> endpointProperties;

    /**
     * A list of tenant links which can access this service.
     */
    @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND })
    @Since(ReleaseConstants.RELEASE_VERSION_0_6_12)
    public List<String> tenantLinks;
}
