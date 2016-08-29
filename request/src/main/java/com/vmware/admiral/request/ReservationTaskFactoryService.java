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

package com.vmware.admiral.request;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;

/**
 * Factory service implementing {@link FactoryService} used to create instances of
 * {@link ReservationTaskService}.
 */
public class ReservationTaskFactoryService extends FactoryService {
    public static final String SELF_LINK = ManagementUriParts.REQUEST_RESERVATION_TASKS;

    public ReservationTaskFactoryService() {
        super(ReservationTaskService.ReservationTaskState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new ReservationTaskService();
    }
}
