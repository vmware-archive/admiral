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

package com.vmware.admiral.compute;

import java.io.IOException;
import java.net.URI;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.commons.io.IOUtils;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.CertificateUtil;
import com.vmware.admiral.common.util.CertificateUtil.CertChainKeyPair;
import com.vmware.admiral.common.util.KeyUtil;
import com.vmware.admiral.common.util.SshServiceUtil;
import com.vmware.admiral.compute.ConfigureHostOverSshTaskService.ConfigureHostOverSshTaskServiceState.SubStage;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Configure a remote host as a Docker host for Admiral. The task may setup Docker if needed, set
 * self signed certificate and expose the Docker remote API on a designated port. At last it will
 * attach the setup machine as Admiral host.
 */
public class ConfigureHostOverSshTaskService extends
        AbstractTaskStatefulService<ConfigureHostOverSshTaskService.ConfigureHostOverSshTaskServiceState, ConfigureHostOverSshTaskService.ConfigureHostOverSshTaskServiceState.SubStage> {

    public static final String CONFIGURE_HOST_PLACEMENT_ZONE_LINK_CUSTOM_PROP = "__placementZoneLink";
    public static final String CONFIGURE_HOST_AUTH_CREDENTIALS_LINK_CUSTOM_PROP = "__authCredentialsLink";
    public static final String CONFIGURE_HOST_ADDRESS_CUSTOM_PROP = "__address";
    public static final String CONFIGURE_HOST_TAG_LINKS_CUSTOM_PROP = "__tagLinks";

    public static final String ADDRESS_NOT_SET_ERROR_MESSAGE = "Address is not set";
    public static final String PORT_NOT_SET_ERROR_MESSAGE = "Port is not set";
    public static final String CONNECTION_REFUSED_ERROR_MESSAGE = "Connection refused or user is not a sudoer.";

    public static final String DISPLAY_NAME = "Configure Host";
    public static final String FACTORY_LINK = ManagementUriParts.CONFIGURE_HOST;

    public static final String INSTALLER_RESOURCE = "installer.sh";

    static SshServiceUtil sshServiceUtil;

    public ConfigureHostOverSshTaskService() {
        super(ConfigureHostOverSshTaskServiceState.class,
                ConfigureHostOverSshTaskServiceState.SubStage.class,
                DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    public static class ConfigureHostOverSshTaskServiceState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<ConfigureHostOverSshTaskServiceState.SubStage> {
        public static enum SubStage {
            CREATED,
            SETUP,
            ADD_HOST,
            COMPLETED,
            ERROR
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

        @Documentation(description = "Set of tags set on this host.")
        @PropertyOptions(usage = { PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.SINGLE_ASSIGNMENT }, indexing = {
                        PropertyIndexingOption.STORE_ONLY })
        public Set<String> tagLinks;

        @Documentation(description = "Link to configured host")
        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL }, indexing = {
                        PropertyIndexingOption.STORE_ONLY })
        public String hostLink;
    }

    @Override
    protected TaskStatusState fromTask(TaskServiceDocument<SubStage> state) {
        TaskStatusState statusTask = super.fromTask(state);
        statusTask.name = ContainerOperationType.extractDisplayName(DISPLAY_NAME);
        String hostLink = ((ConfigureHostOverSshTaskServiceState) state).hostLink;
        if (hostLink != null) {
            if (statusTask.resourceLinks == null) {
                statusTask.resourceLinks = new HashSet<>();
            }

            statusTask.resourceLinks.add(hostLink);
        }
        return statusTask;
    }

    @Override
    protected void handleStartedStagePatch(ConfigureHostOverSshTaskServiceState state) {
        switch (state.taskSubStage) {
        case CREATED:
            validate(state, (t) -> {
                if (t != null) {
                    failTask(t.getMessage(), t);
                    return;
                }

                uploadResources(state);
            });

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

    @Override
    public void handleStop(Operation delete) {
        sshServiceUtil = null; // Null the static field to avoid issues in tests when the host
                               // changes
        super.handleStop(delete);
    }

    protected void validate(ConfigureHostOverSshTaskServiceState state,
            Consumer<Throwable> consumer) {
        validate(getHost(), state, consumer);
    }

    public static void validate(ServiceHost host, ConfigureHostOverSshTaskServiceState state,
            Consumer<Throwable> consumer) {
        if (state.address == null || state.address.equals("")) {
            consumer.accept(new LocalizableValidationException(ADDRESS_NOT_SET_ERROR_MESSAGE, "compute.configure.host.address.missing"));
        }
        if (state.port == null || state.port < 0) {
            consumer.accept(new LocalizableValidationException(PORT_NOT_SET_ERROR_MESSAGE, "compute.configure.host.port.missing"));
        }

        // Verify connectivity and that the user is root or another sudoer
        fetchCredentials(host, state.authCredentialsLink, (op, failure) -> {
            if (failure != null) {
                consumer.accept(failure);
                return;
            }

            AuthCredentialsServiceState creds = op.getBody(AuthCredentialsServiceState.class);

            String command = "echo 1234";
            if (!isRoot(creds)) {
                command = "sudo " + command;
            }

            getSshServiceUtil(host).exec(state.address, creds, command, (sshOp, sshFailure) -> {
                if (sshFailure != null) {
                    consumer.accept(sshFailure);
                    return;
                }

                consumer.accept(null);
            }, SshServiceUtil.SSH_OPERATION_TIMEOUT_MEDIUM, TimeUnit.SECONDS);
        });
    }

    public void uploadResources(ConfigureHostOverSshTaskServiceState state) {
        Operation fetchCredentialsOperation = createFetchCredentialsOperation(getHost(),
                state.authCredentialsLink, (op, failure) -> {
                    // Handle in the joined operation handler
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

                    AuthCredentialsServiceState credentials = ops
                            .get(fetchCredentialsOperation.getId())
                            .getBody(AuthCredentialsServiceState.class);
                    AuthCredentialsServiceState caCert = ops.get(fetchCaCertOperation.getId())
                            .getBody(AuthCredentialsServiceState.class);

                    createDirectories(state, credentials, caCert);
                }).sendWith(getHost());
    }

    public void createDirectories(ConfigureHostOverSshTaskServiceState state,
            AuthCredentialsServiceState credentials, AuthCredentialsServiceState caCert) {
        getSshServiceUtil().exec(state.address, credentials, "mkdir -p installer/certs",
                (op, failure) -> {
                    if (failure != null) {
                        failTask("Failed to create installer directories.", failure);
                        return;
                    }
                    uploadCaPem(state, credentials, caCert);
                }, SshServiceUtil.SSH_OPERATION_TIMEOUT_MEDIUM, TimeUnit.SECONDS);
    }

    public void uploadCaPem(ConfigureHostOverSshTaskServiceState state,
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

    public void generateServerCertPair(ConfigureHostOverSshTaskServiceState state,
            AuthCredentialsServiceState credentials, AuthCredentialsServiceState caCert) {
        KeyPair caKeyPair = CertificateUtil.createKeyPair(caCert.privateKey);
        X509Certificate caCertificate = CertificateUtil.createCertificate(caCert.publicKey);
        CertChainKeyPair signedForServer = CertificateUtil.generateSigned(state.address,
                caCertificate, caKeyPair.getPrivate());

        uploadServerPem(state, credentials, signedForServer);
    }

    public void uploadServerPem(ConfigureHostOverSshTaskServiceState state,
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

    public void uploadServerKeyPem(ConfigureHostOverSshTaskServiceState state,
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

    public void uploadInstaller(ConfigureHostOverSshTaskServiceState state,
            AuthCredentialsServiceState credentials) {
        byte[] data = null;
        try {
            data = IOUtils.toByteArray(Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(INSTALLER_RESOURCE));
            getSshServiceUtil().upload(state.address, credentials, data,
                    "installer/" + INSTALLER_RESOURCE,
                    (op, failure) -> {
                        proceedTo(SubStage.SETUP);
                    });
        } catch (IOException e) {
            failTask("Failed to load installer data", e);
        }
    }

    public void setup(ConfigureHostOverSshTaskServiceState state,
            AuthCredentialsServiceState credentials) {
        if (credentials == null) {
            fetchCredentials(getHost(), state.authCredentialsLink, (op, failure) -> {
                if (failure != null) {
                    failTask("Failed to fetch credentials", failure);
                    return;
                }
                setup(state, op.getBody(AuthCredentialsServiceState.class));
            });
            return;
        }

        String command = getInstallCommand(state, credentials);

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

    public String getInstallCommand(ConfigureHostOverSshTaskServiceState state,
            AuthCredentialsServiceState credentials) {
        String command = String.format(
                "bash installer.sh " + state.port,
                INSTALLER_RESOURCE);
        if (!isRoot(credentials)) {
            command = "sudo " + command;
        }

        command = "cd installer && " + command;

        return command;
    }

    public void addHost(ConfigureHostOverSshTaskServiceState state) {
        ComputeState cs = new ComputeState();
        cs.id = getHostId(state);
        cs.address = getHostUri(state).toString();
        cs.name = cs.address;
        cs.resourcePoolLink = state.placementZoneLink;
        if (state.customProperties != null) {
            cs.customProperties = new HashMap<>(state.customProperties);
        } else {
            cs.customProperties = new HashMap<>();
        }
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
        cs.tagLinks = state.tagLinks;

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

                    proceedTo(SubStage.COMPLETED, (s) -> {

                        s.hostLink = completedOp.getResponseHeader(Operation.LOCATION_HEADER);
                    });
                })
                .sendWith(getHost());
    }

    private String getHostId(ConfigureHostOverSshTaskServiceState state) {
        return state.address + ":" + state.port;
    }

    private URI getHostUri(ConfigureHostOverSshTaskServiceState state) {
        return URI.create("https://" + state.address + ":" + state.port);
    }

    private static void fetchCredentials(ServiceHost host,
            String authCredentialsLink,
            CompletionHandler handler) {
        createFetchCredentialsOperation(host, authCredentialsLink, handler).sendWith(host);
    }

    private static Operation createFetchCredentialsOperation(ServiceHost host,
            String authCredentialsLink,
            CompletionHandler handler) {
        return Operation.createGet(UriUtils.buildUri(host, authCredentialsLink))
                .setReferer(host.getUri())
                .setCompletion(handler);
    }

    private static boolean isRoot(AuthCredentialsServiceState creds) {
        return creds.userEmail.equals("root");
    }

    protected SshServiceUtil getSshServiceUtil() {
        return getSshServiceUtil(getHost());
    }

    protected static SshServiceUtil getSshServiceUtil(ServiceHost host) {
        synchronized (ConfigureHostOverSshTaskService.class) {
            if (sshServiceUtil == null) {
                sshServiceUtil = new SshServiceUtil(host);
            }

            return sshServiceUtil;
        }
    }
}