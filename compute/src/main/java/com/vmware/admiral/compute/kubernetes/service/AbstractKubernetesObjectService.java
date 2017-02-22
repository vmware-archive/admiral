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

package com.vmware.admiral.compute.kubernetes.service;

import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;

public abstract class AbstractKubernetesObjectService<T extends BaseKubernetesState>
        extends StatefulService {

    private Class<T> stateType;

    protected AbstractKubernetesObjectService(Class<T> stateType) {
        super(stateType);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        this.stateType = stateType;
    }

    @Override
    public void handleCreate(Operation post) {
        if (!checkForBody(post)) {
            return;
        }

        T state = post.getBody(stateType);

        try {
            post.setBody(state);
            post.complete();
        } catch (Throwable e) {
            logSevere(e);
            post.fail(e);
        }
    }

    @Override
    public void handlePut(Operation put) {
        if (!checkForBody(put)) {
            return;
        }

        T putBody = put.getBody(stateType);

        this.setState(put, putBody);
        put.setBody(putBody);
        put.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        T currentState = getState(patch);
        T patchState = patch.getBody(stateType);

        PropertyUtils.mergeServiceDocuments(currentState, patchState);
        patch.complete();
    }
}
