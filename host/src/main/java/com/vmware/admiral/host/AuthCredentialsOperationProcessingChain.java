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

package com.vmware.admiral.host;

import java.util.function.Predicate;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Prevent deletion of {@link AuthCredentialsServiceState} if its in use by a {@link ComputeState}
 */
class AuthCredentialsOperationProcessingChain extends OperationProcessingChain {

    public AuthCredentialsOperationProcessingChain(AuthCredentialsService service) {
        super(service);
        this.add(new Predicate<Operation>() {

            @Override
            public boolean test(Operation op) {
                if (op.getAction() != Action.DELETE) {
                    return true;
                }

                service.sendRequest(Operation.createPost(service, ServiceUriPaths.CORE_QUERY_TASKS)
                        .setBody(QueryUtil.addCountOption(QueryUtil.buildPropertyQuery(ComputeState.class,
                                QuerySpecification.buildCompositeFieldName(
                                        ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                                        ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME), service.getSelfLink())))
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                service.logWarning(Utils.toString(e));
                                op.fail(e);
                            }
                            ServiceDocumentQueryResult result = o.getBody(QueryTask.class).results;
                            if (result.documentCount != 0) {
                                op.fail(new LocalizableValidationException("Auth Credentials are in use", "host.credentials.in.use"));
                            }
                            resumeProcessingRequest(op, this);
                        }));

                return false;
            }
        });
    }

}
