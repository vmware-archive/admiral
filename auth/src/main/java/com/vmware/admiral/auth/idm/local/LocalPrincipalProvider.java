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

package com.vmware.admiral.auth.idm.local;

import static com.vmware.admiral.common.util.AssertUtil.PROPERTY_CANNOT_BE_EMPTY_MESSAGE_FORMAT;
import static com.vmware.admiral.common.util.AssertUtil.PROPERTY_CANNOT_BE_NULL_MESSAGE_FORMAT;

import java.net.URI;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.vmware.admiral.auth.idm.PrincipalProvider;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.UserService;
import com.vmware.xenon.services.common.UserService.UserState;

public class LocalPrincipalProvider implements PrincipalProvider {
    private static final String FILTER_QUERY_KEY = "$filter";
    private static final String EMAIL_FILTER_QUERY_FORMAT = "email eq '*%s*'";

    private static final String PRINCIPAL_NOT_FOUND_MESSAGE_FORMAT = "Principal not found for "
            + "principal id: %s";
    private static final String PRINCIPALS_NOT_FOUND_FOR_CRITERIA_MESSAGE_FORMAT = "Principals not "
            + "found for criteria: %s";

    private ServiceHost host;

    public void setServiceHost(ServiceHost host) {
        this.host = host;
    }

    @Override
    public void getPrincipal(String principalId, BiConsumer<String, Throwable> callback) {
        if (!validateInput(principalId, "principalId", callback)) {
            return;
        }

        URI uri = UriUtils.buildUri(host, UserService.FACTORY_LINK);
        uri = UriUtils.extendUri(uri, principalId);
        Operation get = Operation.createGet(uri)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        if (Operation.STATUS_CODE_NOT_FOUND == o.getStatusCode()) {
                            String errorMessage = String.format(PRINCIPAL_NOT_FOUND_MESSAGE_FORMAT,
                                    principalId);
                            callback.accept(null, new IllegalStateException(errorMessage));
                            return;
                        }
                        callback.accept(null, ex);
                    } else {
                        UserState state = o.getBody(UserState.class);
                        callback.accept(state.email, null);
                    }
                });
        get.sendWith(host);
    }

    @Override
    public void getPrincipals(String criteria, BiConsumer<List<String>, Throwable> callback) {
        if (!validateInput(criteria, "criteria", callback)) {
            return;
        }

        String filterQuery = String.format(EMAIL_FILTER_QUERY_FORMAT, criteria);
        URI uri = UriUtils.buildUri(host, UserService.FACTORY_LINK);
        uri = UriUtils.extendUriWithQuery(uri, FILTER_QUERY_KEY, filterQuery);
        Operation get = Operation.createGet(uri)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        callback.accept(null, ex);
                        return;
                    }
                    ServiceDocumentQueryResult result =
                            o.getBody(ServiceDocumentQueryResult.class);
                    if (result.documentLinks == null || result.documentLinks.isEmpty()) {
                        String errorMessage = String.format(
                                PRINCIPALS_NOT_FOUND_FOR_CRITERIA_MESSAGE_FORMAT, criteria);
                        callback.accept(null, new IllegalStateException(errorMessage));
                        return;
                    }

                    // Deserialize the documents from Object to UserState and collect them in a List
                    List<String> principles = result.documents.values().stream()
                            .map(d -> Utils.fromJson(d, UserState.class))
                            .map(d -> d.email)
                            .collect(Collectors.toList());

                    callback.accept(principles, null);
                });
        get.sendWith(host);
    }

    @SuppressWarnings("unchecked")
    private boolean validateInput(String input, String propertyName,
            BiConsumer callback) {
        if (input == null) {
            callback.accept(null, new LocalizableValidationException(
                    String.format(PROPERTY_CANNOT_BE_NULL_MESSAGE_FORMAT, propertyName),
                    "common.assertion.property.required", propertyName));
            return false;
        }

        if (input.isEmpty()) {
            callback.accept(null, new LocalizableValidationException(
                    String.format(PROPERTY_CANNOT_BE_EMPTY_MESSAGE_FORMAT, propertyName),
                    "common.assertion.property.not.empty", propertyName));
            return false;
        }
        return true;
    }
}
