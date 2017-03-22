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
import java.util.logging.Level;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.photon.controller.model.security.util.KeyUtil;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class ComputeStateGuestCredentialsEnhancer extends ComputeEnhancer {

    private ServiceHost host;
    private URI referer;

    public ComputeStateGuestCredentialsEnhancer(ServiceHost host, URI referer) {
        this.host = host;
        this.referer = referer;
    }

    @Override
    public DeferredResult<ComputeState> enhance(EnhanceContext context,
            ComputeState cs) {
        return getComputeDescription(cs.descriptionLink)
                .thenCompose(cd -> {
                    if (cd.authCredentialsLink == null && !cs.customProperties
                            .containsKey(ComputeConstants.CUSTOM_PROP_ENABLE_SSH_ACCESS_NAME)) {
                        return DeferredResult.completed(cs);
                    }

                    String sshKey = getCustomProperty(cs,
                            ComputeConstants.CUSTOM_PROP_SSH_AUTHORIZED_KEY_NAME);
                    if (sshKey != null && !sshKey.isEmpty()) {
                        addSshAuthorizedKeys(context, sshKey);
                        return DeferredResult.completed(cs);
                    }

                    if (cd.authCredentialsLink == null) {
                        return DeferredResult.completed(cs);
                    }
                    DeferredResult<ComputeState> result = new DeferredResult<>();
                    Operation.createGet(host, cd.authCredentialsLink)
                            .setReferer(referer)
                            .setCompletion((o, e) -> {
                                if (e != null) {
                                    result.fail(e);
                                    return;
                                }
                                String sshAuthorizedKey = getSshKey(
                                        o.getBody(AuthCredentialsServiceState.class));
                                if (sshAuthorizedKey != null && !sshAuthorizedKey.isEmpty()) {
                                    addSshAuthorizedKeys(context, sshAuthorizedKey);
                                    cs.customProperties.put(
                                            ComputeConstants.CUSTOM_PROP_SSH_AUTHORIZED_KEY_NAME,
                                            sshAuthorizedKey);
                                }
                                result.complete(cs);
                            })
                            .sendWith(host);
                    return result;
                });
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

    private DeferredResult<ComputeDescription> getComputeDescription(String uriLink) {
        host.log(Level.INFO, "Loading state for %s", uriLink);

        return host.sendWithDeferredResult(
                Operation.createGet(UriUtils.buildUri(host, uriLink)).setReferer(referer),
                ComputeDescription.class);
    }

}
