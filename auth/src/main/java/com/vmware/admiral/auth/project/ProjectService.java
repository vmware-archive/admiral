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

package com.vmware.admiral.auth.project;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.management.ServiceNotFoundException;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.admiral.auth.idm.AuthRole;
import com.vmware.admiral.auth.idm.Principal;
import com.vmware.admiral.auth.project.ProjectRolesHandler.ProjectRoles;
import com.vmware.admiral.auth.util.AuthUtil;
import com.vmware.admiral.auth.util.ProjectUtil;
import com.vmware.admiral.auth.util.UserGroupsUpdater;
import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.adapters.util.Pair;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ResourceGroupService;
import com.vmware.xenon.services.common.ResourceGroupService.ResourceGroupState;
import com.vmware.xenon.services.common.RoleService;
import com.vmware.xenon.services.common.RoleService.RoleState;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;

/**
 * Project is a group sharing same resources.
 */
public class ProjectService extends StatefulService {

    public static final String PROJECT_NAME_ALREADY_USED_MESSAGE = "Project name '%s' "
            + "is already used.";
    public static final String PROJECT_NAME_ALREADY_USED_CODE = "auth.projects.name.used";

    public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";

    public static final String CUSTOM_PROPERTY_PROJECT_INDEX = "__projectIndex";

    public static final String DEFAULT_PROJECT_ID = "default-project";
    public static final String DEFAULT_PROJECT_INDEX = "1";
    public static final String DEFAULT_PROJECT_LINK = UriUtils
            .buildUriPath(ProjectFactoryService.SELF_LINK, DEFAULT_PROJECT_ID);

    public static ProjectState buildDefaultProjectInstance() {
        ProjectState project = new ProjectState();
        project.documentSelfLink = DEFAULT_PROJECT_LINK;
        project.name = DEFAULT_PROJECT_ID;
        project.id = project.name;
        project.customProperties = new HashMap<>();
        project.customProperties.put(CUSTOM_PROPERTY_PROJECT_INDEX, DEFAULT_PROJECT_INDEX);

        return project;
    }

    /**
     * This class represents the document state associated with a
     * {@link com.vmware.admiral.auth.project.ProjectService}.
     */
    public static class ProjectState extends ResourceState {
        public static final String FIELD_NAME_PUBLIC = "isPublic";
        public static final String FIELD_NAME_DESCRIPTION = "description";
        public static final String FIELD_NAME_ADMINISTRATORS_USER_GROUP_LINKS = "administratorsUserGroupLinks";
        public static final String FIELD_NAME_MEMBERS_USER_GROUP_LINKS = "membersUserGroupLinks";
        public static final String FIELD_NAME_VIEWERS_USER_GROUP_LINKS = "viewersUserGroupLinks";

        @Documentation(description = "Used for define a public project")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public boolean isPublic;

        @Documentation(description = "Used for descripe the purpose of the project")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String description;

