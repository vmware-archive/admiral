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

package com.vmware.admiral.adapter.kubernetes.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.Test;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.xenon.common.Operation;

public class AbstractKubernetesAdapterServiceTest extends AbstractKubernetesAdapterService {

    @Test
    public void testHandleExceptions() {
        Operation op = mock(Operation.class);
        AdapterRequest request = mock(AdapterRequest.class);
        RuntimeException e = new RuntimeException();

        handleExceptions(request, op, () -> {
            throw e;
        });

        verify(op).fail(e);
        verifyNoMoreInteractions(op);
    }
}
