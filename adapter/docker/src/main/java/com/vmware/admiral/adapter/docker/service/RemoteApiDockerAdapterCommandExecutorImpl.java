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

package com.vmware.admiral.adapter.docker.service;

import static com.vmware.admiral.compute.ContainerHostService.SSL_TRUST_ALIAS_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.SSL_TRUST_CERT_PROP_NAME;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

import org.yaml.snakeyaml.util.UriEncoder;

import com.vmware.admiral.adapter.docker.util.DockerStreamUtil;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.DelegatingX509KeyManager;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Docker command executor implementation based on DCP and the docker remote API
 */
public class RemoteApiDockerAdapterCommandExecutorImpl implements
        DockerAdapterCommandExecutor {

    private static final Logger logger = Logger
            .getLogger(RemoteApiDockerAdapterCommandExecutorImpl.class.getName());

    private static final int SSL_TRUST_RETRIES_COUNT = Integer.getInteger(
            "com.vmware.admiral.adapter.ssltrust.delegate.retries", 5);
    private static final long SSL_TRUST_RETRIES_WAIT = Long.getLong(
            "com.vmware.admiral.adapter.ssltrust.delegate.retries.wait.millis", 500);

    public static final String MEDIA_TYPE_APPLICATION_TAR = "application/tar";

    private static volatile RemoteApiDockerAdapterCommandExecutorImpl INSTANCE;

    private static final Pattern ERROR_PATTERN = Pattern.compile("\"error\":\"(.*)\"");
    private final ServiceHost host;
    private final ServiceClient serviceClient;
    // Used for commands like exec start
    private final ServiceClient attachServiceClient;
    // Used for commands like load image from tar
    private final ServiceClient largeDataClient;
    private final DelegatingX509KeyManager keyManager = new DelegatingX509KeyManager();
    private ServerX509TrustManager trustManager;

    private final int DOCKER_REQUEST_PAYLOAD_SIZE_LIMIT = Integer.getInteger(
            "adapter.docker.api.client.request_payload_limit", 1024 * 1024 * 256);

    public final int DOCKER_REQUEST_TIMEOUT_SECONDS = Integer.getInteger(
            "adapter.docker.api.client.request_timeout_seconds", 60 * 2);

    private final int DOCKER_IMAGE_REQUEST_TIMEOUT_SECONDS = Integer.getInteger(
            "adapter.docker.api.client.image_request_timeout_seconds", 60 * 10);

    private enum ClientMode {
        DEFAULT,
        ATTACH,
        LARGE_DATA
    }

    protected RemoteApiDockerAdapterCommandExecutorImpl(ServiceHost host,
            TrustManager trustManager) {
        this.host = host;
        this.serviceClient = ServiceClientFactory.createServiceClient(trustManager, keyManager);
        this.attachServiceClient = ServiceClientFactory.createServiceClient(trustManager,
                keyManager);
        this.largeDataClient = ServiceClientFactory.createServiceClient(
                trustManager, keyManager, DOCKER_REQUEST_PAYLOAD_SIZE_LIMIT);

        if (trustManager instanceof ServerX509TrustManager) {
            this.trustManager = (ServerX509TrustManager) trustManager;
        }
    }

    public static RemoteApiDockerAdapterCommandExecutorImpl create(ServiceHost host,
            TrustManager trustManager) {
        if (INSTANCE != null) {
            return INSTANCE;
        }
        synchronized (RemoteApiDockerAdapterCommandExecutorImpl.class) {
            if (INSTANCE == null) {
                INSTANCE = new RemoteApiDockerAdapterCommandExecutorImpl(host, trustManager);
            }
        }
        return INSTANCE;
    }

    @Override
    public void buildImage(CommandInput input, Operation.CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        URI targetUri = UriUtils.extendUri(input.getDockerUri(), "/build");

        Map<String, Object> props = input.getProperties();
        byte[] imageData = (byte[]) props.get(DockerAdapterCommandExecutor
                .DOCKER_BUILD_IMAGE_DOCKERFILE_DATA);

        props.remove(DockerAdapterCommandExecutor.DOCKER_BUILD_IMAGE_DOCKERFILE_DATA);
        props.remove(SSL_TRUST_ALIAS_PROP_NAME);
        props.remove(SSL_TRUST_CERT_PROP_NAME);

        targetUri = extendUriWithQuery(targetUri, input);

        logger.info("Building image on: " + targetUri);

        Operation op = Operation.createPost(targetUri)
                .setBodyNoCloning(imageData)
                .setContentType(MEDIA_TYPE_APPLICATION_TAR)
                .setCompletion((o, ex) -> {
                    String body = o.getBody(String.class);
                    logger.info("Dump response body: " + body);
                    if (ex == null) {
                        // check the last status to make sure there was no error during download
                        // the response is not valid json so this is hacky way to find out if there
                        // was an error
                        Matcher matcher = ERROR_PATTERN.matcher(body);
                        if (matcher.find()) {
                            logger.info("Build failure detected! Response Body: " + body);
                            completionHandler.handle(o, new RuntimeException("Error: " + body));
                            o.complete();
                            return;
                        }
                    } else {
                        logger.severe("Unable to create image! Reason: " + ex.getMessage());
                    }
                    o.complete();
                    completionHandler.handle(o, ex);
                });

        prepareRequest(op, true);
        largeDataClient.send(op);

        logger.info("Building image request sent.");
    }

    @Override
    public void deleteImage(CommandInput input, Operation.CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String.format("/images/%s", input.getProperties()
                .get(DockerAdapterCommandExecutor.DOCKER_BUILD_IMAGE_TAG_PROP_NAME));
        URI targetUri = UriUtils.extendUri(input.getDockerUri(), path);

        logger.info("Deleting image: " + targetUri);

        sendDelete(targetUri, completionHandler);
    }

    @Override
    public void inspectImage(CommandInput input, Operation.CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String
                .format("/images/%s/json", input.getProperties().get(DockerAdapterCommandExecutor
                        .DOCKER_BUILD_IMAGE_INSPECT_NAME_PROP_NAME));
        URI targetUri = UriUtils.extendUri(input.getDockerUri(), path);

        logger.info("Inspecting image: " + targetUri);

        sendRequest(Service.Action.GET, targetUri, null, completionHandler, ClientMode.LARGE_DATA);
    }

    @Override
    public void loadImage(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        Object imageData = input.getProperties().get(DOCKER_IMAGE_DATA_PROP_NAME);
        URI targetUri = UriUtils.extendUri(input.getDockerUri(), "/images/load");
        sendPost(targetUri, imageData, ClientMode.LARGE_DATA, completionHandler);
    }

    @Override
    public void createImage(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        URI targetUri = UriUtils.extendUri(input.getDockerUri(), "/images/create");
        // extract X-Registry-Auth as a request header
        String registryAuth = (String) input.getProperties().remove(DOCKER_IMAGE_REGISTRY_AUTH);

        // convert the properties to URL parameters, the way /images/create likes them
        // expect fromImage or fromSrc
        targetUri = extendUriWithQuery(targetUri, input);

        Operation op = Operation.createPost(targetUri)
                .setBodyNoCloning("")
                .setCompletion((o, ex) -> {
                    if (ex == null) {
                        // check the last status to make sure there was no error during download
                        // the response is not valid json so this is hacky way to find out if there
                        // was an error
                        String body = o.getBody(String.class);

                        Matcher matcher = ERROR_PATTERN.matcher(body);
                        if (matcher.find()) {
                            completionHandler.handle(o, new RuntimeException("Error: "
                                    + matcher.group(1)));
                            return;
                        }
                    }
                    completionHandler.handle(o, ex);
                });

        if (registryAuth != null) {
            op.addRequestHeader(DOCKER_IMAGE_REGISTRY_AUTH, registryAuth);
        }

        prepareRequest(op, true);
        serviceClient.send(op);
    }

    @Override
    public void tagImage(CommandInput input, Operation.CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        Map<String, Object> props = input.getProperties();
        String path = String
                .format("/images/%s/tag", props.get(DockerAdapterCommandExecutor
                        .DOCKER_IMAGE_NAME_PROP_NAME));
        URI targetUri = UriUtils.extendUri(input.getDockerUri(), path);

        props.remove(DOCKER_IMAGE_NAME_PROP_NAME);

        // convert the properties to URL parameters, the way /images/{name}/tag likes them
        targetUri = extendUriWithQuery(targetUri, input);

        logger.info("Tagging image: " + targetUri);

        sendPost(targetUri, null, ClientMode.DEFAULT, completionHandler);
    }

    @Override
    public void createContainer(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        URI targetUri = UriUtils.extendUri(input.getDockerUri(), "/containers/create");
        String containerName = (String) input.getProperties().get(DOCKER_CONTAINER_NAME_PROP_NAME);
        if (containerName != null) {
            targetUri = UriUtils.extendUriWithQuery(targetUri, "name",
                    containerName);
        }

        sendPost(targetUri, input.getProperties(), ClientMode.DEFAULT, completionHandler);
    }

    @Override
    public void startContainer(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String.format("/containers/%s/start", input
                .getProperties().get(DOCKER_CONTAINER_ID_PROP_NAME));

        sendPost(UriUtils.extendUri(input.getDockerUri(), path), null, ClientMode.DEFAULT,
                completionHandler);
    }

    @Override
    public void stopContainer(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String.format("/containers/%s/stop", input
                .getProperties().get(DOCKER_CONTAINER_ID_PROP_NAME));

        sendPost(UriUtils.extendUri(input.getDockerUri(), path), null, ClientMode.DEFAULT,
                completionHandler);
    }

    @Override
    public void inspectContainer(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String.format("/containers/%s/json", input
                .getProperties().get(DOCKER_CONTAINER_ID_PROP_NAME));

        sendGet(UriUtils.extendUri(input.getDockerUri(), path), input.getProperties(),
                completionHandler);
    }

    @Override
    public void execContainer(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        createExec(input, completionHandler);
    }

    @Override
    public void fetchContainerStats(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String.format("/containers/%s/stats", input
                .getProperties().get(DOCKER_CONTAINER_ID_PROP_NAME));

        // add the stream=0 query parameter so the request returns immediately (requires docker 1.7)
        URI uri = UriUtils.extendUri(input.getDockerUri(), path);
        uri = UriUtils.extendUriWithQuery(uri, "stream", "0");

        sendGet(uri, input.getProperties(), completionHandler);
    }

    @Override
    public void removeContainer(CommandInput input,
            CompletionHandler completionHandler) {

        createOrUpdateTargetSsl(input);

        String path = String.format("/containers/%s", input
                .getProperties().get(DOCKER_CONTAINER_ID_PROP_NAME));

        // add v=1 and force=1 query parameters
        URI uri = UriUtils.extendUri(input.getDockerUri(), path);
        uri = UriUtils.extendUriWithQuery(uri, "v", "1", "force", "1");

        sendDelete(uri, completionHandler);
    }

    @Override
    public void fetchContainerLog(CommandInput input, CompletionHandler completionHandler) {
        // Update certificates
        createOrUpdateTargetSsl(input);

        // Construct the request path for logs
        String requestPath = String.format("/containers/%s/logs",
                input.getProperties().remove(DOCKER_CONTAINER_ID_PROP_NAME));
        URI targetUri = UriUtils.extendUri(input.getDockerUri(), requestPath);
        // append all the query parameters which are sent as input.
        targetUri = extendUriWithQuery(targetUri, input);

        sendGet(targetUri, null, completionHandler);
    }

    @Override
    public void hostPing(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        Operation op = Operation
                .createGet(UriUtils.extendUri(input.getDockerUri(), "/_ping"))
                .setCompletion(completionHandler);

        prepareRequest(op, false);
        op.setExpiration(ServiceUtils.getExpirationTimeFromNowInMicros(
                TimeUnit.SECONDS.toMicros(10)));

        setConnectionTag(input.getCredentials(), op);

        if (isSecure(input.getDockerUri())) {
            // Make sure that the trusted certificate is loaded before proceeding to avoid
            // SSLHandshakeException and getting hosts in DISABLED state
            ensureTrustDelegateExists(input, SSL_TRUST_RETRIES_COUNT, () -> {
                serviceClient.send(op);
            });
        } else {
            serviceClient.send(op);
        }
    }

    @Override
    public void hostInfo(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        Operation op = Operation
                .createGet(UriUtils.extendUri(input.getDockerUri(), "/info"))
                .setCompletion(completionHandler);

        prepareRequest(op, false);

        setConnectionTag(input.getCredentials(), op);

        if (isSecure(input.getDockerUri())) {
            // Make sure that the trusted certificate is loaded before proceeding to avoid
            // SSLHandshakeException and getting hosts in DISABLED state
            ensureTrustDelegateExists(input, SSL_TRUST_RETRIES_COUNT, () -> {
                serviceClient.send(op);
            });
        } else {
            serviceClient.send(op);
        }
    }

    private void setConnectionTag(AuthCredentialsServiceState credentials, Operation op) {
        // Avoid reusing an open channel to this host to ensure certs validation.
        if (credentials != null) {
            op.setConnectionTag(credentials.documentSelfLink +
                    String.valueOf(credentials.documentUpdateTimeMicros));
        }
    }

    @Override
    public void hostVersion(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        sendGet(UriUtils.extendUri(input.getDockerUri(), "/version"), null, completionHandler);
    }

    @Override
    public void listContainers(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        URI uri = UriUtils.extendUri(input.getDockerUri(), "/containers/json");
        uri = UriUtils.extendUriWithQuery(uri, "all", "1");
        sendGet(uri, null, completionHandler);
    }

    /**
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/#create-a-network
     * Mandatory properties for <code>input</code>:
     * <li>{@link DockerAdapterCommandExecutor#DOCKER_CONTAINER_NETWORK_NAME_PROP_NAME}
     */
    @Override
    public void createNetwork(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        URI targetUri = UriUtils.extendUri(input.getDockerUri(), "/networks/create");

        sendPost(targetUri, input.getProperties(), ClientMode.DEFAULT, completionHandler);
    }

    /**
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/#remove-a-network
     */
    @Override
    public void removeNetwork(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String idEncoded = UriEncoder.encode(input
                .getProperties().get(DOCKER_CONTAINER_NETWORK_ID_PROP_NAME).toString());

        String path = String.format("/networks/%s", idEncoded);

        URI uri = UriUtils.extendUri(input.getDockerUri(), path);
        // TODO there is no force remove for networks. All connected containers must be disconnected
        // or stopped for this to succeed

        sendDelete(uri, completionHandler);
    }

    /**
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/#/list-networks
     */
    @Override
    public void listNetworks(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        URI uri = UriUtils.extendUri(input.getDockerUri(), "/networks");

        sendGet(uri, null, completionHandler);
    }

    @Override
    public void inspectNetwork(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String idEncoded = UriEncoder.encode(input
                .getProperties().get(DOCKER_CONTAINER_NETWORK_ID_PROP_NAME).toString());
        String path = String.format("/networks/%s", idEncoded);

        sendGet(UriUtils.extendUri(input.getDockerUri(), path), input.getProperties(),
                completionHandler);
    }

    /**
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/#/connect-a-container-to-a-network
     */
    @Override
    public void connectContainerToNetwork(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String idEncoded = UriEncoder.encode(input
                .getProperties().get(DOCKER_CONTAINER_NETWORK_ID_PROP_NAME).toString());

        String path = String.format("/networks/%s/connect", idEncoded);

        sendPost(UriUtils.extendUri(input.getDockerUri(), path), input.getProperties(),
                ClientMode.DEFAULT, completionHandler);
    }

    @Override
    public void stop() {
        if (attachServiceClient != null) {
            attachServiceClient.stop();
        }
        if (this.serviceClient != null) {
            this.serviceClient.stop();
        }
        if (largeDataClient != null) {
            largeDataClient.stop();
        }

        INSTANCE = null;
    }

    @Override
    public void handlePeriodicMaintenance(Operation post) {
        if (attachServiceClient != null) {
            attachServiceClient.handleMaintenance(post);
        }
        if (serviceClient != null) {
            serviceClient.handleMaintenance(post);
        }
        if (largeDataClient != null) {
            largeDataClient.handleMaintenance(post);
        }
    }

    private void createExec(CommandInput input, CompletionHandler completionHandler) {
        String containerId = (String) input.getProperties().remove(DOCKER_CONTAINER_ID_PROP_NAME);
        if (containerId == null || containerId.isEmpty()) {
            completionHandler.handle(null,
                    new IllegalArgumentException("Container id not provided"));
            return;
        }

        String[] command = (String[]) input.getProperties().remove(DOCKER_EXEC_COMMAND_PROP_NAME);

        URI execUri = UriUtils.extendUri(input.getDockerUri(),
                String.format("/containers/%s/exec", containerId));

        String attachStdErr = (String) input.getProperties().remove(
                DOCKER_EXEC_ATTACH_STDERR_PROP_NAME);
        String attachStdOut = (String) input.getProperties().remove(
                DOCKER_EXEC_ATTACH_STDOUT_PROP_NAME);

        Map<String, Object> create = new HashMap<>();
        create.put(DOCKER_EXEC_ATTACH_STDIN_PROP_NAME, false);
        create.put(DOCKER_EXEC_ATTACH_STDERR_PROP_NAME,
                attachStdErr != null ? Boolean.valueOf(attachStdErr) : true);
        create.put(DOCKER_EXEC_ATTACH_STDOUT_PROP_NAME,
                attachStdOut != null ? Boolean.valueOf(attachStdOut) : true);
        create.put(DOCKER_EXEC_TTY_PROP_NAME, false);
        create.put(DOCKER_EXEC_COMMAND_PROP_NAME, command);

        sendPost(execUri, create, ClientMode.DEFAULT, (o, e) -> {
            if (e != null) {
                completionHandler.handle(o, e);
            } else {
                @SuppressWarnings("unchecked")
                Map<String, String> result = o.getBody(Map.class);
                String execId = result.get(DOCKER_EXEC_ID_PROP_NAME);
                startExec(input, execId, completionHandler);
            }
        });
    }

    private void startExec(CommandInput input, String execId, CompletionHandler completionHandler) {
        Map<String, Object> startBody = new HashMap<>();
        startBody.put(DOCKER_EXEC_TTY_PROP_NAME, false);
        startBody.put(DOCKER_EXEC_DETACH_PROP_NAME, false);

        URI startUri = UriUtils.extendUri(input.getDockerUri(),
                String.format("/exec/%s/start", execId));

        sendPostAttach(startUri, startBody, (op, ex) -> {
            if (ex != null) {
                completionHandler.handle(null, ex);
            } else {
                if (op.hasBody()) {
                    String body;

                    Object rawBody = op.getBodyRaw();
                    /* when the shell command does not return anything */
                    if (startBody.equals(rawBody)) {
                        body = "";
                    } else if (rawBody instanceof byte[]) {
                        try {
                            body = DockerStreamUtil.decodeFullRawResponce((byte[]) rawBody);
                        } catch (Exception decodeEx) {
                            logger.severe(decodeEx.getMessage());
                            completionHandler.handle(null, decodeEx);
                            return;
                        }
                    } else {
                        String err = "Unexpected response body of docker exec: " + rawBody;
                        logger.severe(err);
                        completionHandler.handle(null, new RuntimeException(err));
                        return;
                    }
                    op.setBodyNoCloning(body);
                }
                completionHandler.handle(op, null);
            }
        });
    }

    /**
     * Constructs the URI query parameters with the set of input properties from the command.
     */
    private URI extendUriWithQuery(URI targetUri, CommandInput input) {
        List<String> parameters = new ArrayList<>();
        for (Map.Entry<String, Object> property : input.getProperties().entrySet()) {
            parameters.add(property.getKey());
            parameters.add(String.valueOf(property.getValue()));
        }

        return UriUtils.extendUriWithQuery(targetUri, parameters.toArray(new String[0]));
    }

    /**
     * Update the dynamic KeyManager and TrustManager with the client and server certs for the
     * current request
     */
    private void createOrUpdateTargetSsl(CommandInput input) {
        if (input.getCredentials() == null) {
            return;
        }

        if (!isSecure(input.getDockerUri())) {
            return;
        }

        String sslTrust = (String) input.getProperties().get(SSL_TRUST_CERT_PROP_NAME);
        String trustAlias = (String) input.getProperties().get(SSL_TRUST_ALIAS_PROP_NAME);
        if (trustAlias == null) {
            logger.warning("No trust alias property set, not using certificate.");
            return;
        }

        if (sslTrust != null && trustManager != null) {
            trustManager.putDelegate(trustAlias, sslTrust);
        }

        String clientKey = EncryptionUtils.decrypt(input.getCredentials().privateKey);
        String clientCert = input.getCredentials().publicKey;

        // TODO use an LRU cache to limit the number of stored
        // KeyManagers while minimizing time wasted repeatedly
        // recreating them
        if (clientKey != null && !clientKey.isEmpty()) {
            X509ExtendedKeyManager delegateKeyManager = (X509ExtendedKeyManager) CertificateUtil
                    .getKeyManagers(trustAlias, clientKey, clientCert)[0];
            keyManager.putDelegate(trustAlias, delegateKeyManager);
        }
    }

    private boolean isSecure(URI dockerUri) {
        AssertUtil.assertNotNull(dockerUri, "dockerUri");

        return UriUtils.HTTPS_SCHEME.equalsIgnoreCase(dockerUri.getScheme());
    }

    private void ensureTrustDelegateExists(CommandInput input, int retryCount, Runnable callback) {
        if (trustManager != null) {
            String trustAlias = (String) input.getProperties().get(SSL_TRUST_ALIAS_PROP_NAME);
            if (trustAlias == null) {
                // trustAlias not defined, continue anyways, might fail later
                logger.warning("Trust alias not defined");
                callback.run();
                return;
            }
            X509TrustManager delegate = trustManager.getDelegate(trustAlias);
            if (delegate != null) {
                callback.run();
                return;
            }
            if (retryCount > 0) {
                host.schedule(() -> {
                    logger.info("Waiting for the trust delegate for " + trustAlias
                            + " to be populated");
                    ensureTrustDelegateExists(input, retryCount - 1, callback);
                }, SSL_TRUST_RETRIES_WAIT, TimeUnit.MILLISECONDS);
            } else {
                // trust not found, continue anyways, might fail later
                logger.warning("Trust delegate for " + trustAlias + " not found");
                callback.run();
            }
        } else {
            callback.run();
        }
    }

    private void sendGet(URI uri, Object body, CompletionHandler completionHandler) {
        sendRequest(Service.Action.GET, uri, body, completionHandler, ClientMode.DEFAULT);
    }

    private void sendPost(URI uri, Object body, ClientMode mode,
            CompletionHandler completionHandler) {

        if (ClientMode.LARGE_DATA != mode) {
            String msg = String.format("Sending POST to %s with body (possibly truncated):"
                            + "%n---%n%1.1024s%n---%n", uri, Utils.toJsonHtml(body));
            logger.finest(msg);
        } else {
            String msg = String.format("Sending POST to %s with large body", uri);
            logger.finest(msg);
        }
        sendRequest(Service.Action.POST, uri, body, completionHandler, mode);
    }

    private void sendPostAttach(URI uri, Object body, CompletionHandler completionHandler) {
        String msg = String.format( "Sending POST for attach to %s with body (possibly truncated):"
                + "%n---%n%1.1024s%n---%n", uri, Utils.toJsonHtml(body));
        logger.finest(msg);
        sendRequest(Service.Action.POST, uri, body, completionHandler, ClientMode.ATTACH);
    }

    private void sendDelete(URI uri, CompletionHandler completionHandler) {
        sendRequest(Service.Action.DELETE, uri, null, completionHandler, ClientMode.DEFAULT);
    }

    private void sendRequest(Service.Action action, URI uri, Object body,
            CompletionHandler completionHandler, ClientMode mode) {
        // with createGet the authorization context is propagated to the completion handler...
        Operation op = Operation.createGet(uri)
                .setAction(action)
                .setCompletion(completionHandler);

        if (ClientMode.LARGE_DATA == mode) {
            op.setBodyNoCloning(body);
            prepareRequest(op, true);
            largeDataClient.send(op);
        } else if (ClientMode.ATTACH == mode) {
            op.setBody(body);
            prepareRequest(op,false);
            attachServiceClient.send(op);
        } else {
            op.setBody(body);
            prepareRequest(op, false);
            serviceClient.send(op);
        }
    }

    /* Common settings on all outgoing requests to the docker server */
    protected void prepareRequest(Operation op, boolean longRunningRequest) {
        op.setReferer(URI.create("/"));
        op.forceRemote();

        if (op.getExpirationMicrosUtc() == 0) {
            long timeout = longRunningRequest ?
                    TimeUnit.SECONDS.toMicros(DOCKER_IMAGE_REQUEST_TIMEOUT_SECONDS) :
                    TimeUnit.SECONDS.toMicros(DOCKER_REQUEST_TIMEOUT_SECONDS);

            op.setExpiration(ServiceUtils.getExpirationTimeFromNowInMicros(timeout));
        }
    }

    /**
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/ Section 3.4 Volumes -
     * Create a volume Mandatory properties for <code>input</code>:
     * <li>{@link DockerAdapterCommandExecutor#DOCKER_VOLUME_NAME_PROP_NAME}
     */
    @Override
    public void createVolume(CommandInput input, CompletionHandler completionHandler) {

        createOrUpdateTargetSsl(input);

        URI targetUri = UriUtils.extendUri(input.getDockerUri(), "/volumes/create");

        sendPost(targetUri, input.getProperties(), ClientMode.DEFAULT, completionHandler);
    }

    /**
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/ Section 3.4 Volumes -
     * Remove a volume
     */
    @Override
    public void removeVolume(CommandInput input, CompletionHandler completionHandler) {

        createOrUpdateTargetSsl(input);

        String path = String.format("/volumes/%s",
                input.getProperties().get(DOCKER_VOLUME_NAME_PROP_NAME));

        URI uri = UriUtils.extendUri(input.getDockerUri(), path);

        sendDelete(uri, completionHandler);
    }

    /**
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/ Section 3.4 Volumes -
     * List volumes
     */
    @Override
    public void listVolumes(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);
        URI uri = UriUtils.extendUri(input.getDockerUri(), "/volumes");

        sendGet(uri, null, completionHandler);
    }

    /**
     * https://docs.docker.com/engine/reference/api/docker_remote_api_v1.24/ Section 3.4 Volumes -
     * Inspect a volume
     */
    @Override
    public void inspectVolume(CommandInput input, CompletionHandler completionHandler) {
        createOrUpdateTargetSsl(input);

        String path = String.format("/volumes/%s", input
                .getProperties().get(DOCKER_VOLUME_NAME_PROP_NAME));

        sendGet(UriUtils.extendUri(input.getDockerUri(), path), input.getProperties(),
                completionHandler);
    }

}
