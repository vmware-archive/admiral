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

package com.vmware.admiral.log;

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;

/**
 * Describes a result of some asynchronous operations in the form of event log that usually cannot
 * be propagated to the UI by normal means but still need user attention.
 */
public class EventLogService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.EVENT_LOG;

    protected static final long EXPIRATION_INTERVAL_HOURS = Long.getLong(
            "com.vmware.admiral.log.eventlogservice.expiration.interval.hours",
            TimeUnit.HOURS.toMicros(72));

    public static class EventLogState extends MultiTenantDocument {

        public static final String FIELD_NAME_EVENT_LOG_TYPE = "eventLogType";

        public enum EventLogType {
            /**
             * Events that pass non-critical information to the administrator.
             */
            INFO,
            /**
             * Events that provide implication of potential problems; a warning indicates that the
             * system is not in an ideal state and that some further actions could result in an
             * error.
             */
            WARNING,
            /**
             * Events that indicate problems that are not system-critical and do not require
             * immediate attention.
             */
            ERROR
        }

        /**
         * The operation this event originates from. Example: Host config, Registry config, Create
         * container, Maintenance task.
         */
        @Documentation(description = "The operation this event originates from. Example: Host config, Registry config, Create container, Maintenance task.")
        public String resourceType;

        /** Severity level type. */
        @Documentation(description = "Severity level type.")
        public EventLogType eventLogType;

        /** User-friendly description of the event */
        @Documentation(description = "User-friendly description of the event")
        public String description;

        /** Additional data like operation request/response body, Request IP, etc. */
        @Documentation(description = "Additional data like operation request/response body, Request IP, etc.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Map<String, String> customProperties;
    }

    public EventLogService() {
        super(EventLogState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.DOCUMENT_OWNER, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation post) {
        if (!post.hasBody()) {
            post.fail(new IllegalArgumentException("empty body"));
            return;
        }
        try {
            EventLogState state = post.getBody(EventLogState.class);
            validateStateOnStart(state);
            state.documentExpirationTimeMicros = ServiceUtils
                    .getExpirationTimeFromNowInMicros(
                            EXPIRATION_INTERVAL_HOURS);
            post.setBody(state).complete();
        } catch (Throwable e) {
            logSevere(e);
            post.fail(e);
        }
    }

    @Override
    public void handlePut(Operation put) {
        Operation.failActionNotSupported(put);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }

    private void validateStateOnStart(EventLogState state) {
        assertNotNull(state.description, "description");
        assertNotNull(state.resourceType, "resourceType");
        assertNotNull(state.eventLogType, "eventLogType");
    }
}
