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

package com.vmware.admiral.service.common;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class MockPingService extends StatelessService {

    public static final String SELF_LINK = "/ping";

    public static final String BODY_PING = "{ \"body\" : \"ping\" }";
    public static final String BODY_PONG = "{ \"body\" : \"pong\" }";

    public MockPingService() {
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    public void handleGet(Operation get) {
        setResponseAndComplete(get);
    }

    @Override
    public void handlePost(Operation post) {
        setResponseAndComplete(post);
    }

    @Override
    public void handlePatch(Operation patch) {
        setResponseAndComplete(patch);
    }

    @Override
    public void handlePut(Operation put) {
        setResponseAndComplete(put);
    }

    @Override
    public void handleDelete(Operation delete) {
        setResponseAndComplete(delete);
    }

    @Override
    public void handleOptions(Operation options) {
        setResponseAndComplete(options);
    }

    private void setResponseAndComplete(Operation op) {
        op.setBody("{}");
        op.setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON);
        op.setStatusCode(Operation.STATUS_CODE_OK);
        op.complete();
    }

}
