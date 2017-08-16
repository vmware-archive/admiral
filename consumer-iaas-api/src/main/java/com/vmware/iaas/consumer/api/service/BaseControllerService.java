/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.iaas.consumer.api.service;

import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.RequestRouter;
import com.vmware.xenon.common.StatelessService;

public abstract class BaseControllerService extends StatelessService {

    @Override
    public OperationProcessingChain getOperationProcessingChain() {
        if (super.getOperationProcessingChain() != null) {
            return super.getOperationProcessingChain();
        }

        OperationProcessingChain opProcessingChain = new OperationProcessingChain(this);

        RequestRouter requestRouter = createControllerRouting();
        opProcessingChain.add(requestRouter);
        setOperationProcessingChain(opProcessingChain);
        return opProcessingChain;
    }

    protected abstract RequestRouter createControllerRouting();

}
