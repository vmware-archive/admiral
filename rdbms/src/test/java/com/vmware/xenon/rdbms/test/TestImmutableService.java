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

package com.vmware.xenon.rdbms.test;

import java.util.List;

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;

public class TestImmutableService extends StatefulService {
    public static final String FACTORY_LINK = "/resources/test-immutable-items";

    public static class TestImmutableState extends ServiceDocument {
        @PropertyOptions(usage = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String firstName;

        @PropertyOptions(usage = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL, indexing =
                PropertyIndexingOption.CASE_INSENSITIVE)
        public String lastName;

        public List<String> tenantLinks;
    }

    public TestImmutableService() {
        super(TestImmutableState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IMMUTABLE, true);
    }

}
