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

package com.vmware.admiral.auth;

import static com.vmware.admiral.auth.util.AuthUtil.buildBasicUsersResourceGroup;
import static com.vmware.admiral.auth.util.AuthUtil.buildBasicUsersResourceGroupExtended;
import static com.vmware.admiral.auth.util.AuthUtil.buildBasicUsersRole;
import static com.vmware.admiral.auth.util.AuthUtil.buildBasicUsersRoleExtended;
import static com.vmware.admiral.auth.util.AuthUtil.buildCloudAdminsResourceGroup;
import static com.vmware.admiral.auth.util.AuthUtil.buildCloudAdminsRole;
import static com.vmware.admiral.auth.util.AuthUtil.buildEmptyBasicUsersUserGroup;
import static com.vmware.admiral.auth.util.AuthUtil.buildEmptyCloudAdminsUserGroup;

import com.vmware.admiral.auth.project.ProjectService;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.service.common.AbstractInitialBootService;
import com.vmware.xenon.common.Operation;

/**
 * Initial boot service for creating system default documents for the auth module.
 */
public class AuthInitialBootService extends AbstractInitialBootService {
    public static final String SELF_LINK = ManagementUriParts.CONFIG + "/auth-initial-boot";

    @Override
    public void handlePost(Operation post) {
        logInfo("Creating user groups.");
        initInstances(post,
                ProjectService.buildDefaultProjectInstance(),
                //Initialize Cloud Admins global role.
                buildEmptyCloudAdminsUserGroup(),
                buildCloudAdminsResourceGroup(),
                buildCloudAdminsRole(),
                //Initialize Basic Users global role.
                buildEmptyBasicUsersUserGroup(),
                buildBasicUsersResourceGroup(),
                buildBasicUsersRole(),
                //Initialize Basic Users Extended role.
                buildBasicUsersResourceGroupExtended(),
                buildBasicUsersRoleExtended());
    }

}