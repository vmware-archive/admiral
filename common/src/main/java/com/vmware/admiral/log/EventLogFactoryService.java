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

package com.vmware.admiral.log;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.log.EventLogService.EventLogState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

public class EventLogFactoryService extends FactoryService {
    public static final String SELF_LINK = ManagementUriParts.EVENT_LOG;

    public EventLogFactoryService() {
        super(EventLogState.class);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new EventLogService();
    }

    @Override
    public void handleGet(Operation get) {
        OperationUtil.transformProjectHeaderToFilterQuery(get);
        super.handleGet(get);
    }
}
