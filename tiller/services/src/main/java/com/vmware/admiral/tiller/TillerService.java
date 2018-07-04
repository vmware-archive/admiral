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

package com.vmware.admiral.tiller;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.StatefulService;

public class TillerService extends StatefulService {

    public static class TillerState extends ResourceState {

        public String k8sClusterSelfLink;

        public String tillerNamespace;

        public Integer tillerPort;

        public String tillerCertificateAuthorityLink;

        public String tillerCredentialsLink;
    }

    // TODO extract to a common place like ManagementUriParts
    public static final String FACTORY_LINK = ManagementUriParts.RESOURCES + "/tillers";

    public TillerService() {
        super(TillerState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

}
