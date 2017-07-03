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

package com.vmware.admiral.auth.project.transformation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeDescriptionService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * The logic is executed during the upgrade from VIC 1.1 to 1.2 and vRA 7.3 to 7.4. The service
 * clones the composite descriptions for all the available projects
 */
public class CompositeDescriptionTransformationService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.COMPOSITE_DESCRIPTION_UPGRADE_TRANSFORM_PATH;

    @Override
    public void handlePost(Operation post) {
        QueryTask queryTask = QueryUtil.buildQuery(CompositeDescription.class, true);
        QueryUtil.addExpandOption(queryTask);
        List<CompositeDescription> compositeDescriptions = new ArrayList<CompositeDescription>();
        new ServiceDocumentQuery<CompositeDescription>(getHost(), CompositeDescription.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        post.fail(r.getException());
                    } else if (r.hasResult()) {
                        compositeDescriptions.add(r.getResult());
                    } else {
                        processCompositeDescriptions(compositeDescriptions, post);
                    }
                });
    }

    private void processCompositeDescriptions(List<CompositeDescription> compositeDescriptions,
            Operation post) {
        QueryTask queryTask = QueryUtil.buildQuery(ProjectState.class, true);
        QueryUtil.addExpandOption(queryTask);
        List<ProjectState> projects = new ArrayList<ProjectState>();
        new ServiceDocumentQuery<ProjectState>(getHost(), ProjectState.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        post.fail(r.getException());
                    } else if (r.hasResult()) {
                        projects.add(r.getResult());
                    } else {
                        processProjects(projects, compositeDescriptions, post);
                    }
                });
    }

    private void processProjects(List<ProjectState> projects,
            List<CompositeDescription> compositeDescriptions,
            Operation post) {
        AtomicInteger projectsToProcess = new AtomicInteger(projects.size());
        for (ProjectState project : projects) {
            AtomicInteger compositeDescriptionsToProcess = new AtomicInteger(compositeDescriptions.size());
            for (CompositeDescription compositeDescription : compositeDescriptions) {
                compositeDescription.documentSelfLink = null;
                compositeDescription.tenantLinks = new ArrayList<String>();
                compositeDescription.tenantLinks.add(project.documentSelfLink);
                Operation.createPost(this, CompositeDescriptionService.SELF_LINK)
                        .setBody(compositeDescription)
                        .setReferer(UriUtils.buildUri(getHost(), SELF_LINK))
                        .setCompletion((o, ex) -> {
                            if (ex != null) {
                                post.fail(ex);
                            } else {
                                if (compositeDescriptionsToProcess.decrementAndGet() == 0) {
                                    if (projectsToProcess.decrementAndGet() == 0) {
                                        post.complete();
                                    }
                                }
                            }
                        }).sendWith(getHost());
            }
        }
    }
}
