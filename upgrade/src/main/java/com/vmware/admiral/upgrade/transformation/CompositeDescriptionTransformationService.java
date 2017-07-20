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

package com.vmware.admiral.upgrade.transformation;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.CompositeDescriptionService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.upgrade.transformation.util.ClonePerProjectUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * The logic is executed during the upgrade from VIC 1.1 to 1.2 and vRA 7.3 to 7.4. The service
 * clones the composite descriptions for all the available projects
 */
public class CompositeDescriptionTransformationService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.COMPOSITE_DESCRIPTION_UPGRADE_TRANSFORM_PATH;

    @Override
    public void handlePost(Operation post) {
        ClonePerProjectUtil.getDocuments(CompositeDescription.class,
                (result) -> ClonePerProjectUtil.processDocuments(result, post, this,
                        CompositeDescriptionService.SELF_LINK,
                        UriUtils.buildUri(getHost(), SELF_LINK), getHost(), false),
                getHost(), post);
    }
}
