/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.auth.idm.local;

import java.util.logging.Level;

import com.vmware.admiral.auth.idm.LogoutProvider;
import com.vmware.admiral.common.util.AuthUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.authn.AuthenticationRequest;
import com.vmware.xenon.services.common.authn.AuthenticationRequest.AuthenticationRequestType;
import com.vmware.xenon.services.common.authn.BasicAuthenticationService;

public class LocalLogoutProvider implements LogoutProvider {

    private Service service;

    @Override
    public void init(Service service) {
        this.service = service;
    }

    @Override
    public void doLogout(Operation op) {

        AuthenticationRequest logout = new AuthenticationRequest();
        logout.requestType = AuthenticationRequestType.LOGOUT;

        service.sendRequest(Operation.createPost(service, BasicAuthenticationService.SELF_LINK)
                .setBody(logout)
                .forceRemote()
                .setCompletion((o, e) -> {
                    if (e != null) {
                        service.getHost().log(Level.SEVERE, "Logout failed: %s", Utils.toString(e));
                        op.fail(e);
                        return;
                    }
                    // clears auth token and cookie
                    AuthUtils.cleanupSessionData(op);
                    op.complete();
                }));
    }

}
