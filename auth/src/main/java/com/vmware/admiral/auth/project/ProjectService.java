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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.vmware.admiral.auth.util.ProjectUtil;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;
import com.vmware.xenon.services.common.UserService.UserState;

/**
 * Project is a group sharing same resources.
 */
public class ProjectService extends StatefulService {

    private static final String FAILED_TO_RETRIEVE_STATE_WITH_MEMBERS_MESSAGE_FORMAT =
            "Failed to retrieve project state with members for project %s";
    private static final String FAILED_TO_RETRIEVE_STATE_WITH_MEMBERS_MESSAGE_CODE =
            "auth.project.retrieve.state.with.members.failed";

    public static final String FACTORY_LINK = ManagementUriParts.PROJECTS;
    public static final String DEFAULT_PROJECT_ID = "default-project";
    public static final String DEFAULT_PROJECT_LINK = UriUtils.buildUriPath(
            ProjectService.FACTORY_LINK, DEFAULT_PROJECT_ID);

    public static ProjectState buildDefaultProjectInstance() {
        ProjectState project = new ProjectState();
        project.documentSelfLink = DEFAULT_PROJECT_LINK;
        project.name = DEFAULT_PROJECT_ID;
        project.id = project.name;

        return project;
    }

    /**
     * This class represents the document state associated with a
     * {@link com.vmware.admiral.auth.project.ProjectService}.
     */
    public static class ProjectState extends ResourceState {
        public static final String FIELD_NAME_PUBLIC = "isPublic";
        public static final String FIELD_NAME_DESCRIPTION = "description";

        @Documentation(description = "Used for define a public project")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public boolean isPublic;

        @Documentation(description = "Used for descripe the purpose of the project")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String description;

        /** Link to the group of administrators for this project. */
        @Documentation(description = "Link to the group of administrators for this project.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL, PropertyUsageOption.LINK,
                PropertyUsageOption.SINGLE_ASSIGNMENT, PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String administratorsUserGroupLink;

        /** Link to the group of members for this project. */
        @Documentation(description = "Link to the group of members for this project.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL, PropertyUsageOption.LINK,
                PropertyUsageOption.SINGLE_ASSIGNMENT, PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String membersUserGroupLink;
    }

    public static class ProjectStateWithMembers extends ProjectState {

        /** List of administrators for this project. */
        @Documentation(description = "List of administrators for this project.")
        public List<UserState> administrators;

        /** List of members for this project. */
        @Documentation(description = "List of members for this project.")
        public List<UserState> members;

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

        if (isDevOpsAdmin(post)) {
            createAdminAndMemberGroups(post);
            // Operation will be completed after a successful creation of the groups
        } else {
            post.complete();
        }
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

        ProjectState putBody = put.getBody(ProjectState.class);
        validateState(putBody);

        this.setState(put, putBody);
        put.setBody(putBody).complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        ProjectState currentState = getState(patch);
        ProjectState patchBody = patch.getBody(ProjectState.class);

        ServiceDocumentDescription docDesc = getDocumentTemplate().documentDescription;
        String currentSignature = Utils.computeSignature(currentState, docDesc);

        PropertyUtils.mergeServiceDocuments(currentState, patchBody);

        String newSignature = Utils.computeSignature(currentState, docDesc);

        // if the signature hasn't change we shouldn't modify the state
        if (currentSignature.equals(newSignature)) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        }

        patch.complete();
    }

