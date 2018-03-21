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

package com.vmware.admiral.service.common.harbor;

import static com.vmware.admiral.common.ManagementUriParts.CONFIG_PROPS;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.RegistryFactoryService;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class HarborInitRegistryService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.CONFIG + "/harbor-init-registry";

    @Override
    public void handleStart(Operation post) {
        getConfigProperty(Harbor.CONFIGURATION_URL_PROPERTY_NAME, (state, e) -> {
            if (e != null) {
                post.complete();
                return;
            }
            String harborUrl = state.value;
            if (harborUrl != null && !harborUrl.trim().isEmpty()
                    && !"http://vmware.github.io/harbor/".equals(harborUrl)) {

                checkAndCreateRegistry(post, harborUrl);
            } else {
                post.complete();
            }
        });
    }

    /**
     * Checks for existing default-harbor-registry and compares the address. If needed an new
     * registry is created or existing is updated and a new credentials are created.
     */
    private void checkAndCreateRegistry(Operation post, String harborUrl) {
        sendRequest(Operation
                .createGet(this, Harbor.DEFAULT_REGISTRY_LINK)
                .setCompletion((o, e) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        logInfo("Creating default Harbor registry");
                        createHarborCredentials(post,
                                (authLink) -> createHarborRegistry(post, harborUrl, authLink));
                    } else if (e != null) {
                        logSevere("Error getting default Harbor registry: %s", Utils.toString(e));
                        post.fail(e);
                    } else {
                        RegistryState registryState = o.getBody(RegistryState.class);
                        if (!harborUrl.equals(registryState.address)) {
                            logInfo("Default Harbor registry address has changed. Recreating it.");
                            createHarborCredentials(post,
                                    (authLink) -> createHarborRegistry(post, harborUrl, authLink));
                        } else {
                            post.complete();
                        }
                    }
                }));
    }

    private void createHarborCredentials(Operation post, Consumer<String> callback) {
        AuthCredentialsServiceState state = new AuthCredentialsServiceState();

        state.type = AuthCredentialsType.Password.toString();
        state.userEmail = Harbor.DEFAULT_REGISTRY_USER_PREFIX + UUID.randomUUID();
        state.privateKey = new BigInteger(160, new SecureRandom()).toString(32);

        sendRequest(Operation
                .createPost(UriUtils.buildUri(getHost(), ServiceUriPaths.CORE_CREDENTIALS))
                .setBodyNoCloning(state)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Unable to create default harbor credentials: %s",
                                Utils.toString(e));
                        post.fail(e);
                        return;
                    }
                    AuthCredentialsServiceState body = o.getBody(AuthCredentialsServiceState.class);
                    callback.accept(body.documentSelfLink);
                }));
    }

    private void createHarborRegistry(Operation post, String harborUrl,
            String authCredentialsLink) {
        RegistryState state = new RegistryState();
        state.documentSelfLink = Harbor.DEFAULT_REGISTRY_LINK;
        state.address = harborUrl;
        state.name = Harbor.DEFAULT_REGISTRY_NAME;
        state.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;
        state.authCredentialsLink = authCredentialsLink;
        state.customProperties = new HashMap<>();
        state.customProperties.put(RegistryService.API_VERSION_PROP_NAME,
                RegistryService.ApiVersion.V2.toString());

        sendRequest(Operation
                .createPost(UriUtils.buildUri(getHost(), RegistryFactoryService.SELF_LINK))
                .setBodyNoCloning(state)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Unable to create default harbor registry: %s",
                                Utils.toString(e));
                        post.fail(e);
                        return;
                    }
                    post.complete();
                }));
    }

    private void getConfigProperty(String propName,
            BiConsumer<ConfigurationState, Throwable> callback) {
        sendRequest(Operation
                .createGet(getHost(), UriUtils.buildUriPath(CONFIG_PROPS, propName))
                .setCompletion((res, ex) -> {
                    ConfigurationState body = null;
                    if (ex == null && res.hasBody()) {
                        body = res.getBody(ConfigurationState.class);
                    }
                    callback.accept(body, ex);
                }));
    }

}
