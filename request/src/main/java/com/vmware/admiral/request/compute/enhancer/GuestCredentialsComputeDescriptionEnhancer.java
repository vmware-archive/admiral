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

import static com.vmware.admiral.request.compute.enhancer.ComputeDescriptionEnhancer.getCustomProperty;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vmware.admiral.common.AuthCredentialsType;
import com.vmware.admiral.common.util.KeyUtil;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class GuestCredentialsComputeDescriptionEnhancer implements ComputeDescriptionEnhancer {
    private static final String SSH_AUTHORIZED_KEY_PROP = "sshAuthorizedKey";
    private static final Pattern SSH_KEY_PLACEHOLDER = Pattern
            .compile("\\{\\{sshAuthorizedKey\\}\\}");

    private StatefulService sender;

    public GuestCredentialsComputeDescriptionEnhancer(StatefulService sender) {
        this.sender = sender;
    }

    @Override
    public void enhance(ComputeDescription cd, BiConsumer<ComputeDescription, Throwable> callback) {

        createClientCredentialsIfNeeded(cd, (c, t) -> {
            if (t != null) {
                callback.accept(cd, t);
                return;
            }
            cd.authCredentialsLink = c.documentSelfLink;
            String fileContent = getCustomProperty(cd,
                    ComputeConstants.COMPUTE_CONFIG_CONTENT_PROP_NAME);
            if (fileContent == null) {
                callback.accept(cd, null);
                return;
            }

            Matcher matcher = SSH_KEY_PLACEHOLDER.matcher(fileContent);
            if (matcher.find()) {
                String sshKey = getSshKey(cd, c);
                if (sshKey != null && !sshKey.isEmpty()) {
                    cd.customProperties.put(ComputeConstants.COMPUTE_CONFIG_CONTENT_PROP_NAME,
                            matcher.replaceFirst(sshKey));
                    cd.customProperties.put(ComputeConstants.CUSTOM_PROP_SSH_AUTHORIZED_KEY_NAME,
                            sshKey);
                }
            }
            callback.accept(cd, null);
        });
    }

    private String getSshKey(ComputeDescription cd, AuthCredentialsServiceState c) {
        String sshKey = getCustomProperty(cd, ComputeConstants.CUSTOM_PROP_SSH_AUTHORIZED_KEY_NAME);
        if (sshKey == null || sshKey.isEmpty()) {
            sshKey = c.customProperties != null ? c.customProperties.get(SSH_AUTHORIZED_KEY_PROP)
                    : null;
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
            credentialsState.customProperties.put(SSH_AUTHORIZED_KEY_PROP, sshAuthorizedKey);

            Operation.createPost(sender, AuthCredentialsService.FACTORY_LINK)
                    .setBody(credentialsState)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            sender.logWarning("Failed to store credentials: %s",
                                    Utils.toString(e));
                            callback.accept(null, e);
                            return;
                        }
                        callback.accept(o.getBody(AuthCredentialsServiceState.class), null);
                    }).sendWith(sender);
        } else {
            Operation.createGet(sender, cd.authCredentialsLink)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            callback.accept(null, e);
                            return;
                        }
                        callback.accept(o.getBody(AuthCredentialsServiceState.class), null);
                    })
                    .sendWith(sender);
        }
    }

}