    @Override
    public void handleDelete(Operation delete) {
        ProjectState state = getState(delete);
        if (state == null || state.documentSelfLink == null) {
            delete.complete();
            return;
        }

        QueryTask queryTask = ProjectUtil.createQueryTaskForProjectAssociatedWithPlacement(state, null);


        sendRequest(Operation.createPost(getHost(), ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Failed to retrieve placements associated with project: " + state.documentSelfLink);
                        delete.fail(e);
                        return;
                    } else {
                        ServiceDocumentQueryResult result = o.getBody(QueryTask.class).results;
                        long documentCount = result.documentCount;
                        if (documentCount != 0) {
                            delete.fail(new LocalizableValidationException(
                                    ProjectUtil.PROJECT_IN_USE_MESSAGE,
                                    ProjectUtil.PROJECT_IN_USE_MESSAGE_CODE,
                                    documentCount, documentCount > 1 ? "s" : ""));
                            return;
                        }

                        super.handleDelete(delete);
                    }
                }));
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ProjectState template = (ProjectState) super.getDocumentTemplate();
        template.name = "resource-group-1";
        template.id = "project-id";
        template.description = "project1";
        template.isPublic = true;

        return template;
    }

    private boolean isDevOpsAdmin(Operation op) {
        // TODO extract this to a common utility
        return !OperationUtil.isGuestUser(op);
    }

    /**
     * Creates a {@link ProjectStateWithMembers} based on the current state of the service by
     * additionally building the lists of administrators and members. When done, the prepared
     * expanded state will be set as body for the provided <code>get</code> {@link Operation} and it
     * will be completed.
     */
    private void retrieveExpandedState(ProjectState simpleState, Operation get) {
        ProjectStateWithMembers expandedState = new ProjectStateWithMembers();
        simpleState.copyTo(expandedState);
        expandedState.isPublic = simpleState.isPublic;
        expandedState.description = simpleState.description;
        expandedState.administratorsUserGroupLink = simpleState.administratorsUserGroupLink;
        expandedState.membersUserGroupLink = simpleState.membersUserGroupLink;

        Operation adminsOp = buildUsersRetrievalHelperOperation((adminsList) -> {
            expandedState.administrators = adminsList;
        });
        Operation membersOp = buildUsersRetrievalHelperOperation((membersList) -> {
            expandedState.members = membersList;
        });
        OperationJoin.create(adminsOp, membersOp).setCompletion((ops, exs) -> {
            if (exs != null && !exs.isEmpty()) {
                String error = String.format(FAILED_TO_RETRIEVE_STATE_WITH_MEMBERS_MESSAGE_FORMAT,
                        simpleState.documentSelfLink);
                get.fail(new LocalizableValidationException(exs.values().iterator().next(), error,
                        FAILED_TO_RETRIEVE_STATE_WITH_MEMBERS_MESSAGE_CODE,
                        simpleState.documentSelfLink));
            } else {
                get.setBody(expandedState).complete();
            }
        });

        retrieveUserGroupMembers(simpleState.administratorsUserGroupLink, adminsOp);
        retrieveUserGroupMembers(simpleState.membersUserGroupLink, membersOp);
    }

    /**
     * Creates a helper {@link Operation} that is supposed to be completed when the {@link List} of
     * members of a specific user group has been populated. On successful completion, the operation
     * body is expected to be the list of {@link UserState}s
     */
    @SuppressWarnings("unchecked")
    private Operation buildUsersRetrievalHelperOperation(
            Consumer<List<UserState>> successHandler) {

        Operation result = new Operation();
        result.setCompletion((o, e) -> {
            if (e != null) {
                result.fail(e);
            } else {
                // o.getRawBody() is used instead of o.getBody(List.class) to avoid
                // converting the list of UserState-s to list of LinkedTreeMap-s
                successHandler.accept((List<UserState>) o.getBodyRaw());
            }
        });
        return result;
    }

    /**
     * Retrieves the list of members for the specified by document link user group, sets that list
     * as a body for the provided operation and completes it. If <code>groupLink</code> is
     * <code>null</code>, an empty list will be returned in the <code>callerOp</code> body.
     *
     * @see #retrieveUserStatesForGroup(UserGroupState, Operation)
     */
    private void retrieveUserGroupMembers(String groupLink, Operation callerOp) {
        if (groupLink == null) {
            callerOp.setBody(new ArrayList<>(0)).complete();
            return;
        }

        Operation.createGet(getHost(), groupLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Failed to retrieve UserGroupState %s: %s", groupLink,
                                Utils.toString(e));
                        callerOp.fail(e);
                        return;
                    }

                    UserGroupState groupState = o.getBody(UserGroupState.class);
                    retrieveUserStatesForGroup(groupState, callerOp);
                }).sendWith(this);
    }

    /**
     * Retrieves the list of members for the specified user group, sets that list as a body for the
     * provided operation and completes it.
     *
     * @see #retrieveUserStatesForGroup(UserGroupState, Operation)
     */
    private void retrieveUserStatesForGroup(UserGroupState groupState, Operation callerOp) {
        ArrayList<UserState> resultList = new ArrayList<>();

        QueryTask queryTask = QueryUtil.buildQuery(UserState.class, true, groupState.query);
        QueryUtil.addExpandOption(queryTask);
        new ServiceDocumentQuery<UserState>(getHost(), UserState.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        logWarning("Failed to retrieve members of UserGroupState %s: %s",
                                groupState.documentSelfLink, Utils.toString(r.getException()));
                        callerOp.fail(r.getException());
                    } else if (r.hasResult()) {
                        resultList.add(r.getResult());
                    } else {
                        callerOp.setBody(resultList).complete();
                    }
                });
    }

    private void validateState(ProjectState state) {
        Utils.validateState(getStateDescription(), state);
        AssertUtil.assertNotNullOrEmpty(state.name, ProjectState.FIELD_NAME_NAME);
    }

    private void createAdminAndMemberGroups(Operation createPost) {
        ProjectState createBody = createPost.getBody(ProjectState.class);
        if (createBody.administratorsUserGroupLink != null
                && createBody.membersUserGroupLink != null
                && !createBody.administratorsUserGroupLink.trim().isEmpty()
                && !createBody.membersUserGroupLink.trim().isEmpty()) {
            // No groups to create, complete the operation
            createPost.complete();
            return;
        }

        // Prepare operations to create only the missing UserGroup-s
        ArrayList<Operation> operations = new ArrayList<>(2);
        Query defaultQuery = createDefaultUserGroupQuery(createPost);
        if (createBody.administratorsUserGroupLink == null
                || createBody.administratorsUserGroupLink.trim().isEmpty()) {
            Operation createAdminsGroup = buildCreateUserGroupOperation(defaultQuery,
                    (userState) -> {
                        createBody.administratorsUserGroupLink = userState.documentSelfLink;
                    });
            operations.add(createAdminsGroup);
        }
        if (createBody.membersUserGroupLink == null
                || createBody.membersUserGroupLink.trim().isEmpty()) {
            Operation createMembersGroup = buildCreateUserGroupOperation(defaultQuery,
                    (userState) -> {
                        createBody.membersUserGroupLink = userState.documentSelfLink;
                    });
            operations.add(createMembersGroup);
        }

        // Execute the prepared operations and complete the createPost on success
        OperationJoin.create(operations)
                .setCompletion((ops, ers) -> {
                    if (ers != null && ers.size() > 0) {
                        createPost.fail(ers.values().iterator().next());
                    } else {
                        createPost.complete();
                    }
                }).sendWith(getHost());
    }

    private Operation buildCreateUserGroupOperation(Query userGroupQuery,
            Consumer<UserState> successHandler) {
        UserGroupState userGroupState = UserGroupState.Builder
                .create()
                .withQuery(userGroupQuery)
                .build();

        return Operation.createPost(getHost(), UserGroupService.FACTORY_LINK)
                .setReferer(getHost().getUri())
                .setBody(userGroupState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(Utils.toString(e));
                        return;
                        // error will be handled in joined completion handler
                    }
                    if (successHandler != null) {
                        successHandler.accept(o.getBody(UserState.class));
                    }
                });
    }

    private Query createDefaultUserGroupQuery(Operation callerOp) {
        String userLink = callerOp.getAuthorizationContext().getClaims().getSubject();
        return QueryUtil.buildPropertyQuery(UserState.class, UserState.FIELD_NAME_SELF_LINK,
                userLink).querySpec.query;
    }
}
