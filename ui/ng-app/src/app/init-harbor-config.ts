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

import { HarborLibraryModule, SERVICE_CONFIG, IServiceConfig } from 'harbor-ui';
import { FT } from './utils/ft';

export function initHarborConfig() {
    var sc: IServiceConfig = {
        systemInfoEndpoint: "/hbr-api/systeminfo",
        repositoryBaseEndpoint: "/hbr-api/repositories",
        vulnerabilityScanningBaseEndpoint: "/hbr-api/repositories",
        logBaseEndpoint: "/hbr-api/logs",
        targetBaseEndpoint: "/hbr-api/targets",
        replicationBaseEndpoint: "/hbr-api/replications",
        replicationRuleEndpoint: "/hbr-api/policies/replication",
        replicationJobEndpoint: "/hbr-api/jobs/replication",
        projectBaseEndpoint: "/hbr-api/projects",
        enablei18Support: true,
        langMessageLoader: FT.isHbrEnabled() ? "http" : null,
        langMessagePathForHttpLoader: "/hbr-api/i18n/lang/",
        configurationEndpoint: "/hbr-api/configurations",
        scanJobEndpoint: "/hbr-api/jobs/scan"
    };

    return sc;
}
