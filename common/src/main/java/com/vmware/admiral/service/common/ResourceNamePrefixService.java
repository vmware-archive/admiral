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

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.admiral.service.common.ResourceNamePrefixService.ResourceNamePrefixState.ADD_RANDOM_GENERATED_TOKEN;
import static com.vmware.admiral.service.common.ResourceNamePrefixService.ResourceNamePrefixState.DEFAULT_NEXT_NUMBER;
import static com.vmware.admiral.service.common.ResourceNamePrefixService.ResourceNamePrefixState.DEFAULT_NUMBER_OF_DIGITS;
import static com.vmware.admiral.service.common.ResourceNamePrefixService.ResourceNamePrefixState.MAX_NUMBER_OF_DIGITS;
import static com.vmware.admiral.service.common.ResourceNamePrefixService.ResourceNamePrefixState.MAX_PREFIX_LENGTH;
import static com.vmware.admiral.service.common.ResourceNamePrefixService.ResourceNamePrefixState.RANDOM_GENERATED_TOKEN_DELIMITER;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Random;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;

/**
 * Service for generating resource prefixes that are used to create names for provisioned resources.
 * The prefixes will be associated based on the <code>group</code> property (or considered global).
 *
 * A prefix is a base name to be followed by a counter of a specified number of digits. When the
 * digits have all been used the service rolls back to the first number (which potentially might
 * cause issue if the resources are still not removed).
 */
public class ResourceNamePrefixService extends StatefulService {
    public static final String FACTORY_LINK = ManagementUriParts.RESOURCE_NAME_PREFIXES;
    public static final String DEFAULT_RESOURCE_NAME_PREFIX_ID = "system-default";
    public static final String DEFAULT_RESOURCE_NAME_PREFIX_SELF_LINK = UriUtils.buildUriPath(
            FACTORY_LINK, DEFAULT_RESOURCE_NAME_PREFIX_ID);
    private static final String DEFAULT_NAME_PREFIX = "mcm";
    private static final Boolean DEFAULT_ADD_RANDOM_TOKEN = Boolean.TRUE;
    private static final long SINCE_TIME = new GregorianCalendar(2016, Calendar.JANUARY, 1)
            .getTime().getTime();

    static ServiceDocument buildDefaultStateInstance() {
        ResourceNamePrefixState state = new ResourceNamePrefixState();
        state.documentSelfLink = DEFAULT_RESOURCE_NAME_PREFIX_SELF_LINK;
        state.prefix = DEFAULT_NAME_PREFIX;
        state.numberOfDigits = ResourceNamePrefixState.DEFAULT_NUMBER_OF_DIGITS;
        state.addRandomToken = DEFAULT_ADD_RANDOM_TOKEN;
        Random r = new Random();
        int low = 1;
        int high = 999;
        int randomStartNumber = r.nextInt(high - low) + low;
        state.nextNumber = randomStartNumber;

        return state;
    }

    public static class ResourceNamePrefixState extends MultiTenantDocument {

        public static final boolean DEFAULT_PREFIX_FLAG = Boolean
                .getBoolean("dcp.management.name.prefix.flag");
        public static final String PREFIX_DELIMITER = System.getProperty(
                "dcp.management.name.prefix.delimiter", "-");
        public static final long MAX_PREFIX_LENGTH = Long.getLong(
                "dcp.management.name.prefix.max.lenght", 7);
        public static final long MAX_NUMBER_OF_DIGITS = Long.getLong(
                "dcp.management.name.prefix.max.number.digits", 9);
        public static final long DEFAULT_NUMBER_OF_DIGITS = Long.getLong(
                "dcp.management.name.prefix.default.number.digits", 7);
        public static final long DEFAULT_NEXT_NUMBER = Long.getLong(
                "dcp.management.name.prefix.default.next.number", 1);
        public static final boolean ADD_RANDOM_GENERATED_TOKEN = Boolean
                .getBoolean("dcp.management.name.prefix.add.random.generated.token");
        public static final String RANDOM_GENERATED_TOKEN_DELIMITER = System.getProperty(
                "dcp.management.name.prefix.random.generated.token.delimiter", "-");

        /** (Required) Prefix or suffix name. */
        @Documentation(description = "Prefix or suffix name", exampleString = "prefix")
        public String prefix;

        /** The amount of ending digits. */
        @Documentation(description = "The amount of ending digits")
        public long numberOfDigits;

        /** The number where the prefix sequence will begin. */
        @Documentation(description = "The number where the prefix sequence will begin.")
        public long nextNumber;

        /** Flag indicating whether to add additional generated token to the  sequence **/
        @Documentation(description = "Flag indicating whether to add additional generated token to the  sequence.")
        public Boolean addRandomToken;

        /* The current count number */
        @UsageOption(option = PropertyUsageOption.SERVICE_USE)
        public long currentCount = -1;