        /**
         * Links to the groups of administrators for this project.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_1_2_0)
        @Documentation(description = "Links to the groups of administrators for this project.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public List<String> administratorsUserGroupLinks;

        /**
         * Links to the groups of members for this project.
         */
        @Since(ReleaseConstants.RELEASE_VERSION_1_2_0)
        @Documentation(description = "Links to the groups of members for this project.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public List<String> membersUserGroupLinks;

        /**
         * Links to the groups of viewers for this project.
         */
        @Documentation(description = "Links to the groups of viewers for this project.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public List<String> viewersUserGroupLinks;

        @Deprecated
        @Documentation(description = "Link to the group of administrators for this project.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL, PropertyUsageOption.LINK,
                PropertyUsageOption.SINGLE_ASSIGNMENT, PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String administratorsUserGroupLink;

        @Deprecated
        @Documentation(description = "Link to the group of members for this project.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL, PropertyUsageOption.LINK,
                PropertyUsageOption.SINGLE_ASSIGNMENT, PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String membersUserGroupLink;

        public void copyTo(ProjectState destination) {
            super.copyTo(destination);
            destination.isPublic = this.isPublic;
            destination.description = this.description;

            if (this.administratorsUserGroupLinks != null
                    && !this.administratorsUserGroupLinks.isEmpty()) {
                destination.administratorsUserGroupLinks = new ArrayList<>(
                        this.administratorsUserGroupLinks.size());
                destination.administratorsUserGroupLinks.addAll(this.administratorsUserGroupLinks);
            }

            if (this.membersUserGroupLinks != null
                    && !this.membersUserGroupLinks.isEmpty()) {
                destination.membersUserGroupLinks = new ArrayList<>(
                        this.membersUserGroupLinks.size());
                destination.membersUserGroupLinks.addAll(this.membersUserGroupLinks);
            }

            if (this.viewersUserGroupLinks != null
                    && !this.viewersUserGroupLinks.isEmpty()) {
                destination.viewersUserGroupLinks = new ArrayList<>(
                        this.viewersUserGroupLinks.size());
                destination.viewersUserGroupLinks.addAll(this.viewersUserGroupLinks);
            }
        }

        public static ProjectState copyOf(ProjectState source) {
            if (source == null) {
                return null;
            }

            ProjectState result = new ProjectState();
            source.copyTo(result);
            return result;
        }
    }

    /**
     * This class represents the expanded document state associated with a
     * {@link com.vmware.admiral.auth.project.ProjectService}.
     */
    public static class ExpandedProjectState extends ProjectState {

        /**
         * List of administrators for this project.
         */
        @Documentation(description = "List of administrators for this project.")
        public List<Principal> administrators;

        /**
         * List of members for this project.
         */
        @Documentation(description = "List of members for this project.")
        public List<Principal> members;

        /**
         * List of viewers for this project.
         */
        @Documentation(description = "List of viewers for this project.")
        public List<Principal> viewers;

        /**
         * List of cluster links for this project.
         */
        @Documentation(description = "List of cluster links for this project.")
        public List<String> clusterLinks;

        /**
         * List of repositories in this project.
         */
        @Documentation(description = "List of repositories in this project.")
        public List<String> repositories;

        /**
         * List of template links for this project.
         */
        @Documentation(description = "List of template links for this project.")
        public List<String> templateLinks;

        /**
         * Number of images associated with this project.
         */
        @Documentation(description = "Number of images associated with this project.")
        public Long numberOfImages;

        public void copyTo(ExpandedProjectState destination) {
            super.copyTo(destination);
            if (administrators != null) {
                destination.administrators = new ArrayList<>(administrators);
            }
            if (members != null) {
                destination.members = new ArrayList<>(members);
            }

            if (viewers != null) {
                destination.viewers = new ArrayList<>(viewers);
            }
        }
    }

    public ProjectService() {
        super(ProjectState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation post) {
        if (!checkForBody(post)) {
            return;
        }

        ProjectState createBody = post.getBody(ProjectState.class);
        validateState(createBody);
        createBody.creationTimeMicros = Instant.now().toEpochMilli();

        isProjectNameUsed(createBody.name, createBody.documentSelfLink)
                .whenComplete((isNameUsed, ex) -> {
                    if (ex != null) {
                        logWarning("Error during project name check: %s", Utils.toString(ex));
                        post.fail(ex);
                        return;
                    }
                    if (isNameUsed) {
                        String message = String.format(PROJECT_NAME_ALREADY_USED_MESSAGE,
                                createBody.name);
                        post.fail(new LocalizableValidationException(message,
                                PROJECT_NAME_ALREADY_USED_CODE, createBody.name));
                        return;
                    }
                    generateProjectIndex()
                            .thenApply(index -> handleProjectIndex(index, createBody))
                            .thenCompose(this::createProjectUserGroups)
                            .thenAccept(projectState -> {
                                projectState.membersUserGroupLink = null;
                                projectState.administratorsUserGroupLink = null;

                                if (projectState.tenantLinks == null) {
                                    projectState.tenantLinks = new ArrayList<>();
                                }

                                if (!projectState.tenantLinks.contains(
                                        projectState.documentSelfLink)) {

                                    projectState.tenantLinks.add(projectState.documentSelfLink);
                                }

                                post.setBody(projectState);
                            })
                            .whenCompleteNotify(post);
                });

    }

    @Override
    public void handleGet(Operation get) {
        if (UriUtils.hasODataExpandParamValue(get.getUri())) {
            retrieveExpandedState(getState(get), get);
        } else {
            super.handleGet(get);
        }
    }

    @Override
    public void handlePut(Operation put) {
        if (!checkForBody(put)) {
            return;
        }

        ProjectState currentState = getState(put);
        if (ProjectRolesHandler.isProjectRolesUpdate(put)) {
            ProjectRoles rolesPut = put.getBody(ProjectRoles.class);

            // this is an update of the roles
            new ProjectRolesHandler(this, getSelfLink())
                    .handleRolesUpdate(currentState, rolesPut, put)
                    .whenComplete((ignore, ex) -> {
                        if (ex != null) {
                            if (ex.getCause() instanceof ServiceNotFoundException) {
                                put.fail(Operation.STATUS_CODE_BAD_REQUEST, ex.getCause(),
                                        ex.getCause());
                                return;
                            }
                            put.fail(ex);
                            return;
                        }
                        put.complete();
                    });
        } else {
            // this is an update of the state
            ProjectState projectPut = put.getBody(ProjectState.class);
            validateState(projectPut);
            isProjectNameUsed(projectPut.name, currentState.documentSelfLink)
                    .whenComplete((isNameUsed, e) -> {
                        if (e != null) {
                            logWarning("Error during project name check: %s",
                                    Utils.toString(e));
                            put.fail(e);
                            return;
                        }
                        if (isNameUsed) {
                            String message = String.format(PROJECT_NAME_ALREADY_USED_MESSAGE,
                                    projectPut.name);
                            put.fail(new LocalizableValidationException(message,
                                    PROJECT_NAME_ALREADY_USED_CODE, projectPut.name));
                            return;
                        }
                        handleProjectPut(projectPut, put);
                    });
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!patch.hasBody()) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
            patch.complete();
            return;
        }

        ProjectState projectPatch = patch.getBody(ProjectState.class);
        ProjectState currentState = getState(patch);
        isProjectNameUsed(projectPatch.name, currentState.documentSelfLink)
                .whenComplete((isNameUsed, e) -> {
                    if (e != null) {
                        logWarning("Error during project name check: %s", Utils.toString(e));
                        patch.fail(e);
                        return;
                    }
                    if (isNameUsed) {
                        String message = String.format(PROJECT_NAME_ALREADY_USED_MESSAGE,
                                projectPatch.name);
                        patch.fail(new LocalizableValidationException(message,
                                PROJECT_NAME_ALREADY_USED_CODE, projectPatch.name));
                        return;
                    }

                    DeferredResult<ProjectState> projectDefRes = new DeferredResult<>();

                    if (ProjectRolesHandler.isProjectRolesUpdate(patch)) {
                        projectDefRes = new ProjectRolesHandler(this, getSelfLink())
                                .handleRolesUpdate(currentState, patch.getBody(ProjectRoles.class),
                                        patch);
                    } else {
                        projectDefRes.complete(currentState);
                    }

                    projectDefRes.thenCompose(project -> {
                        return handleProjectPatch(project, projectPatch);
                    }).whenComplete((ignore, ex) -> {
                        if (ex != null) {
                            if (ex.getCause() instanceof ServiceNotFoundException) {
                                patch.fail(Operation.STATUS_CODE_BAD_REQUEST,
                                        ex.getCause(), ex.getCause());
                                return;
                            }
                            patch.fail(ex);
                            return;
                        }

                        patch.complete();
                    });
                });
    }

    /**
     * Returns whether the projects state signature was changed after the patch.
     */
    private DeferredResult<Boolean> handleProjectPatch(ProjectState currentState,
            ProjectState patchState) {
        ServiceDocumentDescription docDesc = getDocumentTemplate().documentDescription;
        String currentSignature = Utils.computeSignature(currentState, docDesc);
        DeferredResult<Long> projectIndex;

        if (currentState.customProperties == null) {
            projectIndex = generateProjectIndex();
        } else {
            projectIndex = DeferredResult.completed(Long.parseLong(
                    currentState.customProperties.get(CUSTOM_PROPERTY_PROJECT_INDEX)));
        }

        return projectIndex.thenApply(index -> {
            Map<String, String> mergedProperties = PropertyUtils.mergeCustomProperties(
                    currentState.customProperties, patchState.customProperties);
            PropertyUtils.mergeServiceDocuments(currentState, patchState);
            currentState.customProperties = mergedProperties;

            handleProjectIndex(index, currentState);
            String newSignature = Utils.computeSignature(currentState, docDesc);
            return !currentSignature.equals(newSignature);
        });
    }

    private void handleProjectPut(ProjectState putState, Operation put) {
        ProjectState currentState = getState(put);
        DeferredResult<Long> projectIndex;
        if (currentState.customProperties == null) {
            projectIndex = generateProjectIndex();
        } else {
            projectIndex = DeferredResult.completed(Long.parseLong(
                    currentState.customProperties.get(CUSTOM_PROPERTY_PROJECT_INDEX)));
        }

        projectIndex.whenComplete((index, ex) -> {
            if (ex != null) {
                put.fail(ex);
                return;
            }
            handleProjectIndex(index, putState);
            setState(put, putState);
            put.setBody(putState);
            put.complete();
        });
    }

    @Override
    public void handleDelete(Operation delete) {
        ProjectState state = getState(delete);
        if (state == null || state.documentSelfLink == null) {
            delete.complete();
            return;
        }

        QueryTask queryTask = ProjectUtil.createQueryTaskForProjectAssociatedWithPlacement(state,
                null);

        Operation getPlacementsWithProject = Operation
                .createPost(getHost(), ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTask);

        sendWithDeferredResult(getPlacementsWithProject, ServiceDocumentQueryResult.class)
                .thenApply(result -> new Pair<>(result, (Throwable) null))
                .exceptionally(ex -> new Pair<>(null, ex))
                .thenCompose(pair -> {
                    if (pair.right != null) {
                        logSevere("Failed to retrieve placements associated with project: %s",
                                state.documentSelfLink);
                        return DeferredResult.failed(pair.right);
                    } else {
                        Long documentCount = pair.left.documentCount;
                        if (documentCount != null && documentCount != 0) {
                            return DeferredResult.failed(new LocalizableValidationException(
                                    ProjectUtil.PROJECT_IN_USE_MESSAGE,
                                    ProjectUtil.PROJECT_IN_USE_MESSAGE_CODE,
                                    documentCount, documentCount > 1 ? "s" : ""));
                        }
                        String projectId = Service.getId(getState(delete).documentSelfLink);
                        return deleteDefaultProjectGroups(projectId, delete)
                                .thenCompose(ignore -> deleteDuplicatedRolesAndResourceGroups(
                                        state));
                    }
                })
                .whenComplete((ignore, ex) -> {
                    if (ex != null) {
                        delete.fail(ex);
                        return;
                    }
                    super.handleDelete(delete);
                });

    }

    private DeferredResult<Void> deleteDefaultProjectGroups(String projectId,
            Operation delete) {

        String adminsUserGroupUri = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_ADMIN.buildRoleWithSuffix(projectId));

        String membersUserGroupUri = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(projectId));

        String viewersUserGroupUri = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_VIEWER.buildRoleWithSuffix(projectId));

        String resourceGroupUri = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                projectId);

