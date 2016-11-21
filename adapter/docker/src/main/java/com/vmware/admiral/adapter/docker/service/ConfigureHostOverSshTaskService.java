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

package com.vmware.admiral.adapter.docker.service;

import java.io.IOException;
import java.net.URI;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.commons.io.IOUtils;

import com.vmware.admiral.adapter.docker.service.ConfigureHostOverSshTaskService.SetupOverSshServiceState.SubStage;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.CertificateUtil;
import com.vmware.admiral.common.util.CertificateUtil.CertChainKeyPair;
import com.vmware.admiral.common.util.KeyUtil;
import com.vmware.admiral.common.util.SshServiceUtil;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Configure a remote host as a Docker host for Admiral. The task may setup Docker if needed, set
 * self signed certificate and expose the Docker remote API on a designated port. At last it will
 * attach the setup machine as Admiral host.
 */
public class ConfigureHostOverSshTaskService extends
        AbstractTaskStatefulService<ConfigureHostOverSshTaskService.SetupOverSshServiceState, ConfigureHostOverSshTaskService.SetupOverSshServiceState.SubStage> {

    public static final String ADDRESS_NOT_SET_ERROR_MESSAGE = "Address is not set";
    public static final String PORT_NOT_SET_ERROR_MESSAGE = "Port is not set";

    public static final String DISPLAY_NAME = "Configure Host";
    public static final String FACTORY_LINK = ManagementUriParts.CONFIGURE_HOST;

    public static final String INSTALLER_RESOURCE = "installer.tar.gz"; // TODO: Iterate over
                                                                        // contents and
                                                                        // packaging, this one is
                                                                        // prototype for testing

    private SshServiceUtil sshServiceUtil;

    public ConfigureHostOverSshTaskService() {
        super(SetupOverSshServiceState.class, SetupOverSshServiceState.SubStage.class,
                DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    public static class SetupOverSshServiceState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<SetupOverSshServiceState.SubStage> {
        public static enum SubStage {
            CREATED,
            SETUP,
            ADD_HOST,
            COMPLETED,
            ERROR,
            FAILED;
        }

        @Documentation(description = "IP or hostname of the target machine.")
        @PropertyOptions(usage = { PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.SINGLE_ASSIGNMENT }, indexing = {
                        PropertyIndexingOption.STORE_ONLY })
        public String address;

        @Documentation(description = "Port for the docker remote API")
        @PropertyOptions(usage = { PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL }, indexing = {
                        PropertyIndexingOption.STORE_ONLY })
        public Integer port;

        @Documentation(description = "Auth credentials for SSH access to the machine. The user MUST be a sudoer.")
        @PropertyOptions(usage = { PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.SINGLE_ASSIGNMENT }, indexing = {
                        PropertyIndexingOption.STORE_ONLY })
        public String authCredentialsLink;

        @Documentation(description = "Placement zone where the target host is going to be assigned to")
        @PropertyOptions(usage = { PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.SINGLE_ASSIGNMENT }, indexing = {
                        PropertyIndexingOption.STORE_ONLY })
        public String placementZoneLink;
    }

    @Override
    protected void handleStartedStagePatch(SetupOverSshServiceState state) {
        switch (state.taskSubStage) {
        case CREATED:
            if (state.address == null || state.address.equals("")) {
                failTask(ADDRESS_NOT_SET_ERROR_MESSAGE,
                        new IllegalArgumentException(ADDRESS_NOT_SET_ERROR_MESSAGE));
                return;
            }
            if (state.port == null || state.port < 0) {
                failTask(PORT_NOT_SET_ERROR_MESSAGE,
                        new IllegalArgumentException(PORT_NOT_SET_ERROR_MESSAGE));
                return;
            }

            uploadResources(state);
            return;
        case SETUP:
            setup(state, null);
            return;
        case ADD_HOST:
            addHost(state);
            return;
        case COMPLETED:
            complete();
            return;
        default:
            completeWithError();
        }

    }

    public void uploadResources(SetupOverSshServiceState state) {
        Operation fetchCredentialsOperation = createFetchCredentialsOperation(
                state.authCredentialsLink, (creds) -> {
                });

        Operation fetchCaCertOperation = Operation
                .createGet(getHost(), ManagementUriParts.AUTH_CREDENTIALS_CA_LINK)
                .setReferer(this.getUri())
                .setCompletion((completedOp, failure) -> {
                    if (failure != null) {
                        failTask("Failed to retrive CA cert.", failure);
                        return;
                    }
                });

        OperationSequence.create(fetchCredentialsOperation, fetchCaCertOperation)
                .setCompletion((ops, failures) -> {
                    if (failures != null) {
                        failTask("One or several fetch operation for the setup failed!", null);
                        return;
                    }

                    AuthCredentialsServiceState credentials = ops.get(fetchCredentialsOperation.getId())
                            .getBody(AuthCredentialsServiceState.class);
                    AuthCredentialsServiceState caCert = ops.get(fetchCaCertOperation.getId())
                            .getBody(AuthCredentialsServiceState.class);

                    createDirectories(state, credentials, caCert);
                }).sendWith(getHost());
    }

    public void createDirectories(SetupOverSshServiceState state,
            AuthCredentialsServiceState credentials, AuthCredentialsServiceState caCert) {
        getSshServiceUtil().exec(state.address, credentials, "mkdir -p installer/certs",
                (op, failure) -> {
                    if (failure != null) {
                        failTask("Failed to create installer directories.", failure);
                        return;
                    }
                    uploadCaPem(state, credentials, caCert);
                }, 1, TimeUnit.MINUTES);
    }

    public void uploadCaPem(SetupOverSshServiceState state,
            AuthCredentialsServiceState credentials, AuthCredentialsServiceState caCert) {
        String pem = caCert.publicKey;
        getSshServiceUtil().upload(state.address, credentials, pem.getBytes(),
                "installer/certs/ca.pem", (op, failure) -> {
                    if (failure != null) {
                        failTask("Failed to upload ca.pem.", failure);
                        return;
                    }
                    generateServerCertPair(state, credentials, caCert);
                });
    }

    public void generateServerCertPair(SetupOverSshServiceState state,
            AuthCredentialsServiceState credentials, AuthCredentialsServiceState caCert) {
        KeyPair caKeyPair = CertificateUtil.createKeyPair(caCert.privateKey);
        X509Certificate caCertificate = CertificateUtil.createCertificate(caCert.publicKey);
        CertChainKeyPair signedForServer = CertificateUtil.generateSigned(state.address,
                caCertificate, caKeyPair.getPrivate());

        uploadServerPem(state, credentials, signedForServer);
    }

    public void uploadServerPem(SetupOverSshServiceState state,
            AuthCredentialsServiceState credentials, CertChainKeyPair pair) {
        String pem = CertificateUtil.toPEMformat(pair.getCertificate());
        getSshServiceUtil().upload(state.address, credentials, pem.getBytes(),
                "installer/certs/server.pem", (op, failure) -> {
                    if (failure != null) {
                        failTask("Failed to upload server.pem.", failure);
                        return;
                    }
                    uploadServerKeyPem(state, credentials, pair);
                });

    }

    public void uploadServerKeyPem(SetupOverSshServiceState state,
            AuthCredentialsServiceState credentials, CertChainKeyPair pair) {
        String pem = KeyUtil.toPEMFormat(pair.getPrivateKey());
        getSshServiceUtil().upload(state.address, credentials, pem.getBytes(),
                "installer/certs/server-key.pem", (op, failure) -> {
                    if (failure != null) {
                        failTask("Failed to upload server-key.pem.", failure);
                        return;
                    }
                    uploadInstaller(state, credentials);
                });
    }

    public void uploadInstaller(SetupOverSshServiceState state,
            AuthCredentialsServiceState credentials) {
        byte[] data = null;
        try {
            data = IOUtils.toByteArray(Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(INSTALLER_RESOURCE));
            getSshServiceUtil().upload(state.address, credentials, data, INSTALLER_RESOURCE,
                    (op, failure) -> {
                        proceedTo(SubStage.SETUP);
                    });
        } catch (IOException e) {
            failTask("Failed to load installer data", e);
        }
    }

    public void setup(SetupOverSshServiceState state, AuthCredentialsServiceState credentials) {
        if (credentials == null) {
            fetchCredentials(state.authCredentialsLink, (creds) -> {
                setup(state, creds);
            });
            return;
        }

        // Untar and execute
        String command = getInstallCommand(state);

        getSshServiceUtil().exec(state.address, credentials, command,
                (op, failure) -> {
                    if (failure != null) {
                        failTask("Failed to setup the docker daemon", failure);
                        return;
                    }

                    proceedTo(SubStage.ADD_HOST);
                },
                SshServiceUtil.SSH_OPERATION_TIMEOUT_LONG, TimeUnit.SECONDS);
    }

    public String getInstallCommand(SetupOverSshServiceState state) {
        return String.format(
                "tar -zxvf %s && cd installer && sudo bash installer.sh " + state.port,
                INSTALLER_RESOURCE);
    }

    public void addHost(SetupOverSshServiceState state) {
        ComputeState cs = new ComputeState();
        cs.address = getHostUri(state).toString();
        cs.name = cs.address;
        cs.resourcePoolLink = state.placementZoneLink;
        cs.customProperties = new HashMap<>();
        cs.customProperties.put(
                ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                ManagementUriParts.AUTH_CREDENTIALS_CLIENT_LINK);
        cs.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                DockerAdapterType.API.name());
        cs.powerState = ComputeService.PowerState.ON;
        cs.customProperties.put(ComputeConstants.COMPUTE_HOST_PROP_NAME, "true");
        cs.customProperties.put(ComputeConstants.COMPUTE_CONTAINER_HOST_PROP_NAME,
                "true");
        cs.tenantLinks = state.tenantLinks;

        ContainerHostSpec spec = new ContainerHostSpec();
        spec.hostState = cs;
        spec.acceptCertificate = true;

        Operation.createPut(getHost(), ContainerHostService.SELF_LINK)
                .setReferer(this.getUri())
                .setBody(spec)
                .setCompletion((completedOp, failure) -> {
                    if (failure != null) {
                        failTask("Failed to add host", failure);
                        return;
                    }

                    proceedTo(SubStage.COMPLETED);
                })
                .sendWith(getHost());
    }

    private URI getHostUri(SetupOverSshServiceState state) {
        return URI.create("https://" + state.address + ":" + state.port);
    }

    public void fetchCredentials(String authCredentialsLink,
            Consumer<AuthCredentialsServiceState> consumer) {
        createFetchCredentialsOperation(authCredentialsLink, consumer).sendWith(getHost());
    }

    private Operation createFetchCredentialsOperation(String authCredentialsLink,
            Consumer<AuthCredentialsServiceState> consumer) {
        return Operation.createGet(UriUtils.buildUri(getHost(), authCredentialsLink))
                .setReferer(this.getUri())
                .setCompletion((op, failure) -> {
                    if (failure != null) {
                        failTask("Failed to find auth credentials: " + authCredentialsLink,
                                failure);
                        return;
                    }

                    consumer.accept(op.getBody(AuthCredentialsServiceState.class));
                });
    }

    private SshServiceUtil getSshServiceUtil() {
        if (sshServiceUtil == null) {
            sshServiceUtil = new SshServiceUtil(getHost());
        }

        return sshServiceUtil;
    }
}