        public long getMaxNumber() {
            return ((long) Math.pow(10, numberOfDigits)) - 1;
        }

        public long getRange() {
            return getMaxNumber() - nextNumber;
        }
    }

    /** An DTO used during PATCH request in order to get the next prefix in the sequence. */
    public static class NamePrefixRequest {
        public long resourceCount;
    }

    /** An DTO used during PATCH response in order to return the requested prefixes. */
    public static class NamePrefixResponse {
        public List<String> resourceNamePrefixes;
    }

    public ResourceNamePrefixService() {
        super(ResourceNamePrefixState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation start) {
        if (!checkForBody(start)) {
            return;
        }
        ResourceNamePrefixState state = start.getBody(ResourceNamePrefixState.class);
        logFine("Initial name is %s", state.prefix);
        try {
            validateStateOnStart(state);
            start.complete();
        } catch (Throwable e) {
            start.fail(e);
        }
    }

    private static void validateStateOnStart(ResourceNamePrefixState state) {
        assertNotEmpty(state.prefix, "prefix");
        if (state.prefix.length() > MAX_PREFIX_LENGTH) {
            throw new LocalizableValidationException("'prefix' must be less characters than "
                    + MAX_PREFIX_LENGTH + 1, "common.resource-name.characters", MAX_PREFIX_LENGTH + 1);
        }

        if (state.numberOfDigits < 0) {
            throw new LocalizableValidationException("'numberOfDigits' must be positive.", "common.resource-name.digits.positive");
        } else if (state.numberOfDigits == 0) {
            state.numberOfDigits = DEFAULT_NUMBER_OF_DIGITS;
        } else if (state.numberOfDigits > MAX_NUMBER_OF_DIGITS) {
            throw new LocalizableValidationException("'numberOfDigits' must be less than: "
                    + MAX_NUMBER_OF_DIGITS, "common.resource-name.digits.range", MAX_NUMBER_OF_DIGITS);
        }

        if (state.nextNumber < 0) {
            throw new LocalizableValidationException("'nextNumber' must be positive.", "common.resource-name.next-number.positive");
        } else if (state.nextNumber == 0) {
            state.nextNumber = DEFAULT_NEXT_NUMBER;
        } else if (state.nextNumber >= state.getMaxNumber()) {
            throw new LocalizableValidationException("'nextNumber' must be less than the max number of "
                    + state.getMaxNumber(), "common.resource-name.next-number.max", state.getMaxNumber());
        }

        if (state.currentCount == -1) { // initialize the counter only on initial start up.
            state.currentCount = state.nextNumber;
        }

        if (state.addRandomToken == null) {
            state.addRandomToken = ADD_RANDOM_GENERATED_TOKEN;
        }

    }

    @Override
    public void handlePatch(Operation patch) {
        if (!checkForBody(patch)) {
            return;
        }
        adjustStat(ResourceNamePrefixState.class.getSimpleName().toString(), 1);

        NamePrefixRequest request = patch.getBody(NamePrefixRequest.class);
        ResourceNamePrefixState state = getState(patch);
        if (request.resourceCount <= 0) {
            patch.fail(new LocalizableValidationException(
                    "Requested resource count must be positive number.", "common.name-prefix.count.positive"));
            return;
        } else if (request.resourceCount > state.getRange()) {
            patch.fail(new LocalizableValidationException(
                    "Requested resource count must be less than the range.", "common.name-prefix.count.range"));
            return;
        }

        NamePrefixResponse response = new NamePrefixResponse();
        response.resourceNamePrefixes = new ArrayList<String>((int) request.resourceCount);

        for (int i = 0; i < request.resourceCount; i++) {
            final StringBuilder namePrefix = new StringBuilder();
            namePrefix.append(state.prefix);
            namePrefix.append(state.currentCount++);
            if (state.addRandomToken) {
                namePrefix.append(RANDOM_GENERATED_TOKEN_DELIMITER);
                //adding time since 2016 as shortest and smallest possible guaranteed random token
                long timestamp = System.currentTimeMillis() - SINCE_TIME;
                namePrefix.append(timestamp);
            }
            response.resourceNamePrefixes.add(namePrefix.toString());
            if (state.currentCount > state.getMaxNumber()) {
                // reset back to the beginning.
                logWarning("Reseting name prefix counter [%s] to initial value [%s]...",
                        state.currentCount, state.nextNumber);
                state.currentCount = state.nextNumber;
            }
        }

        patch.setBodyNoCloning(response);
        patch.complete();
    }

    public static String getDefaultResourceNameFormat(String baseName) {
        if (ResourceNamePrefixState.DEFAULT_PREFIX_FLAG) {
            return "%s" + ResourceNamePrefixState.PREFIX_DELIMITER + baseName;
        } else {
            return baseName + ResourceNamePrefixState.PREFIX_DELIMITER + "%s";
        }
    }
}
