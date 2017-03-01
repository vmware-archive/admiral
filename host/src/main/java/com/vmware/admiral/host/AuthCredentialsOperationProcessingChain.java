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

package com.vmware.admiral.host;

import java.util.function.Predicate;

import com.vmware.admiral.common.security.EncryptionUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.service.common.AuthBootstrapService;
import com.vmware.admiral.service.common.AuthBootstrapService.CredentialsScope;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationProcessingChain;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * This processing chain:
 * - Prevents deletion of a {@link AuthCredentialsServiceState} if its in use by a
 * {@link ComputeState}.
 * - Encrypts the private key field of a {@link AuthCredentialsServiceState} when needed if the
 * encryption is enabled.
 */
public class AuthCredentialsOperationProcessingChain extends OperationProcessingChain {

    public static final String CREDENTIALS_IN_USE_MESSAGE = "Credentials are in use";
    public static final String CREDENTIALS_IN_USE_MESSAGE_CODE = "host.credentials.in.use";

    public AuthCredentialsOperationProcessingChain(FactoryService service) {
        super(service);
        this.add(new Predicate<Operation>() {
            @Override
            public boolean test(Operation op) {
                if (op.getAction() == Action.POST) {
                    return handlePatchPostPut(service, op);
                }
                return true;
            }
        });
    }

    public AuthCredentialsOperationProcessingChain(AuthCredentialsService service) {
        super(service);
        this.add(new Predicate<Operation>() {
            @Override
            public boolean test(Operation op) {
                switch (op.getAction()) {
                case DELETE:
                    return handleDelete(service, op, this);
                case PATCH:
                case POST:
                case PUT:
                    return handlePatchPostPut(service, op);
                default:
                    return true;
                }
            }
        });
    }

    private boolean handlePatchPostPut(Service service, Operation op) {
        AuthCredentialsServiceState body = op.getBody(AuthCredentialsServiceState.class);

        // Credentials with SYSTEM scope need the password in plain text or they can't be used to
        // login into Admiral!
        boolean isSystemScope = (body.customProperties != null)
                && CredentialsScope.SYSTEM.toString().equals(
                        body.customProperties.get(AuthBootstrapService.PROPERTY_SCOPE));

        if (!isSystemScope) {
            body.privateKey = EncryptionUtils.encrypt(body.privateKey);
            op.setBodyNoCloning(body);
        }
        return true;
    }

    private boolean handleDelete(AuthCredentialsService service, Operation op,
            Predicate<Operation> invokingFilter) {
        service.sendRequest(Operation.createPost(service, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(QueryUtil.addCountOption(QueryUtil.buildPropertyQuery(ComputeState.class,
                        QuerySpecification.buildCompositeFieldName(
                                ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                                ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME),
                        service.getSelfLink())))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        service.logWarning(Utils.toString(e));
                        op.fail(e);
                    }
                    ServiceDocumentQueryResult result = o.getBody(QueryTask.class).results;
                    if (result.documentCount != 0) {
                        op.fail(new LocalizableValidationException(CREDENTIALS_IN_USE_MESSAGE,
                                CREDENTIALS_IN_USE_MESSAGE_CODE));
                    }
                    resumeProcessingRequest(op, invokingFilter);
                }));
        return false;
    }

}
