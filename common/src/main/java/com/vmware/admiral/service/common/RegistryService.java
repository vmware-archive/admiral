/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.service.common;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.CertificateUtilExtended;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.SslCertificateResolver;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Describes the authentication credentials to authenticate with internal/external APIs.
 */
public class RegistryService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.REGISTRIES;

    protected static final String DEFAULT_REGISTRY_ADDRESS = System.getProperty(
            "admiral.default.registry.address", "https://registry.hub.docker.com");

    private static final String DEFAULT_INSTANCE_ID = "default-registry";
    public static final String DEFAULT_INSTANCE_LINK = UriUtils.buildUriPath(FACTORY_LINK,
            DEFAULT_INSTANCE_ID);
    public static final String API_VERSION_PROP_NAME = "apiVersion";
    public static final String DISABLE_DEFAULT_REGISTRY_PROP_NAME = "disable.default.registry";

    public enum ApiVersion {
        V1, V2
    }

    static ServiceDocument buildDefaultStateInstance(ServiceHost host) {
        RegistryState state = new RegistryState();
        state.documentSelfLink = DEFAULT_INSTANCE_LINK;
        state.address = DEFAULT_REGISTRY_ADDRESS;
        state.endpointType = RegistryState.DOCKER_REGISTRY_ENDPOINT_TYPE;
        state.customProperties = new HashMap<>();
        state.customProperties.put(API_VERSION_PROP_NAME, ApiVersion.V1.toString());

        boolean disableDefaultRegistry = Boolean.valueOf(
                ConfigurationUtil.getProperty(RegistryService.DISABLE_DEFAULT_REGISTRY_PROP_NAME));

        // create or delete default registry
        if (disableDefaultRegistry) {
            // ensure default registry does not exist
            host.registerForServiceAvailability((o, e) -> {
                host.log(Level.INFO, "registerForServiceAvailability: %s",
                        RegistryService.FACTORY_LINK);
                deleteDefaultRegistry(host);
            }, true, RegistryService.FACTORY_LINK);

            return null;
        }

        return state;
    }

    private static void deleteDefaultRegistry(ServiceHost host) {
        new ServiceDocumentQuery<RegistryState>(host, RegistryState.class)
                .queryDocument(DEFAULT_INSTANCE_LINK, (r) -> {
                    if (r.hasException()) {
                        r.throwRunTimeException();
                    } else if (r.hasResult()) {
                        host.registerForServiceAvailability((o, e) -> {
                            if (e != null) {
                                host.log(Level.SEVERE,
                                        "Failure waiting for service availability: %s. Error is: %s",
                                        DEFAULT_INSTANCE_LINK, Utils.toString(e));
                                return;
                            }
                            host.sendRequest(Operation.createDelete(
                                    UriUtils.buildUri(host, DEFAULT_INSTANCE_LINK))
                                    .setReferer(host.getUri())
                                    .setCompletion((op, ex) -> {
                                        if (ex != null) {
                                            host.log(Level.WARNING,
                                                    "Failed to destroy default registry on startup. %s",
                                                    Utils.toString(ex));
                                            return;
                                        }
                                        host.log(Level.INFO, "Successfully deleted default registry.");
                                    }));
                        }, DEFAULT_INSTANCE_LINK);
                    } else {
                        host.log(Level.INFO, "Default registry does not exist. Skip deletion.");
                    }
                });
    }

    public static class RegistryState extends MultiTenantDocument {

        public static final String DOCKER_REGISTRY_ENDPOINT_TYPE = "container.docker.registry";

        public static final String FIELD_NAME_ADDRESS = "address";
        public static final String FIELD_NAME_AUTH_CREDENTIALS_LINK = "authCredentialsLink";
        public static final String FIELD_NAME_PROTOCOL_TYPE = "protocolType";
        public static final String FIELD_NAME_ENDPOINT_TYPE = "endpointType";
        public static final String FIELD_NAME_NAME = "name";
        public static final String FIELD_NAME_DISABLED = "disabled";
        public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";

        /** Connection protocol type - HTTP, SSH... */
        @Documentation(description = "Connection protocol type", exampleString = "HTTP, SSH...")
        public String protocolType;

        /** The type of the endpoint configuration */
        @Documentation(description = " The type of the endpoint configuration", exampleString = DOCKER_REGISTRY_ENDPOINT_TYPE)
        public String endpointType;

        /** Name/type of a component/service (Required) */
        @Documentation(description = "Name/type of a component/service")
        public String name;

        /** URI or other connection address. (Required) */
        @Documentation(description = "URI or other connection address.")
        public String address;

        /** Link to associated authentication credentials. */
        @Documentation(description = "Link to associated authentication credentials.")
        @UsageOption(option = PropertyUsageOption.LINK)
        public String authCredentialsLink;

        /** (Optional) Version of the API supported by service registration */
        @Documentation(description = "Version of the API supported by service registration.", exampleString = "v1")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public String version;

        /** (Optional) Disabling the registry and removing it from the active registries. */
        @Documentation(description = "Disabling the registry and removing it from the active registries.")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Boolean disabled;

        /** (Optional) Custom properties */
        @Documentation(description = "Custom properties ")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        public Map<String, String> customProperties;
    }

    /**
     * State with in-line, expanded description
     */
    public static class RegistryAuthState extends RegistryState {
        public static URI buildUri(URI registryHostUri) {
            return UriUtils.extendUriWithQuery(registryHostUri, UriUtils.URI_PARAM_ODATA_EXPAND,
                    RegistryState.FIELD_NAME_AUTH_CREDENTIALS_LINK);
        }

        public AuthCredentialsServiceState authCredentials;

        public static RegistryAuthState create(
                AuthCredentialsServiceState authCredentials, RegistryState registryState) {
            RegistryAuthState regWithAuth = new RegistryAuthState();
            registryState.copyTo(regWithAuth);

            regWithAuth.address = registryState.address;
            regWithAuth.protocolType = registryState.protocolType;
            regWithAuth.endpointType = registryState.endpointType;
            regWithAuth.tenantLinks = registryState.tenantLinks;
            regWithAuth.name = registryState.name;
            regWithAuth.disabled = registryState.disabled;
            regWithAuth.address = registryState.address;
            regWithAuth.customProperties = registryState.customProperties;

            regWithAuth.authCredentials = authCredentials;
            regWithAuth.authCredentialsLink = authCredentials.documentSelfLink;

            return regWithAuth;
        }
    }

    public RegistryService() {
        super(RegistryState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleGet(Operation get) {
        RegistryState currentState = getState(get);
        boolean doExpand = get.getUri().getQuery() != null
                && get.getUri().getQuery().contains(UriUtils.URI_PARAM_ODATA_EXPAND);
        if (!doExpand || currentState.authCredentialsLink == null) {
            get.setBody(currentState).complete();
            return;
        }

        // retrieve the authnCredentials and include in an augmented version of the current state
        Operation getDesc = Operation
                .createGet(this, currentState.authCredentialsLink)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                get.fail(e);
                                return;
                            }
                            AuthCredentialsServiceState authCredentials = o
                                    .getBody(AuthCredentialsServiceState.class);
                            RegistryAuthState regWithAuth = RegistryAuthState
                                    .create(authCredentials, currentState);
                            get.setBody(regWithAuth).complete();
                        });
        sendRequest(getDesc);
    }

    @Override
    public void handlePatch(Operation patch) {
        RegistryState currentState = getState(patch);
        RegistryState patchBody = patch.getBody(RegistryState.class);

        updateState(patchBody, currentState);
        patch.setBody(currentState).complete();
    }

    @Override
    public void handlePut(Operation put) {
        RegistryState currentState = getState(put);
        RegistryState putBody = put.getBody(RegistryState.class);

        updateState(putBody, currentState);
        // PUT replaces entire state, so update the linked state
        setState(put, currentState);
        put.setBody(currentState).complete();
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }

    public static void fetchRegistryCertificate(RegistryState registry, Consumer<String> callback,
            ServiceHost host) {
        if (DeploymentProfileConfig.getInstance().isTest()) {
            Utils.logWarning("No ssl trust validation is performed in test mode...");
            return;
        }

        URI uri = URI.create(registry.address);
        if (!UriUtils.HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            Utils.logWarning("Using non secure channel, no certificate distribution for [%s]",
                    registry.address);
            return;
        }

        // follow the logic as before refactoring
        if (callback == null) {
            return;
        }

        SslCertificateResolver.execute(uri, (resolver, ex) -> {
            if (ex != null) {
                Utils.logWarning("Cannot connect to %s to get remote certificate",
                        registry.address);
                return;
            }
            X509Certificate[] certificateChain = resolver.getCertificateChain();

            String certificate = CertificateUtilExtended.toPEMformat(certificateChain, host);
            callback.accept(certificate);
        });
    }

    private void updateState(RegistryState newState, RegistryState currentState) {
        if (newState.address != null) {
            currentState.address = newState.address;
        }
        if (newState.protocolType != null) {
            currentState.protocolType = newState.protocolType;
        }
        if (newState.endpointType != null) {
            currentState.endpointType = newState.endpointType;
        }
        if (newState.tenantLinks != null) {
            currentState.tenantLinks = newState.tenantLinks;
        }
        if (newState.name != null) {
            currentState.name = newState.name;
        }
        if (newState.disabled != null) {
            currentState.disabled = newState.disabled;
        }
        if (newState.version != null) {
            currentState.version = newState.version;
        }
        if (newState.customProperties != null) {
            currentState.customProperties = newState.customProperties;
        }
        if (newState.authCredentialsLink != null) {
            currentState.authCredentialsLink = newState.authCredentialsLink;
        }
        if (newState.documentExpirationTimeMicros != 0) {
            currentState.documentExpirationTimeMicros = newState.documentExpirationTimeMicros;
        }
    }
}
