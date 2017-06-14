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

package com.vmware.admiral;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class UiComputeService extends BaseUiService {

    public static final String SELF_LINK = ManagementUriParts.UI_COMPUTE_SERVICE;

    @Override
    protected void startUiFileContentServices() throws Throwable {
        Map<Path, String> pathToURIPath = new HashMap<>();

        String servicePath = Utils.buildServicePath(UiOgService.class);
        Path baseResourcePath = Paths.get(Utils.UI_DIRECTORY_NAME, servicePath);
        try {
            pathToURIPath = discoverUiResources(baseResourcePath, this);
        } catch (Throwable e) {
            log(Level.WARNING, "Error enumerating UI resources for %s: %s", this.getSelfLink(),
                    Utils.toString(e));
        }
        for (Entry<Path, String> e : pathToURIPath.entrySet()) {
            String value = e.getValue();

            Operation post = Operation
                    .createPost(UriUtils.buildUri(getHost(), value));
            RestrictiveFileContentService fcs = new RestrictiveFileContentService(
                    e.getKey().toFile());
            getHost().startService(post, fcs);
        }
    }
}
