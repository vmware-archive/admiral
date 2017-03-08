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
import java.util.function.Consumer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.UserGroupService;
import com.vmware.xenon.services.common.UserGroupService.UserGroupState;
import com.vmware.xenon.services.common.UserService.UserState;

/**
 * Project is a group sharing same resources.
 */
public class ProjectService extends StatefulService {

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
        createAdminAndMemberGroups(post);
        // Operation will be completed after a successful creation of the groups
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
    public ServiceDocument getDocumentTemplate() {
        ProjectState template = (ProjectState) super.getDocumentTemplate();
        template.name = "resource-group-1";
        template.description = "project1";
        template.isPublic = true;

        return template;
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
