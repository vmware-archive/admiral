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

package com.vmware.admiral.service.common;

import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.StatefulService;

/**
 * LogService is log management service which maintains the logs of a container.
 */
public class LogService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.LOGS;

    protected static final long DEFAULT_EXPIRATION_MICROS = Long.getLong(
            "com.vmware.admiral.service.common.expiration.micros", TimeUnit.MINUTES.toMicros(5));

    public static class LogServiceState extends com.vmware.admiral.service.common.MultiTenantDocument {

        public static final String FIELD_NAME_LOGS = "logs";

        /** Stream of container log data */
        @Documentation(description = "Stream of container log data.")
        @PropertyOptions(indexing = {
                PropertyIndexingOption.STORE_ONLY,
                PropertyIndexingOption.EXCLUDE_FROM_SIGNATURE })
        public byte[] logs;
    }

    public LogService() {
        super(LogServiceState.class);
        super.toggleOption(Service.ServiceOption.PERSISTENCE, true);
        super.toggleOption(Service.ServiceOption.DOCUMENT_OWNER, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation startPost) {
        if (!checkForBody(startPost)) {
            return;
        }

        // Set the expiration time to be 15 minutes by default.
        LogServiceState state = startPost.getBody(LogServiceState.class);
        state.documentExpirationTimeMicros = ServiceUtils
                .getExpirationTimeFromNowInMicros(
                DEFAULT_EXPIRATION_MICROS);
        startPost.complete();
    }

    @Override
    public void handlePut(Operation put) {
        if (!checkForBody(put)) {
            return;
        }

        LogServiceState currentState = getState(put);
        LogServiceState newState = put.getBody(LogServiceState.class);

        if (newState.logs != null) {
            currentState.logs = newState.logs;
        }
        // workaround for NullPointerException in xenon when the service has no REPLICATION option
        if (currentState.documentEpoch == null) {
            currentState.documentEpoch = 0L;
        }
        setState(put, currentState);
        put.setBody(currentState).complete();
    }

    /**
     * Provides a default instance of the service state and allows service author to specify
     * indexing and usage options, per service document property
     */
    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);

        // resource reference prop:
        ServiceDocumentDescription.PropertyDescription pd = template.documentDescription
                .propertyDescriptions.get(LogServiceState.FIELD_NAME_LOGS);
        pd.indexingOptions = EnumSet.of(PropertyIndexingOption.STORE_ONLY,
                PropertyIndexingOption.EXCLUDE_FROM_SIGNATURE);

        // logs can be big, need to increase the default size limit
        template.documentDescription.serializedStateSizeLimit = 4 * 1024 * 1024; // 4MB

        return template;
    }
}
