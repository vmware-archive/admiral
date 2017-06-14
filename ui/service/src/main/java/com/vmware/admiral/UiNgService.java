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

package com.vmware.admiral;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

// Temporary service until new UI is moved
public class UiNgService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.UI_NG_SERVICE;

    @Override
    public void handleGet(Operation get) {
        get.addResponseHeader(Operation.LOCATION_HEADER, "../");
        get.setStatusCode(Operation.STATUS_CODE_MOVED_TEMP);
        get.complete();
    }

    @Override
    public void authorizeRequest(Operation op) {
        // No authorization required.
        op.complete();
    }
}
