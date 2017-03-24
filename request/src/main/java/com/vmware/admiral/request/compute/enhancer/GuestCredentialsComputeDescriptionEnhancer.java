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

package com.vmware.admiral.request.compute.enhancer;

import static com.vmware.admiral.request.compute.enhancer.EnhancerUtils.getCustomProperty;

import java.net.URI;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.photon.controller.model.security.util.KeyUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class GuestCredentialsComputeDescriptionEnhancer extends ComputeDescriptionEnhancer {

    private ServiceHost host;
    private URI referer;

    public GuestCredentialsComputeDescriptionEnhancer(ServiceHost host, URI referer) {
        this.host = host;
        this.referer = referer;
    }

    @Override
    public DeferredResult<ComputeDescription> enhance(EnhanceContext context,
            ComputeDescription cd) {
        if (cd.authCredentialsLink == null && !cd.customProperties
                .containsKey(ComputeConstants.CUSTOM_PROP_ENABLE_SSH_ACCESS_NAME)) {
            return DeferredResult.completed(cd);
        }

        String sshKey = getCustomProperty(cd, ComputeConstants.CUSTOM_PROP_SSH_AUTHORIZED_KEY_NAME);
        if (sshKey != null && !sshKey.isEmpty()) {
            return DeferredResult.completed(cd);
        }

        if (cd.authCredentialsLink != null) {
            return DeferredResult.completed(cd);
        }

        DeferredResult<ComputeDescription> result = new DeferredResult<>();
        createClientCredentials(cd, (c, t) -> {
            if (t != null) {
                result.fail(t);
                return;
            }
            cd.authCredentialsLink = c.documentSelfLink;
            result.complete(cd);
        });
        return result;
    }

    private void createClientCredentials(ComputeDescription cd,
            BiConsumer<AuthCredentialsServiceState, Throwable> callback) {

        KeyPair keyPair = KeyUtil.generateRSAKeyPair();

        AuthCredentialsServiceState credentialsState = new AuthCredentialsServiceState();
        credentialsState.type = AuthCredentialsType.PublicKey.name();
        credentialsState.userEmail = UUID.randomUUID().toString();
        credentialsState.publicKey = KeyUtil.toPEMFormat(keyPair.getPublic());
        credentialsState.privateKey = KeyUtil.toPEMFormat(keyPair.getPrivate());

        String sshAuthorizedKey = KeyUtil
                .toPublicOpenSSHFormat((RSAPublicKey) keyPair.getPublic());
        credentialsState.customProperties = new HashMap<>();
        credentialsState.customProperties
                .put(ComputeConstants.CUSTOM_PROP_SSH_AUTHORIZED_KEY_NAME, sshAuthorizedKey);

        Operation.createPost(host, AuthCredentialsService.FACTORY_LINK)
                .setReferer(referer)
                .setBody(credentialsState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.WARNING, "Failed to store credentials: %s",
                                Utils.toString(e));
                        callback.accept(null, e);
                        return;
                    }
                    callback.accept(o.getBody(AuthCredentialsServiceState.class), null);
                }).sendWith(host);
    }

}
