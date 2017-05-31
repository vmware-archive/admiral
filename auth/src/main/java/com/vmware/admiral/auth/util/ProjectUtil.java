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

package com.vmware.admiral.auth.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import com.vmware.admiral.auth.project.ProjectService.ExpandedProjectState;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;
import com.vmware.xenon.services.common.UserService.UserState;

public class ProjectUtil {
    public static final String PROJECT_IN_USE_MESSAGE = "Project is associated with %s placement%s";
    public static final String PROJECT_IN_USE_MESSAGE_CODE = "host.resource-group.in.use";

    public static QueryTask createQueryTaskForProjectAssociatedWithPlacement(ResourceState project, Query query) {
        QueryTask queryTask = null;
        if (query != null) {
            queryTask = QueryTask.Builder.createDirectTask().setQuery(query).build();
        } else if (project != null && project.documentSelfLink != null) {
            queryTask = QueryUtil.buildQuery(GroupResourcePlacementState.class, true, QueryUtil.addTenantAndGroupClause(Arrays.asList(project.documentSelfLink)));
        }

        if (queryTask != null) {
            QueryUtil.addCountOption(queryTask);
        }

        return queryTask;
    }

    /**
     * Creates a {@link ExpandedProjectState} based on the provided simple state additionally
     * building the lists of administrators and members.
     *
     * @param host a {@link ServiceHost} that can be used to retrieve service documents
     * @param simpleState the {@link ProjectState} that needs to be expanded
     * @param referer the {@link URI} of the service that issues the expand
     */
    public static DeferredResult<ExpandedProjectState> expandProjectState(
            ServiceHost host, ProjectState simpleState, URI referer) {
        ExpandedProjectState expandedState = new ExpandedProjectState();
        simpleState.copyTo(expandedState);

        DeferredResult<Void> retrieveAdmins = retrieveUserGroupMembers(host,
                simpleState.administratorsUserGroupLink, referer)
                        .thenAccept((adminsList) -> expandedState.administrators = adminsList);
        DeferredResult<Void> retrieveMembers = retrieveUserGroupMembers(host,
                simpleState.membersUserGroupLink, referer)
                        .thenAccept((membersList) -> expandedState.members = membersList);
        DeferredResult<Void> retrieveClusterLinks = retrieveClusterLinks(simpleState.documentSelfLink)
                .thenAccept((clusterLinks) -> expandedState.clusterLinks = clusterLinks);
        DeferredResult<Void> retrieveRepositoryLinks = retrieveRepositoryLinks(simpleState.documentSelfLink)
                .thenAccept((repositoryLinks) -> expandedState.repositoryLinks = repositoryLinks);

        return DeferredResult.allOf(retrieveAdmins, retrieveMembers, retrieveClusterLinks,
                retrieveRepositoryLinks).thenApply((ignore) -> expandedState);
    }

    private static DeferredResult<List<String>> retrieveClusterLinks(String projectLink) {
        // TODO implement when the Cluster service becomes available
        final int maxDummyClusters = 7;
        return DeferredResult.completed(createDummyLinksList("/clusters/dummy-cluster",
                // bit-mask to avoid Math.abs. Interesting read on the topic:
                // http://findbugs.blogspot.bg/2006/09/is-mathabs-broken.html
                (projectLink.hashCode() & Integer.MAX_VALUE) % maxDummyClusters));
    }

    private static DeferredResult<List<String>> retrieveRepositoryLinks(String projectLink) {
        // TODO implement when the proxy service that fetches data from Harbor becomes available
        final int maxDummyRepositories = 13;
        return DeferredResult.completed(createDummyLinksList("/repositories/dummy-repository",
                // bit-mask to avoid Math.abs. Interesting read on the topic:
                // http://findbugs.blogspot.bg/2006/09/is-mathabs-broken.html
                (projectLink.hashCode() & Integer.MAX_VALUE) % maxDummyRepositories));
    }

    private static List<String> createDummyLinksList(String linksPrefix, int linksCount) {
        ArrayList<String> dummyLinks = new ArrayList<>(linksCount);
        for (int i = 0; i < linksCount; i++) {
            dummyLinks.add(String.format("%s-%d", linksPrefix, i));
        }
        return dummyLinks;
    }

    /**
     * Retrieves the list of members for the specified by document link user group.
     *
     * @see #retrieveUserStatesForGroup(UserGroupState)
     */
    private static DeferredResult<List<UserState>> retrieveUserGroupMembers(ServiceHost host,
            String groupLink, URI referer) {
        if (groupLink == null) {
            return DeferredResult.completed(new ArrayList<>(0));
        }

        Operation groupGet = Operation.createGet(host, groupLink).setReferer(referer);
        return host.sendWithDeferredResult(groupGet, UserGroupState.class)
                .thenCompose((groupState) -> retrieveUserStatesForGroup(host, groupState));
    }

    /**
     * Retrieves the list of members for the specified user group.
     */
    private static DeferredResult<List<UserState>> retrieveUserStatesForGroup(ServiceHost host,
            UserGroupState groupState) {
        DeferredResult<List<UserState>> deferredResult = new DeferredResult<>();
        ArrayList<UserState> resultList = new ArrayList<>();

        QueryTask queryTask = QueryUtil.buildQuery(UserState.class, true, groupState.query);
        QueryUtil.addExpandOption(queryTask);
        new ServiceDocumentQuery<UserState>(host, UserState.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        host.log(Level.WARNING,
                                "Failed to retrieve members of UserGroupState %s: %s",
                                groupState.documentSelfLink, Utils.toString(r.getException()));
                        deferredResult.fail(r.getException());
                    } else if (r.hasResult()) {
                        resultList.add(r.getResult());
                    } else {
                        deferredResult.complete(resultList);
                    }
                });

        return deferredResult;
    }

}