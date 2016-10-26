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

package com.vmware.admiral;

import java.io.File;

import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.FileContentService;

public class RestrictiveFileContentService extends FileContentService {

    public RestrictiveFileContentService(File file) {
        super(file);
    }

    @Override
    public void handleGet(Operation op) {
        if (op != null && ConfigurationUtil.isEmbedded() && op.getRequestHeader(ConfigurationUtil.UI_PROXY_FORWARD_HEADER) == null) {
            Exception notFound = new ServiceHost.ServiceNotFoundException(op.getUri().toString());
            notFound.setStackTrace(new StackTraceElement[] {});
            op.setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON).fail(
                    Operation.STATUS_CODE_NOT_FOUND, notFound, null);
            return;
        }

        super.handleGet(op);
    }
}
