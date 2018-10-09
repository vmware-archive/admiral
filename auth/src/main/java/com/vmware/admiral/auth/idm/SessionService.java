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

package com.vmware.admiral.auth.idm;

import java.util.Collections;

import com.vmware.admiral.auth.util.AuthUtil;
import com.vmware.admiral.auth.util.SecurityContextUtil;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AuthUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

public class SessionService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.AUTH_SESSION;

    private static final SecurityContext NO_AUTH_SECURITY_CONTEXT = new SecurityContext();

    static {
        NO_AUTH_SECURITY_CONTEXT.roles = Collections.singleton(AuthRole.CLOUD_ADMIN);
    }

    private LogoutProvider provider;

    public SessionService() {
        super();
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    public void handleStart(Operation startPost) {
        provider = AuthUtil.getPreferredLogoutProvider();
        provider.init(this);
        startPost.complete();
    }

    @Override
    public void handleGet(Operation get) {
        if (isLogoutRequest(get)) {
            if (!AuthUtil.isAuthxEnabled(getHost())) {
                return;
            }
            provider.doLogout(get);
        } else if (isSessionRequest(get)) {
            if (!AuthUtil.isAuthxEnabled(getHost())) {
                get.setBody(NO_AUTH_SECURITY_CONTEXT).complete();
                return;
            }
            SecurityContextUtil.getSecurityContext(this, get)
                    .thenAccept((context) -> {
                        get.setBody(context).complete();
                    })
                    .exceptionally((ex) -> {
                        if (ex.getCause() instanceof ServiceNotFoundException) {
                            logWarning("Failed to retrieve session for current user!");
                            // Clean-up auth token header and cookie
                            AuthUtils.cleanupSessionData(get);
                            get.setStatusCode(Operation.STATUS_CODE_UNAUTHORIZED).complete();
                        } else {
                            logWarning("Failed to retrieve session for current user: %s",
                                    Utils.toString(ex));
                            get.fail(ex);
                        }
                        return null;
                    });
        } else {
            Operation.failServiceNotFound(get);
        }
    }

    private boolean isLogoutRequest(Operation op) {
        return ManagementUriParts.AUTH_LOGOUT.equalsIgnoreCase(op.getUri().getPath());
    }

    private boolean isSessionRequest(Operation op) {
        return SELF_LINK.equalsIgnoreCase(op.getUri().getPath());
    }

}
