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

package com.vmware.admiral.compute.kubernetes.service;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.service.common.AbstractSecuredFactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

/**
 * Factory service implementing {@link AbstractSecuredFactoryService} used to create instances of
 * {@link GenericKubernetesEntityService}.
 */
public class GenericKubernetesEntityFactoryService extends AbstractSecuredFactoryService {
    public static final String SELF_LINK = ManagementUriParts.KUBERNETES_GENERIC_ENTITIES;

    public GenericKubernetesEntityFactoryService() {
        super(GenericKubernetesEntityService.GenericKubernetesEntityState.class);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new GenericKubernetesEntityService();
    }

    @Override
    public void handleGet(Operation get) {
        OperationUtil.transformProjectHeaderToFilterQuery(get);
        super.handleGet(get);
    }
}