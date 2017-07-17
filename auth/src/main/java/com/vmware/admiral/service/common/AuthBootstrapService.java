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

import java.util.logging.Level;

import com.vmware.admiral.auth.idm.AuthConfigProvider;
import com.vmware.admiral.auth.idm.local.LocalAuthConfigProvider;
import com.vmware.admiral.auth.util.AuthUtil;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

/**
 * One-time node group setup (bootstrap) for the auth settings.
 *
 * This service is guaranteed to be performed only once within entire node group, in a consistent
 * safe way. Durable for restarting the owner node or even complete shutdown and restarting of all
 * nodes. Following the SampleBootstrapService.
 */
public class AuthBootstrapService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.CONFIG + "/auth-bootstrap";

    public static FactoryService createFactory() {
        return FactoryService.create(AuthBootstrapService.class);
    }

    public static CompletionHandler startTask(ServiceHost host) {
        return (o, e) -> {
            if (e != null) {
                host.log(Level.SEVERE, Utils.toString(e));
                return;
            }
            // create service with fixed link
            // POST will be issued multiple times but will be converted to PUT after the first one.
            ServiceDocument doc = new ServiceDocument();
            doc.documentSelfLink = "auth-preparation-task";
            Operation.createPost(host, AuthBootstrapService.FACTORY_LINK)
                    .setBody(doc)
                    .setReferer(host.getUri())
                    .setCompletion((oo, ee) -> {
                        if (ee != null) {
                            host.log(Level.SEVERE, Utils.toString(ee));
                            return;
                        }
                        host.log(Level.INFO, "auth-preparation-task triggered");
                    })
                    .sendWith(host);
        };
    }

    public AuthBootstrapService() {
        super(ServiceDocument.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation post) {
        getHost().log(Level.INFO, "handleStart");

        AuthConfigProvider provider = AuthUtil.getPreferredProvider(AuthConfigProvider.class);

        // TODO - Refactor: the LocalAuthConfigProvider should be initialized only once whereas the
        // PSC should be initialized every time...
        if (!ServiceHost.isServiceCreate(post) && (provider instanceof LocalAuthConfigProvider)) {
            // do not perform bootstrap logic when the post is NOT from direct client, eg: node
            // restart
            post.complete();
            return;
        }

        provider.initConfig(getHost(), post);
    }

    @Override
    public void handlePut(Operation put) {
        getHost().log(Level.INFO, "handlePut");

        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            // converted PUT due to IDEMPOTENT_POST option
            logInfo("Task has already started. Ignoring converted PUT.");
            put.complete();
            return;
        }
        // normal PUT is not supported
        put.fail(Operation.STATUS_CODE_BAD_METHOD);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }

}
