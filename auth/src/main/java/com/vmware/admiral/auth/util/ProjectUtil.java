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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.google.gson.annotations.SerializedName;

import com.vmware.admiral.auth.project.ProjectFactoryService;
import com.vmware.admiral.auth.project.ProjectService;
import com.vmware.admiral.auth.project.ProjectService.ExpandedProjectState;
import com.vmware.admiral.auth.project.ProjectService.ProjectState;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.cluster.ClusterService;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.service.common.HbrApiProxyService;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;
import com.vmware.xenon.services.common.UserService.UserState;

public class ProjectUtil {
    public static final String PROJECT_IN_USE_MESSAGE = "Project is associated with %s placement%s";
    public static final String PROJECT_IN_USE_MESSAGE_CODE = "host.resource-group.in.use";

    private static class HbrRepositoriesResponse {
        public static final String FIELD_NAME_RESPONSE_ENTRIES = "responseEntries";

        /** List of response entries returned by Harbor */
        public List<HbrRepositoriesResponseEntry> responseEntries;
    }

    private static class HbrRepositoriesResponseEntry {
        /** Name of the repository. */
        public String name;

        @SerializedName("tags_count")
        /** Number of tags for this repository. */
        public long tagsCount;
    }

    public static final long PROJECT_INDEX_ORIGIN = 2L;
    public static final long PROJECT_INDEX_BOUND = (long)Integer.MAX_VALUE * 2; // simulate uint


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
    public static DeferredResult<ExpandedProjectState> expandProjectState(ServiceHost host,
            ProjectState simpleState, URI referer) {
        ExpandedProjectState expandedState = new ExpandedProjectState();
        simpleState.copyTo(expandedState);

        DeferredResult<Void> retrieveAdmins = retrieveUserGroupMembers(host,
                simpleState.administratorsUserGroupLinks, referer)
                        .thenAccept((adminsList) -> expandedState.administrators = adminsList);
        DeferredResult<Void> retrieveMembers = retrieveUserGroupMembers(host,
                simpleState.membersUserGroupLinks, referer)
                        .thenAccept((membersList) -> expandedState.members = membersList);
        DeferredResult<Void> retrieveViewers = retrieveUserGroupMembers(host,
                simpleState.viewersUserGroupLinks, referer)
                        .thenAccept((viewersList) -> expandedState.viewers = viewersList);
        DeferredResult<Void> retrieveClusterLinks = retrieveClusterLinks(host,
                simpleState.documentSelfLink)
                        .thenAccept((clusterLinks) -> expandedState.clusterLinks = clusterLinks);
        DeferredResult<Void> retrieveTemplateLinks = retrieveTemplateLinks(host,
                simpleState.documentSelfLink)
                        .thenAccept((templateLinks) -> {
                            expandedState.templateLinks = templateLinks;
                        });
        DeferredResult<Void> retrieveRepositoriesAndImagesCount = retrieveRepositoriesAndTagsCount(host,
                simpleState.documentSelfLink, getHarborId(simpleState))
                        .thenAccept(
                                (repositories) -> {
                                    expandedState.repositories = new ArrayList<>(repositories.size());
                                    expandedState.numberOfImages = 0L;

                                    repositories.forEach((entry) -> {
                                        expandedState.repositories.add(entry.name);
                                        expandedState.numberOfImages += entry.tagsCount;
                                    });
                                });

        return DeferredResult.allOf(retrieveAdmins, retrieveMembers, retrieveViewers,
                retrieveClusterLinks, retrieveTemplateLinks, retrieveRepositoriesAndImagesCount)
                .thenApply((ignore) -> expandedState);
    }

    public static String getHarborId(ProjectState state) {
        if (state == null || state.customProperties == null) {
            return null;
        }
        return state.customProperties.get(ProjectService.CUSTOM_PROPERTY_PROJECT_INDEX);
    }

    private static DeferredResult<List<String>> retrieveClusterLinks(ServiceHost host,
            String projectLink) {
        return retrieveProjectRelatedDocumentLinks(host, projectLink, ResourcePoolState.class,
                "clusters")
                        .thenApply(
                                (links) -> {
                                    return links.stream()
                                            .map((link) -> UriUtils.buildUriPath(
                                                    ClusterService.SELF_LINK, Service.getId(link)))
                                            .collect(Collectors.toList());
                                });
    }

    private static DeferredResult<List<String>> retrieveTemplateLinks(ServiceHost host,
            String projectLink) {
        return retrieveProjectRelatedDocumentLinks(host, projectLink, CompositeDescription.class,
                "templates");
    }

    private static <T extends ServiceDocument> DeferredResult<List<String>> retrieveProjectRelatedDocumentLinks(
            ServiceHost host, String projectLink, Class<T> documentClass, String documentName) {
        return new QueryByPages<T>(host,
                QueryUtil.createKindClause(documentClass), documentClass,
                Collections.singletonList(projectLink)).collectLinks(Collectors.toList())
                        .exceptionally((ex) -> {
                            host.log(Level.WARNING,
                                    "Could not retrieve %s for project %s: %s", documentName,
                                    projectLink, Utils.toString(ex));
                            return Collections.emptyList();
                        });
    }

