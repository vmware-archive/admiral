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

import static com.vmware.admiral.request.compute.enhancer.EnhancerUtils.SSH_AUTHORIZED_KEYS;
import static com.vmware.admiral.request.compute.enhancer.EnhancerUtils.getCustomProperty;

import java.net.URI;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import com.vmware.admiral.common.AuthCredentialsType;
import com.vmware.admiral.common.util.CertificateUtil;
import com.vmware.admiral.common.util.KeyUtil;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
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
            addSshAuthorizedKeys(context, sshKey);
            return DeferredResult.completed(cd);
        }
        DeferredResult<ComputeDescription> result = new DeferredResult<>();
        createClientCredentialsIfNeeded(cd, (c, t) -> {
            if (t != null) {
                result.fail(t);
                return;
            }
            cd.authCredentialsLink = c.documentSelfLink;

            String sshAuthorizedKey = getSshKey(c);
            if (sshAuthorizedKey != null && !sshAuthorizedKey.isEmpty()) {
                addSshAuthorizedKeys(context, sshAuthorizedKey);
                cd.customProperties.put(ComputeConstants.CUSTOM_PROP_SSH_AUTHORIZED_KEY_NAME,
                        sshAuthorizedKey);
            }
            result.complete(cd);
        });
        return result;
    }

    private void addSshAuthorizedKeys(EnhanceContext context, String sshKey) {
        ArrayList<String> keys = new ArrayList<>();
        keys.add(sshKey);
        context.content.put(SSH_AUTHORIZED_KEYS, keys);
    }

    private String getSshKey(AuthCredentialsServiceState c) {

        String sshKey = getCustomProperty(c.customProperties,
                ComputeConstants.CUSTOM_PROP_SSH_AUTHORIZED_KEY_NAME);
        if (sshKey != null) {
            return sshKey;
        }
        if (AuthCredentialsType.Public.name().equals(c.type)) {
            return c.publicKey;
        } else if (AuthCredentialsType.PublicKey.name().equals(c.type)) {
            try {
                KeyPair keyPair = CertificateUtil.createKeyPair(c.privateKey);
                return KeyUtil.toPublicOpenSSHFormat((RSAPublicKey) keyPair.getPublic());
            } catch (Exception e) {
                return null;
            }
        }
        return sshKey;
    }

    private void createClientCredentialsIfNeeded(ComputeDescription cd,
            BiConsumer<AuthCredentialsServiceState, Throwable> callback) {
        if (cd.authCredentialsLink == null) {
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
        } else {
            Operation.createGet(host, cd.authCredentialsLink)
                    .setReferer(referer)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            callback.accept(null, e);
                            return;
                        }
                        callback.accept(o.getBody(AuthCredentialsServiceState.class), null);
                    })
                    .sendWith(host);
        }
    }

}