        String extendedResourceGroupUri = UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER_EXTENDED.buildRoleWithSuffix(projectId));

        String adminsRoleUri = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_ADMIN.buildRoleWithSuffix(projectId));

        String membersRoleUri = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(projectId));

        String extendedMembersRoleUri = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER_EXTENDED.buildRoleWithSuffix(projectId));

        String viewersRoleUri = UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                AuthRole.PROJECT_VIEWER.buildRoleWithSuffix(projectId));

        return DeferredResult.allOf(
                // First remove groups from user states
                removeProjectDefaultGroupFromUserStates(adminsUserGroupUri, delete.getUri()),
                removeProjectDefaultGroupFromUserStates(membersUserGroupUri, delete.getUri()),
                removeProjectDefaultGroupFromUserStates(viewersUserGroupUri, delete.getUri()))
                .thenCompose((ignore) -> {
                    // Then delete the user groups
                    return DeferredResult.allOf(
                            doDeleteDocument(adminsUserGroupUri, UserGroupState.class,
                                    delete.getUri()),
                            doDeleteDocument(membersUserGroupUri, UserGroupState.class,
                                    delete.getUri()),
                            doDeleteDocument(viewersUserGroupUri, UserGroupState.class,
                                    delete.getUri()));
                }).thenCompose((ignore) -> {
                    // Then delete the resource group
                    return DeferredResult.allOf(
                            doDeleteDocument(resourceGroupUri, ResourceGroupState.class,
                                    delete.getUri()),
                            doDeleteDocument(extendedResourceGroupUri, ResourceGroupState.class,
                                    delete.getUri()));
                }).thenCompose((ignore) -> {
                    // Then delete the groups
                    return DeferredResult.allOf(
                            doDeleteDocument(adminsRoleUri, RoleState.class, delete.getUri()),
                            doDeleteDocument(membersRoleUri, RoleState.class, delete.getUri()),
                            doDeleteDocument(viewersRoleUri, RoleState.class, delete.getUri()),
                            doDeleteDocument(extendedMembersRoleUri, RoleState.class,
                                    delete.getUri()));
                });
    }

    private DeferredResult<Void> removeProjectDefaultGroupFromUserStates(String groupLink,
            URI referer) {

        Operation getAdminsGroup = Operation.createGet(this, groupLink)
                .setReferer(referer);

        return sendWithDeferredResult(getAdminsGroup, UserGroupState.class)
                .exceptionally(ex -> {
                    logWarning("Couldn't get group %s: %s", groupLink, Utils.toString(ex));
                    return null;
                })
                .thenCompose((membersGroupState) -> {
                    if (membersGroupState == null) {
                        return DeferredResult.completed(null);
                    } else {
                        return doRemoveProjectDefaultGroupFromUserStates(membersGroupState);
                    }
                })
                .exceptionally(ex -> {
                    logWarning("Couldn't remove group %s from users: %s", groupLink,
                            Utils.toString(ex));
                    return null;
                });
    }

    private DeferredResult<Void> deleteDuplicatedRolesAndResourceGroups(ProjectState state) {
        String projectId = Service.getId(state.documentSelfLink);
        String defaultAdminsLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_ADMIN.buildRoleWithSuffix(projectId));
        String defaultMembersLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(projectId));
        String defaultViewersLink = UriUtils.buildUriPath(UserGroupService.FACTORY_LINK,
                AuthRole.PROJECT_VIEWER.buildRoleWithSuffix(projectId));

        state.membersUserGroupLinks.remove(defaultMembersLink);
        state.administratorsUserGroupLinks.remove(defaultAdminsLink);
        state.viewersUserGroupLinks.remove(defaultViewersLink);

        List<String> resourcesToRemove = new ArrayList<>();

        // Duplicated roles.
        for (String link : state.viewersUserGroupLinks) {
            resourcesToRemove.add(UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                    AuthRole.PROJECT_VIEWER.buildRoleWithSuffix(projectId, Service.getId(link))));
        }

        for (String link : state.administratorsUserGroupLinks) {
            resourcesToRemove.add(UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                    AuthRole.PROJECT_ADMIN.buildRoleWithSuffix(projectId, Service.getId(link))));
        }

        for (String link : state.membersUserGroupLinks) {
            resourcesToRemove.add(UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                    AuthRole.PROJECT_MEMBER.buildRoleWithSuffix(projectId, Service.getId(link))));
            resourcesToRemove.add(UriUtils.buildUriPath(RoleService.FACTORY_LINK,
                    AuthRole.PROJECT_MEMBER_EXTENDED.buildRoleWithSuffix(projectId,
                            Service.getId(link))));
            // Duplicated resource groups for extended members.
            resourcesToRemove.add(UriUtils.buildUriPath(ResourceGroupService.FACTORY_LINK,
                    AuthRole.PROJECT_MEMBER_EXTENDED.buildRoleWithSuffix(projectId,
                            Service.getId(link))));
        }

        List<DeferredResult<Operation>> results = new ArrayList<>();

        for (String link : resourcesToRemove) {
            Operation delete = Operation.createDelete(this, link)
                    .setReferer(this.getUri());
            results.add(sendWithDeferredResult(delete)
                    .exceptionally(ex -> {
                        logWarning("Error when deleting project resource %s: %s",
                                link, Utils.toString(ex));
                        return null;
                    }));
        }

        return DeferredResult.allOf(results).thenAccept(ignore -> {
        });
    }

    private <T> DeferredResult<T> doDeleteDocument(String groupLink, Class<T> documentClass,
            URI referer) {
        return sendWithDeferredResult(Operation.createDelete(this, groupLink)
                .setReferer(referer), documentClass)
                        .exceptionally(ex -> {
                            logWarning(
                                    "Couldn't delete document of type %s with document link %s: %s",
                                    documentClass.getName(), groupLink, Utils.toString(ex));
                            return null;
                        });
    }

    private DeferredResult<Void> doRemoveProjectDefaultGroupFromUserStates(
            UserGroupState groupState) {
        if (groupState == null) {
            return DeferredResult.completed(null);
        }
        return ProjectUtil.retrieveUserStatesForGroup(this, groupState)
                .thenCompose(userStates -> {
                    List<String> userLinks = userStates.stream()
                            .map(us -> Service.getId(us.documentSelfLink))
                            .collect(Collectors.toList());
                    return UserGroupsUpdater.create()
                            .setService(this)
                            .setGroupLink(groupState.documentSelfLink)
                            .setUsersToRemove(userLinks)
                            .update();
                });
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ProjectState template = (ProjectState) super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(template);

        template.name = "resource-group-1";
        template.id = "project-id";
        template.description = "project1";
        template.isPublic = true;
        template.viewersUserGroupLinks = Collections.singletonList("viewers-group");
        template.membersUserGroupLinks = Collections.singletonList("members-group");
        template.administratorsUserGroupLinks = Collections.singletonList("admins-group");

        return template;
    }

    private void retrieveExpandedState(ProjectState simpleState, Operation get) {
        ProjectUtil.expandProjectState(this, get, simpleState, getUri())
                .thenAccept((expandedState) -> get.setBody(expandedState))
                .whenCompleteNotify(get);
    }

    private void validateState(ProjectState state) {
        Utils.validateState(getStateDescription(), state);
        AssertUtil.assertNotNullOrEmpty(state.name, ProjectState.FIELD_NAME_NAME);
    }

    private DeferredResult<ProjectState> createProjectUserGroups(ProjectState projectState) {

        String projectId = Service.getId(projectState.documentSelfLink);
        UserGroupState membersGroupState = AuthUtil.buildProjectMembersUserGroup(projectId);
        UserGroupState adminsGroupState = AuthUtil.buildProjectAdminsUserGroup(projectId);
        UserGroupState viewersGroupState = AuthUtil.buildProjectViewersUserGroup(projectId);

        if (projectState.administratorsUserGroupLinks != null
                && projectState.membersUserGroupLinks != null
                && projectState.viewersUserGroupLinks != null
                && projectState.administratorsUserGroupLinks.contains(
                        adminsGroupState.documentSelfLink)
                && projectState.membersUserGroupLinks.contains(
                        membersGroupState.documentSelfLink)
                && projectState.viewersUserGroupLinks.contains(
                        viewersGroupState.documentSelfLink)) {
            // No groups to create
            return DeferredResult.completed(projectState);
        }

        if (projectState.administratorsUserGroupLinks == null) {
            projectState.administratorsUserGroupLinks = new ArrayList<>();
        }
        if (projectState.membersUserGroupLinks == null) {
            projectState.membersUserGroupLinks = new ArrayList<>();
        }
        if (projectState.viewersUserGroupLinks == null) {
            projectState.viewersUserGroupLinks = new ArrayList<>();
        }

        return DeferredResult.allOf(
                createProjectUserGroup(projectState.administratorsUserGroupLinks, adminsGroupState),
                createProjectUserGroup(projectState.membersUserGroupLinks, membersGroupState),
                createProjectUserGroup(projectState.viewersUserGroupLinks, viewersGroupState))
                .thenCompose((ignore) ->
        // Project Admins/Members/Viewers use the same resource group.
        // We need to create additional one for Members extended role only.
        createProjectResourceGroup(projectState, AuthRole.PROJECT_ADMIN))
                .thenCompose((resourceGroup) -> DeferredResult.allOf(
                        createProjectAdminRole(projectState, resourceGroup.documentSelfLink,
                                adminsGroupState.documentSelfLink),
                        createProjectMemberRole(projectState, resourceGroup.documentSelfLink,
                                membersGroupState.documentSelfLink),
                        createProjectViewerRole(projectState, resourceGroup.documentSelfLink,
                                viewersGroupState.documentSelfLink)))
                .thenCompose((ignore) -> createProjectResourceGroup(projectState,
                        AuthRole.PROJECT_MEMBER_EXTENDED))
                .thenCompose(resourceGroup -> createProjectExtendedMemberRole(projectState,
                        resourceGroup.documentSelfLink, membersGroupState.documentSelfLink))
                .thenApply(ignore -> projectState);
    }

    private DeferredResult<Void> createProjectUserGroup(List<String> addTo,
            UserGroupState groupState) {
        AssertUtil.assertNotNull(addTo, "addTo");
        AssertUtil.assertNotNull(groupState, "groupState");

        if (addTo.contains(groupState.documentSelfLink)) {
            return DeferredResult.completed(null);
        }

        return getHost().sendWithDeferredResult(buildCreateUserGroupOperation(groupState),
                UserGroupState.class)
                .thenAccept((createdState) -> addTo.add(createdState.documentSelfLink));

    }

    private DeferredResult<ResourceGroupState> createProjectResourceGroup(ProjectState projectState,
            AuthRole role) {
        String projectId = Service.getId(projectState.documentSelfLink);
        ResourceGroupState resourceGroupState;

        if (role.equals(AuthRole.PROJECT_MEMBER_EXTENDED)) {
            resourceGroupState = AuthUtil.buildProjectExtendedMemberResourceGroup(projectId);
        } else {
            resourceGroupState = AuthUtil.buildProjectResourceGroup(projectId);
        }

        return getHost().sendWithDeferredResult(
                buildCreateResourceGroupOperation(resourceGroupState), ResourceGroupState.class);
    }

    private DeferredResult<RoleState> createProjectAdminRole(ProjectState projectState,
            String resourceGroupLink, String userGroupLink) {
        String projectId = Service.getId(projectState.documentSelfLink);

        RoleState projectAdminRoleState = AuthUtil.buildProjectAdminsRole(projectId,
                userGroupLink, resourceGroupLink);

        return getHost().sendWithDeferredResult(
                buildCreateRoleOperation(projectAdminRoleState), RoleState.class);

    }

    private DeferredResult<RoleState> createProjectMemberRole(ProjectState projectState,
            String resourceGroupLink, String userGroupLink) {
        String projectId = Service.getId(projectState.documentSelfLink);

        RoleState projectMemberRoleState = AuthUtil.buildProjectMembersRole(projectId,
                userGroupLink, resourceGroupLink);

        return getHost().sendWithDeferredResult(
                buildCreateRoleOperation(projectMemberRoleState), RoleState.class);
    }

    private DeferredResult<RoleState> createProjectExtendedMemberRole(ProjectState projectState,
            String resourceGroupLink, String userGroupLink) {
        String projectId = Service.getId(projectState.documentSelfLink);

        RoleState projectMemberRoleState = AuthUtil.buildProjectExtendedMembersRole(projectId,
                userGroupLink, resourceGroupLink);

        return getHost().sendWithDeferredResult(
                buildCreateRoleOperation(projectMemberRoleState), RoleState.class);
    }

    private DeferredResult<RoleState> createProjectViewerRole(ProjectState projectState,
            String resourceGroupLink, String userGroupLink) {
        String projectId = Service.getId(projectState.documentSelfLink);

        RoleState projectViewerRoleState = AuthUtil.buildProjectViewersRole(projectId,
                userGroupLink, resourceGroupLink);

        return getHost().sendWithDeferredResult(
                buildCreateRoleOperation(projectViewerRoleState), RoleState.class);
    }

    private Operation buildCreateUserGroupOperation(UserGroupState state) {
        return Operation.createPost(getHost(), UserGroupService.FACTORY_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setReferer(getHost().getUri())
                .setBody(state);
    }

    private Operation buildCreateResourceGroupOperation(ResourceGroupState state) {
        return Operation.createPost(getHost(), ResourceGroupService.FACTORY_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setReferer(getHost().getUri())
                .setBody(state);
    }

    private Operation buildCreateRoleOperation(RoleState state) {
        return Operation.createPost(getHost(), RoleService.FACTORY_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setReferer(getHost().getUri())
                .setBody(state);
    }

    private DeferredResult<Long> generateProjectIndex() {
        long random = ProjectUtil.generateRandomUnsignedInt();

        return isProjectIndexUsed(random)
                .thenCompose(isUsed -> {
                    if (!isUsed) {
                        return DeferredResult.completed(random);
                    }
                    return generateProjectIndex();
                });
    }

    private DeferredResult<Boolean> isProjectNameUsed(String name, String currentStateSelfLink) {
        if (name == null || name.isEmpty()) {
            return DeferredResult.completed(false);
        }
        DeferredResult<Boolean> result = new DeferredResult<>();

        List<ProjectState> foundProjects = new ArrayList<>();

        Query query = ProjectUtil.buildQueryForProjectsFromName(name, currentStateSelfLink);

        QueryTask queryTask = QueryUtil.buildQuery(ProjectState.class, true, query);
        QueryUtil.addExpandOption(queryTask);

        new ServiceDocumentQuery<>(getHost(), ProjectState.class).query(queryTask, (r) -> {
            if (r.hasException()) {
                result.fail(r.getException());
            } else if (r.hasResult()) {
                foundProjects.add(r.getResult());
            } else {
                if (foundProjects.isEmpty()) {
                    result.complete(false);
                } else {
                    result.complete(true);
                }
            }
        });

        return result;
    }

    private DeferredResult<Boolean> isProjectIndexUsed(long random) {

        DeferredResult<Boolean> result = new DeferredResult<>();

        List<ProjectState> foundProjects = new ArrayList<>();

        Query query = ProjectUtil.buildQueryForProjectsFromProjectIndex(random);

        QueryTask queryTask = QueryUtil.buildQuery(ProjectState.class, true, query);
        QueryUtil.addExpandOption(queryTask);

        new ServiceDocumentQuery<>(getHost(), ProjectState.class).query(queryTask, (r) -> {
            if (r.hasException()) {
                result.fail(r.getException());
            } else if (r.hasResult()) {
                foundProjects.add(r.getResult());
            } else {
                if (foundProjects.isEmpty()) {
                    result.complete(false);
                } else {
                    result.complete(true);
                }
            }
        });

        return result;
    }

    private ProjectState handleProjectIndex(long projectIndex, ProjectState state) {
        if (state.customProperties == null) {
            state.customProperties = new HashMap<>();
        }

        // In case it's the default project do not override the index.
        if (state.customProperties.containsKey(CUSTOM_PROPERTY_PROJECT_INDEX)
                && state.customProperties.get(CUSTOM_PROPERTY_PROJECT_INDEX)
                        .equalsIgnoreCase(DEFAULT_PROJECT_INDEX)) {
            return state;
        }
        state.customProperties.put(CUSTOM_PROPERTY_PROJECT_INDEX,
                Long.toString(projectIndex));
        return state;
    }
}