    private static DeferredResult<List<HbrRepositoriesResponseEntry>> retrieveRepositoriesAndTagsCount(
            ServiceHost host, String projectLink, String harborId) {
        if (harborId == null || harborId.isEmpty()) {
            host.log(Level.WARNING,
                    "harborId not set for project %s. Skipping repository retrieval", projectLink);
            return DeferredResult.completed(Collections.emptyList());
        }

        Operation getRepositories = Operation
                .createGet(UriUtils.buildUri(host,
                        UriUtils.buildUriPath(HbrApiProxyService.SELF_LINK,
                                HbrApiProxyService.HARBOR_ENDPOINT_REPOSITORIES),
                        UriUtils.buildUriQuery(HbrApiProxyService.HARBOR_QUERY_PARAM_PROJECT_ID,
                                harborId,
                                HbrApiProxyService.HARBOR_QUERY_PARAM_DETAIL,
                                Boolean.toString(true))))
                .setReferer(ProjectFactoryService.SELF_LINK);

        return host.sendWithDeferredResult(getRepositories)
                .thenApply((op) -> {
                    Object body = op.getBodyRaw();
                    String stringBody = body instanceof String ? (String) body : Utils.toJson(body);

                    // Harbor is returning a list of JSON objects and since in java generic types
                    // are runtime only, the only types that we can get the response body are String
                    // and List of maps. If we try to do a Utils.fromJson later on for each list
                    // entry, we are very likely to get GSON parsing errors since Map.toString
                    // method (called by Utils.fromJson) does not produce valid JSON. For
                    // repositories, the forward slash in the name of the repository breaks
                    // everything.
                    // The following is a workaround: manually wrap the raw output in a
                    // valid JSON object with a single property (list of entries) and parse that.
                    String json = String.format("{\"%s\": %s}",
                            HbrRepositoriesResponse.FIELD_NAME_RESPONSE_ENTRIES, stringBody);

                    HbrRepositoriesResponse response = Utils.fromJson(json, HbrRepositoriesResponse.class);
                    return response.responseEntries;
                })
                .exceptionally((ex) -> {
                    host.log(Level.WARNING,
                            "Could not retrieve repositories for project %s with harborId %s: %s",
                            projectLink, harborId, Utils.toString(ex));
                    return Collections.emptyList();
                });
    }

    /**
     * Retrieves the list of members for the specified by document link user group.
     *
     * @see #retrieveUserStatesForGroup(UserGroupState)
     */
    private static DeferredResult<List<UserState>> retrieveUserGroupMembers(ServiceHost host,
            List<String> groupLinks, URI referer) {
        if (groupLinks == null) {
            return DeferredResult.completed(new ArrayList<>(0));
        }

        List<DeferredResult<List<UserState>>> results = new ArrayList<>();

        for (String groupLink : groupLinks) {
            if (groupLink == null || groupLink.isEmpty()) {
                continue;
            }
            Operation groupGet = Operation.createGet(host, groupLink).setReferer(referer);
            results.add(host.sendWithDeferredResult(groupGet, UserGroupState.class)
                    .thenCompose((groupState) -> retrieveUserStatesForGroup(host, groupState)));
        }

        if (results.isEmpty()) {
            return DeferredResult.completed(new ArrayList<>(0));
        }

        return DeferredResult.allOf(results).thenApply(userStates -> {
            List<UserState> states = new ArrayList<>();
            userStates.forEach(states::addAll);
            return states;
        });
    }

    /**
     * Retrieves the list of members for the specified user group.
     */
    public static DeferredResult<List<UserState>> retrieveUserStatesForGroup(ServiceHost host,
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

    /**
     * Builds a {@link Query} that selects all projects that contain any of the specified groups in
     * one of the administrators or members group lists
     */
    public static Query buildQueryProjectsFromGroups(Collection<String> groupLinks) {
        Query query = new Query();

        Query kindQuery = QueryUtil.createKindClause(ProjectState.class);
        kindQuery.setOccurance(Occurance.MUST_OCCUR);
        query.addBooleanClause(kindQuery);

        Query groupQuery = new Query();
        groupQuery.setOccurance(Occurance.MUST_OCCUR);
        query.addBooleanClause(groupQuery);

        Query adminGroupQuery = QueryUtil.addListValueClause(
                QuerySpecification.buildCollectionItemName(
                        ProjectState.FIELD_NAME_ADMINISTRATORS_USER_GROUP_LINKS),
                groupLinks, MatchType.TERM);
        adminGroupQuery.setOccurance(Occurance.SHOULD_OCCUR);
        groupQuery.addBooleanClause(adminGroupQuery);

        Query membersGroupQuery = QueryUtil.addListValueClause(
                QuerySpecification
                        .buildCollectionItemName(ProjectState.FIELD_NAME_MEMBERS_USER_GROUP_LINKS),
                groupLinks, MatchType.TERM);
        membersGroupQuery.setOccurance(Occurance.SHOULD_OCCUR);
        groupQuery.addBooleanClause(membersGroupQuery);

        return query;
    }

    public static Query buildQueryForProjectsFromProjectIndex(long projectIndex) {
        return QueryUtil.addCaseInsensitiveListValueClause(QuerySpecification
                .buildCompositeFieldName(ProjectState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ProjectService.CUSTOM_PROPERTY_PROJECT_INDEX),
                Collections.singletonList(Long.toString(projectIndex)), MatchType.TERM);
    }

    public static Query buildQueryForProjectsFromName(String name, String documentSelfLink) {
        Query query = new Query();

        Query nameClause = QueryUtil.addCaseInsensitiveListValueClause(ProjectState.FIELD_NAME_NAME,
                Collections.singletonList(name), MatchType.TERM);

        Query selfLinkClause = QueryUtil.addCaseInsensitiveListValueClause(ProjectState
                .FIELD_NAME_SELF_LINK, Collections.singletonList(documentSelfLink), MatchType.TERM);
        selfLinkClause.setOccurance(Occurance.MUST_NOT_OCCUR);

        query.addBooleanClause(nameClause);
        query.addBooleanClause(selfLinkClause);

        return query;
    }

    public static long generateRandomUnsignedInt() {
        return ThreadLocalRandom.current().nextLong(PROJECT_INDEX_ORIGIN, PROJECT_INDEX_BOUND);
    }
}