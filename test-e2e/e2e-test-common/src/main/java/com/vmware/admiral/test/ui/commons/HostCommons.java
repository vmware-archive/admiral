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

package com.vmware.admiral.test.ui.commons;

import java.util.Objects;

import com.vmware.admiral.test.ui.pages.CommonWebClient;
import com.vmware.admiral.test.ui.pages.clusters.AddClusterPage;
import com.vmware.admiral.test.util.HostType;

public class HostCommons {

    public static void addHost(CommonWebClient<?> client, String hostName, String description,
            HostType hostType,
            String hostAddress, String credentialsName, boolean acceptCertificate) {
        client.clusters().clustersPage().clickAddClusterButton();

        AddClusterPage addHostDialog = client.clusters().addClusterPage();
        addHostDialog.waitToLoad();
        addHostDialog.setName(hostName);
        if (Objects.nonNull(description)) {
            addHostDialog.setDescription(description);
        }
        addHostDialog.setHostType(hostType);
        addHostDialog.setUrl(hostAddress);
        if (Objects.nonNull(credentialsName)) {
            addHostDialog.selectCredentials(credentialsName);
        }
        addHostDialog.submit();
        if (acceptCertificate) {
            client.clusters().certificateModalDialog().waitToLoad();
            client.clusters().certificateModalDialog().submit();
        }
        client.clusters().clustersPage().validate().validateHostExistsWithName(hostName);
    }

    public static void deleteHost(CommonWebClient<?> client, String hostName) {
        client.clusters().clustersPage().clickHostDeleteButton(hostName);
        client.clusters().deleteHostDialog().waitToLoad();
        client.clusters().deleteHostDialog().submit();
    }

}
