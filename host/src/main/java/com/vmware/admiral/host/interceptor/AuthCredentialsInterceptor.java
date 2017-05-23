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

package com.vmware.admiral.host.interceptor;

import java.util.logging.Level;

import com.vmware.admiral.auth.idm.AuthConfigProvider;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
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
public class AuthCredentialsInterceptor {

    public static final String CREDENTIALS_IN_USE_MESSAGE = "Credentials are in use";
    public static final String CREDENTIALS_IN_USE_MESSAGE_CODE = "host.credentials.in.use";

    public static void register(OperationInterceptorRegistry registry) {
        registry.addFactoryServiceInterceptor(
                AuthCredentialsService.class, Action.POST, AuthCredentialsInterceptor::handlePatchPostPut);

        registry.addServiceInterceptor(
                AuthCredentialsService.class, Action.POST, AuthCredentialsInterceptor::handlePatchPostPut);
        registry.addServiceInterceptor(
                AuthCredentialsService.class, Action.PUT, AuthCredentialsInterceptor::handlePatchPostPut);
        registry.addServiceInterceptor(
                AuthCredentialsService.class, Action.PATCH, AuthCredentialsInterceptor::handlePatchPostPut);
        registry.addServiceInterceptor(
                AuthCredentialsService.class, Action.DELETE, AuthCredentialsInterceptor::handleDelete);
    }

    public static DeferredResult<Void> handlePatchPostPut(Service service, Operation op) {
        AuthCredentialsServiceState body = op.getBody(AuthCredentialsServiceState.class);

        // Credentials with SYSTEM scope need the password in plain text or they can't be used to
        // login into Admiral!
        boolean isSystemScope = (body.customProperties != null)
                && AuthConfigProvider.CredentialsScope.SYSTEM.toString().equals(
                        body.customProperties.get(AuthConfigProvider.PROPERTY_SCOPE));

        if (!isSystemScope) {
            body.privateKey = EncryptionUtils.encrypt(body.privateKey);
            op.setBodyNoCloning(body);
        }
        return null;
    }

    public static DeferredResult<Void> handleDelete(Service service, Operation op) {
        DeferredResult<Void> dr = new DeferredResult<>();
        service.sendRequest(Operation.createPost(service, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(QueryUtil.addCountOption(QueryUtil.buildPropertyQuery(ComputeState.class,
                        QuerySpecification.buildCompositeFieldName(
                                ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                                ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME),
                        service.getSelfLink())))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        service.getHost().log(Level.WARNING, Utils.toString(e));
                        dr.fail(e);
                    }
                    ServiceDocumentQueryResult result = o.getBody(QueryTask.class).results;
                    if (result.documentCount != 0) {
                        op.fail(new LocalizableValidationException(CREDENTIALS_IN_USE_MESSAGE,
                                CREDENTIALS_IN_USE_MESSAGE_CODE));
                    }
                    dr.complete(null);
                }));
        return dr;
    }

}
