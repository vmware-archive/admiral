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

package com.vmware.admiral.upgrade.transformation.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.service.common.MultiTenantDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

public class ClonePerProjectUtil {

    private static final String TEMPLATE_DOCUMENT_KIND = Utils.buildKind(CompositeDescription.class);

    public static void processDocuments(List<MultiTenantDocument> documents, Operation post,
            Service sender, String factoryLink, URI referer,
            ServiceHost host, boolean generateSelfLink) {
        QueryTask queryTask = QueryUtil.buildQuery(ProjectState.class, true);
        QueryUtil.addExpandOption(queryTask);
        List<ProjectState> projects = new ArrayList<ProjectState>();
        new ServiceDocumentQuery<ProjectState>(host, ProjectState.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        host.log(Level.SEVERE, "Failed to query for project states");
                        post.fail(r.getException());
                    } else if (r.hasResult()) {
                        projects.add(r.getResult());
                    } else {
                        host.log(Level.INFO, "projects found: %d", projects.size());
                        processProjects(projects, documents, post, sender,
                                factoryLink, referer, host, generateSelfLink);
                    }
                });
    }

    public static void processProjects(List<ProjectState> projects,
            List<MultiTenantDocument> documents,
            Operation post, Service sender, String factoryLink, URI referer,
            ServiceHost host, boolean generateSelfLink) {
        if (documents == null || documents.size() == 0 || projects == null
                || projects.size() == 0) {
            host.log(Level.INFO,
                    "No documents/projects found. Transformation completed successfully.");
            post.complete();
            return;
        }
        AtomicInteger projectsToProcess = new AtomicInteger(projects.size());
        AtomicBoolean failed = new AtomicBoolean();
        for (ProjectState project : projects) {
            AtomicInteger documentsToProcess = new AtomicInteger(documents.size());
            for (MultiTenantDocument document : documents) {
                if (generateSelfLink) {
                    document.documentSelfLink = document.documentSelfLink + "-" + project.name;
                } else {
                    document.documentSelfLink = null;
                }
                if (!skipAddProjectTenantLink(document)) {
                    document.tenantLinks = new ArrayList<String>();
                    document.tenantLinks.add(project.documentSelfLink);
                }
                Operation.createPost(sender, factoryLink)
                        .setBody(document)
                        .setReferer(referer)
                        .setCompletion((o, ex) -> {
                            if (ex != null) {
                                host.log(Level.SEVERE, "Failed to clone document");
                                if (failed.compareAndSet(false, true)) {
                                    post.fail(ex);
                                }
                            } else {
                                host.log(Level.INFO, "document created created %s",
                                        o.getBody(ServiceDocument.class).documentSelfLink);
                                if (documentsToProcess.decrementAndGet() == 0) {
                                    if (projectsToProcess.decrementAndGet() == 0) {
                                        host.log(Level.INFO,
                                                "document transformation completed successfully");
                                        if (!failed.get()) {
                                            post.complete();
                                        }

                                    }
                                }
                            }
                        }).sendWith(host);
            }
        }
    }

    /**
     * Checks whether the document specified is a {@link CompositeDescription} in embedded.
     * Templates ({@link CompositeDescription} instances) in embedded mode are shared among all
     * projects so the project tenant link must not be added.
     *
     * @return whether addition of the project tenant link should be skipped or not
     */
    private static boolean skipAddProjectTenantLink(MultiTenantDocument document) {
        return ConfigurationUtil.isEmbedded()
                && document.documentKind != null
                && document.documentKind.equals(TEMPLATE_DOCUMENT_KIND);
    }

    public static <R extends MultiTenantDocument> void getDocuments(Class<R> type,
            Consumer<List<MultiTenantDocument>> consumer, ServiceHost host, Operation post) {
        QueryTask queryTask = QueryUtil.buildQuery(type, true);
        QueryUtil.addExpandOption(queryTask);
        List<MultiTenantDocument> documents = new ArrayList<>();
        new ServiceDocumentQuery<R>(host, type)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        host.log(Level.SEVERE, "Failed to query for project states");
                        post.fail(r.getException());
                    } else if (r.hasResult()) {
                        documents.add((MultiTenantDocument) r.getResult());
                    } else {
                        host.log(Level.INFO, "projects found: %d", documents.size());
                        consumer.accept(documents);
                    }
                });
    }
}
