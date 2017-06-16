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

package com.vmware.admiral.auth.idm.local;

import java.util.logging.Level;

import com.vmware.admiral.auth.idm.LogoutProvider;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.authn.AuthenticationRequest;
import com.vmware.xenon.services.common.authn.AuthenticationRequest.AuthenticationRequestType;
import com.vmware.xenon.services.common.authn.BasicAuthenticationService;

public class LocalLogoutProvider implements LogoutProvider {

    private ServiceHost host;

    public void setServiceHost(ServiceHost host) {
        this.host = host;
    }

    @Override
    public void doLogout(Operation op) {

        AuthenticationRequest logout = new AuthenticationRequest();
        logout.requestType = AuthenticationRequestType.LOGOUT;

        host.sendRequest(Operation.createPost(host, BasicAuthenticationService.SELF_LINK)
                .setBody(logout)
                .setReferer(host.getUri())
                .forceRemote()
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.SEVERE, "Logout failed: %s", Utils.toString(ex));
                        op.fail(ex);
                        return;
                    }
                    op.transferResponseHeadersFrom(o);
                    op.setStatusCode(Operation.STATUS_CODE_UNAUTHORIZED);
                    op.complete();
                }));
    }

}
