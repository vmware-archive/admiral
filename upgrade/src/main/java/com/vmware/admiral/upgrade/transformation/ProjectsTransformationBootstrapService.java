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

package com.vmware.admiral.upgrade.transformation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * One-time node group setup (bootstrap) for removing the self link of the project form the tenant
 * links.
 *
 * The service will update all the projects tenant links and will remove the slef link of the
 * project links
 *
 * This service is guaranteed to be performed only once within entire node group, in a consistent
 * safe way. Durable for restarting the owner node or even complete shutdown and restarting of all
 * nodes. Following the SampleBootstrapService.
 */
public class ProjectsTransformationBootstrapService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.CONFIG
            + "/projects-upgrade-bootstrap";

    public static FactoryService createFactory() {
        return FactoryService.create(ProjectsTransformationBootstrapService.class);
    }

    public static CompletionHandler startTask(ServiceHost host) {
        return (o, e) -> {
            if (e != null) {
                host.log(Level.SEVERE, Utils.toString(e));
                return;
            }
            // create service with fixed link
            // POST will be issued multiple times but will be converted to PUT after the first one.
            ServiceDocument doc = new ServiceDocument();
            doc.documentSelfLink = "projects-upgrade-bootstrap-task";
            Operation.createPost(host, ProjectsTransformationBootstrapService.FACTORY_LINK)
                    .setBody(doc)
                    .setReferer(host.getUri())
                    .setCompletion((oo, ee) -> {
                        if (ee != null) {
                            host.log(Level.SEVERE, Utils.toString(ee));
                            return;
                        }
                        host.log(Level.INFO, "projects-upgrade-bootstrap-task");
                    })
                    .sendWith(host);
        };
    }

    public ProjectsTransformationBootstrapService() {
        super(ServiceDocument.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation post) {
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
                        getHost().log(Level.INFO, "projects found: %d", projects.size());
                        processProjects(projects, post);
                    }
                });
    }

    private void processProjects(List<ProjectState> projects, Operation post) {
        if (projects.size() == 0) {
            post.complete();
            return;

        }
        AtomicInteger projectsCount = new AtomicInteger(projects.size());
        for (ProjectState project : projects) {
            if (project.tenantLinks == null || project.tenantLinks.isEmpty()
                    || !project.tenantLinks.contains(project.documentSelfLink)) {
                if (projectsCount.decrementAndGet() == 0) {
                    post.complete();
                }
            } else {
                project.tenantLinks.remove(project.documentSelfLink);

                Operation.createPatch(this, project.documentSelfLink)
                        .setBody(project)
                        .setReferer(UriUtils.buildUri(getHost(), FACTORY_LINK))
                        .setCompletion((o, ex) -> {
                            if (ex != null) {
                                post.fail(ex);
                            } else {
                                logInfo("Project state %s updated with tenantLinks",
                                        project.documentSelfLink);
                                if (projectsCount.decrementAndGet() == 0) {
                                    logInfo("Projects tranformation completed successfully");
                                    post.complete();
                                }
                            }
                        }).sendWith(getHost());
            }
        }
    }

    @Override
    public void handlePut(Operation put) {
        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            // converted PUT due to IDEMPOTENT_POST option
            put.complete();
            return;
        }
        // normal PUT is not supported
        put.fail(Operation.STATUS_CODE_BAD_METHOD);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }

}
