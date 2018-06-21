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

package com.vmware.photon.controller.model;

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;

public class ServiceUtils {

    public static final int SERVICE_DOCUMENT_VERSION_RETENTION_LIMIT = Integer
            .getInteger("service.document.version.retention.limit",
                    ServiceDocumentDescription.DEFAULT_VERSION_RETENTION_LIMIT);
    public static final int SERVICE_DOCUMENT_VERSION_RETENTION_FLOOR = Integer
            .getInteger("service.document.version.retention.floor",
                    ServiceDocumentDescription.DEFAULT_VERSION_RETENTION_FLOOR);

    public static void setRetentionLimit(ServiceDocument template) {
        template.documentDescription.versionRetentionLimit = SERVICE_DOCUMENT_VERSION_RETENTION_LIMIT;
        template.documentDescription.versionRetentionFloor = SERVICE_DOCUMENT_VERSION_RETENTION_FLOOR;
    }
}
