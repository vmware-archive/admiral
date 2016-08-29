/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral;

import java.net.HttpURLConnection;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.GuestUserService;
import com.vmware.xenon.services.common.SystemUserService;

/**
 * Service that returns the current logged in user session, ignoring system and guest user.
 */
public class UserSessionService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.USER_SESSION_SERVICE;

    public UserSessionService() {
        super();
    }

    @Override
    public void handleGet(Operation get) {
        AuthorizationContext ctx = get.getAuthorizationContext();
        if (ctx == null) {
            get.setStatusCode(HttpURLConnection.HTTP_UNAUTHORIZED);
            get.complete();
            return;
        }

        String subject = ctx.getClaims().getSubject();
        if (subject == null || subject.equals(GuestUserService.SELF_LINK)
                || subject.equals(SystemUserService.SELF_LINK)) {
            get.setStatusCode(HttpURLConnection.HTTP_UNAUTHORIZED);
            get.complete();
            return;
        }

        sendRequest(Operation.createGet(this, subject).setCompletion((o, e) -> {
            if (e != null) {
                get.fail(e);
                return;
            }

            get.setBody(o.getBodyRaw());
            get.complete();
        }));
    }
}
