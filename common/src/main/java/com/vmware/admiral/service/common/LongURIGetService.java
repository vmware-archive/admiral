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

package com.vmware.admiral.service.common;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

/**
 * The service accepts post request with long uri as body. Then a get request is sent using the uri
 * in the body. The service workarounds an issue when the uri is too long and netty does not accept
 * it
 */
public class LongURIGetService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.LONG_URI_GET;

    public static class LongURIRequest {
        public String uri;
    }

    @Override
    public void authorizeRequest(Operation op) {
        op.complete();
    }

    @Override
    public void handlePost(Operation post) {
        LongURIRequest body = post.getBody(LongURIRequest.class);
        sendRequest(Operation.createGet(this, body.uri).setCompletion((o, e) -> {
            post.setBodyNoCloning(o.getBodyRaw());
            post.setStatusCode(o.getStatusCode());
            post.transferResponseHeadersFrom(o);
            if (e != null) {
                post.fail(e);
            } else {
                post.complete();
            }
        }));
    }
}
