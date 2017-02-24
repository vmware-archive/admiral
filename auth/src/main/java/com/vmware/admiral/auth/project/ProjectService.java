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

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * Project is a group sharing same resources.
 */
public class ProjectService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.PROJECTS;

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

        super.handleCreate(post);
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
        AssertUtil.assertNotNullOrEmpty(state.name, ProjectState.FIELD_NAME_NAME);
    }
}
