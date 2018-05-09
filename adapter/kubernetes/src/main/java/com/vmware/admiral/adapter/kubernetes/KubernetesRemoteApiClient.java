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

package com.vmware.admiral.adapter.kubernetes;

import static com.vmware.admiral.adapter.kubernetes.ApiUtil.API_PREFIX_EXTENSIONS_V1BETA;
import static com.vmware.admiral.adapter.kubernetes.ApiUtil.API_PREFIX_V1;
import static com.vmware.admiral.adapter.kubernetes.ApiUtil.getKubernetesPath;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.DEPLOYMENT_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.KUBERNETES_LABEL_APP;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.KUBERNETES_LABEL_APP_ID;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.NAMESPACE_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.NODE_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.POD_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.REPLICATION_CONTROLLER_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.REPLICA_SET_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.SERVICE_TYPE;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;

import com.google.common.util.concurrent.AtomicDouble;

import com.vmware.admiral.adapter.kubernetes.service.AbstractKubernetesAdapterService.KubernetesContext;
import com.vmware.admiral.common.util.AuthUtils;
import com.vmware.admiral.common.util.DelegatingX509KeyManager;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.kubernetes.KubernetesHostConstants;
import com.vmware.admiral.compute.kubernetes.entities.common.ObjectMeta;
import com.vmware.admiral.compute.kubernetes.entities.namespaces.Namespace;
import com.vmware.admiral.compute.kubernetes.entities.namespaces.NamespaceList;
import com.vmware.admiral.compute.kubernetes.entities.nodes.KubernetesNodeData;
import com.vmware.admiral.compute.kubernetes.entities.nodes.Node;
import com.vmware.admiral.compute.kubernetes.entities.nodes.NodeList;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class KubernetesRemoteApiClient {
    /*
     * Kubernetes API doesn't support field selectors just yet
     * https://github.com/kubernetes/kubernetes/issues/1362
     */

    public static final String pingPath = "/healthz";

    public static final String LABEL_SELECTOR_QUERY = "labelSelector";

    private static final Logger logger = Logger
            .getLogger(KubernetesRemoteApiClient.class.getName());

    private static final int REQUEST_TIMEOUT_SECONDS = 10;

    private final ServiceClient serviceClient;
    private final DelegatingX509KeyManager keyManager = new DelegatingX509KeyManager();
    private ServerX509TrustManager trustManager;

    private static KubernetesRemoteApiClient INSTANCE = null;

    protected KubernetesRemoteApiClient(ServiceHost host, final TrustManager trustManager) {
        this.serviceClient = ServiceClientFactory.createServiceClient(trustManager, keyManager);

        if (trustManager instanceof ServerX509TrustManager) {
            this.trustManager = (ServerX509TrustManager) trustManager;
        }
    }

    public static synchronized KubernetesRemoteApiClient create(
            ServiceHost host, final TrustManager trustManager) {
        if (INSTANCE == null) {
            INSTANCE = new KubernetesRemoteApiClient(host, trustManager);
        }
        return INSTANCE;
    }

    public void stop() {
        if (this.serviceClient != null) {
            this.serviceClient.stop();
        }
        INSTANCE = null;
    }

    public void handleMaintenance(Operation post) {
        if (serviceClient != null) {
            serviceClient.handleMaintenance(post);
        }
    }

    private void createOrUpdateTargetSsl(KubernetesContext context) {
        if (context.credentials == null
                || !AuthCredentialsType.PublicKey.name().equals(context.credentials.type)) {
            return;
        }

        URI uri = UriUtils.buildUri(context.host.address);
        if (!isSecure(uri)) {
            return;
        }

        String clientKey = EncryptionUtils.decrypt(context.credentials.privateKey);
        String clientCert = context.credentials.publicKey;
        String alias = context.host.address.toLowerCase();

        if (clientKey != null && !clientKey.isEmpty()) {
            X509ExtendedKeyManager delegateKeyManager = (X509ExtendedKeyManager) CertificateUtil
                    .getKeyManagers(alias, clientKey, clientCert)[0];
            keyManager.putDelegate(alias, delegateKeyManager);
        }

        String sslTrust = context.SSLTrustCertificate;

        if (sslTrust != null && trustManager != null) {
            String trustAlias = context.SSLTrustAlias;

            trustManager.putDelegate(trustAlias, sslTrust);
        }
    }

    private void prepareRequest(Operation op, KubernetesContext context) {
        String authorizationHeaderValue = AuthUtils.createAuthorizationHeader(context.credentials);
        if (authorizationHeaderValue != null) {
            op.addRequestHeader(Operation.AUTHORIZATION_HEADER, authorizationHeaderValue);
        } else {
            createOrUpdateTargetSsl(context);
        }

        op.setReferer(URI.create("/"));
        op.forceRemote();

        if (op.getExpirationMicrosUtc() == 0) {
            long timeout = TimeUnit.SECONDS.toMicros(REQUEST_TIMEOUT_SECONDS);
            op.setExpiration(ServiceUtils.getExpirationTimeFromNowInMicros(timeout));
        }
    }

    public void ping(KubernetesContext context, CompletionHandler completionHandler) {
        URI uri = UriUtils.buildUri(context.host.address + pingPath);
        Operation op = Operation
                .createGet(uri)
                .setCompletion(completionHandler);

        prepareRequest(op, context);
        op.setExpiration(ServiceUtils.getExpirationTimeFromNowInMicros(
                TimeUnit.SECONDS.toMicros(10)));
        serviceClient.send(op);
    }

    public void doInfo(KubernetesContext context, CompletionHandler completionHandler) {
        getNodes(context, (o, ex) -> {
            if (ex != null) {
                completionHandler.handle(null, ex);
            } else {
                NodeList nodeList = o.getBody(NodeList.class);
                AtomicDouble usedCPU = new AtomicDouble(0D);
                AtomicDouble totalCPU = new AtomicDouble(0D);
                AtomicDouble totalMem = new AtomicDouble(0D);
                AtomicDouble usedMem = new AtomicDouble(0D);
                AtomicInteger counter = new AtomicInteger(nodeList.items.size());
                AtomicBoolean hasError = new AtomicBoolean();
                List<KubernetesNodeData> nodes = new ArrayList<>(nodeList.items.size());
                if (nodeList != null && nodeList.items != null) {
                    for (Node node : nodeList.items) {
                        if (node == null || node.metadata == null || node.metadata.name == null) {
                            continue;
                        }
                        getStats(context, node, (o2, ex2) -> {
                            if (ex2 != null) {
                                logger.log(Level.WARNING, String.format("Error while getting stats "
                                        + "for node %s", node.metadata.name), ex2);
                                if (hasError.compareAndSet(false, true)) {
                                    completionHandler.handle(null, ex2);
                                }
                            } else {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> data = o2.getBody(Map.class);
                                KubernetesNodeData nodeData = new KubernetesNodeData();
                                nodeData.name = node.metadata.name;
                                if (data != null && data.containsKey("allocatedResources")) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Double> resources = (Map<String, Double>) data
                                            .get("allocatedResources");
                                    Double val = null;
                                    if ((val = resources.get("cpuRequestsFraction")) != null) {
                                        Double totalForNode = resources.get("cpuCapacity");
                                        totalCPU.addAndGet(totalForNode);
                                        nodeData.usedCPU = val;
                                        usedCPU.addAndGet(val * totalForNode);
                                    }
                                    if ((val = resources.get("memoryCapacity")) != null) {
                                        nodeData.totalMem = val;
                                        totalMem.addAndGet(val);
                                    }
                                    if ((val = resources.get("memoryRequests")) != null) {
                                        nodeData.availableMem = nodeData.totalMem - val;
                                        usedMem.addAndGet(val);
                                    }
                                }
                                synchronized (nodes) {
                                    nodes.add(nodeData);
                                }
                                if (counter.decrementAndGet() == 0 && !hasError.get()) {
                                    Map<String, String> properties = new HashMap<>();

                                    // avoid division by zero - NaN causes trouble when being
                                    // serialized as JSON
                                    Double cpuUsage = totalCPU.compareAndSet(0D, 0D)
                                            ? 0D : usedCPU.get() / totalCPU.get();
                                    properties.put(
                                            ContainerHostService.DOCKER_HOST_CPU_USAGE_PCT_PROP_NAME,
                                            Double.toString(cpuUsage));

                                    properties.put(
                                            ContainerHostService.DOCKER_HOST_AVAILABLE_MEMORY_PROP_NAME,
                                            Double.toString(totalMem.get() - usedMem.get()));

                                    properties.put(
                                            ContainerHostService.DOCKER_HOST_TOTAL_MEMORY_PROP_NAME,
                                            Double.toString(totalMem.get()));

                                    properties.put(
                                            ContainerHostService.KUBERNETES_HOST_NODE_LIST_PROP_NAME,
                                            Utils.toJson(nodes));

                                    Operation result = new Operation();
                                    result.setBody(properties);
                                    completionHandler.handle(result, null);
                                }
                            }
                        });
                    }
                }
            }
        });
    }

    public void getStats(KubernetesContext context, Node node, CompletionHandler
            completionHandler) {

        // This url may not be accessible on every kubernetes setup. It requires that the
        // dashboard service is running
        String dashboardServiceSelflink = "services/kubernetes-dashboard";
        String dashboardServiceNamespace = "kube-system";
        String proxiedPath = UriUtils.buildUriPath("api/v1/node", node.metadata.name);

        URI uri = UriUtils.buildUri(ApiUtil.buildApiServerProxyUri(context, API_PREFIX_V1,
                dashboardServiceNamespace, dashboardServiceSelflink, proxiedPath));
        sendRequest(Action.GET, uri, null, context, completionHandler);
    }

    public void getNamespaces(KubernetesContext context, CompletionHandler completionHandler) {
        URI uri = UriUtils
                .buildUri(ApiUtil.apiPrefix(context, ApiUtil.API_PREFIX_V1) +
                        getKubernetesPath(NAMESPACE_TYPE));
        sendRequest(Action.GET, uri, null, context, completionHandler);
    }

    public void createNamespaceIfMissing(KubernetesContext context,
            CompletionHandler completionHandler) {
        URI uri = UriUtils
                .buildUri(ApiUtil.apiPrefix(context, ApiUtil.API_PREFIX_V1) +
                        getKubernetesPath(NAMESPACE_TYPE));
        String target = context.host.customProperties.get(
                KubernetesHostConstants.KUBERNETES_HOST_NAMESPACE_PROP_NAME);

        //getNamespaces(context, (operation, throwable) -> {
        sendRequest(Action.GET, uri, null, context, (o, ex) -> {
            if (ex != null) {
                completionHandler.handle(o, ex);
                return;
            }
            NamespaceList namespaceList = o.getBody(NamespaceList.class);
            if (namespaceList == null) {
                completionHandler.handle(o, new IllegalStateException("Null body"));
                return;
            }
            for (Namespace namespace : namespaceList.items) {
                if (namespace.metadata != null &&
                        namespace.metadata.name != null &&
                        namespace.metadata.name.equals(target)) {
                    completionHandler.handle(o, null);
                    return;
                }
            }
            Namespace namespace = new Namespace();
            namespace.metadata = new ObjectMeta();
            namespace.metadata.name = target;
            sendRequest(Action.POST, uri, Utils.toJson(namespace), context, completionHandler);
        });
    }

    public void getPods(KubernetesContext context, String appId, CompletionHandler
            completionHandler) {
        URI uri = UriUtils
                .buildUri(ApiUtil.namespacePrefix(context, ApiUtil.API_PREFIX_V1) +
                        getKubernetesPath(POD_TYPE));

        if (appId != null) {
            uri = UriUtils.extendUriWithQuery(uri, LABEL_SELECTOR_QUERY, String
                    .format("%s=%s", KUBERNETES_LABEL_APP_ID, appId));
        }

        sendRequest(Action.GET, uri, null, context, completionHandler);
    }

    public void getNodes(KubernetesContext context, CompletionHandler completionHandler) {
        URI uri = UriUtils
                .buildUri(ApiUtil.apiPrefix(context, ApiUtil.API_PREFIX_V1) + getKubernetesPath
                        (NODE_TYPE));
        sendRequest(Action.GET, uri, null, context, completionHandler);
    }

    public void getServices(KubernetesContext context, String appId, CompletionHandler
            completionHandler) {
        URI uri = UriUtils.buildUri(ApiUtil.namespacePrefix(context, ApiUtil.API_PREFIX_V1) +
                getKubernetesPath(SERVICE_TYPE));

        if (appId != null) {
            uri = UriUtils.extendUriWithQuery(uri, LABEL_SELECTOR_QUERY, String
                    .format("%s=%s", KUBERNETES_LABEL_APP_ID, appId));
        }

        sendRequest(Action.GET, uri, null, context, completionHandler);
    }

    public void getDeployments(KubernetesContext context, String appId, CompletionHandler
            completionHandler) {
        URI uri = UriUtils.buildUri(
                ApiUtil.namespacePrefix(context, API_PREFIX_EXTENSIONS_V1BETA)
                        + getKubernetesPath(DEPLOYMENT_TYPE));

        if (appId != null) {
            uri = UriUtils.extendUriWithQuery(uri, LABEL_SELECTOR_QUERY, String
                    .format("%s=%s", KUBERNETES_LABEL_APP_ID, appId));
        }

        sendRequest(Action.GET, uri, null, context, completionHandler);
    }

    public void getReplicaSets(KubernetesContext context, String appId, CompletionHandler
            completionHandler) {
        URI uri = UriUtils.buildUri(
                ApiUtil.namespacePrefix(context, API_PREFIX_EXTENSIONS_V1BETA)
                        + getKubernetesPath(REPLICA_SET_TYPE));

        if (appId != null) {
            uri = UriUtils.extendUriWithQuery(uri, LABEL_SELECTOR_QUERY, String
                    .format("%s=%s", KUBERNETES_LABEL_APP_ID, appId));
        }

        sendRequest(Action.GET, uri, null, context, completionHandler);
    }

    public void getReplicationControllers(KubernetesContext context, String appId,
            CompletionHandler completionHandler) {
        URI uri = UriUtils.buildUri(
                ApiUtil.namespacePrefix(context, API_PREFIX_V1)
                        + getKubernetesPath(REPLICATION_CONTROLLER_TYPE));

        if (appId != null) {
            uri = UriUtils.extendUriWithQuery(uri, LABEL_SELECTOR_QUERY, String
                    .format("%s=%s", KUBERNETES_LABEL_APP, appId));
        }

        sendRequest(Action.GET, uri, null, context, completionHandler);
    }

    public void createEntity(KubernetesDescription description, KubernetesContext context,
            CompletionHandler completionHandler) throws IOException {
        URI uri = ApiUtil.buildKubernetesFactoryUri(description, context);

        sendRequest(Action.POST, uri, description.getKubernetesEntityAsJson(), context,
                completionHandler);
    }

    public void deleteEntity(String kubernetesSelfLink, KubernetesContext context, CompletionHandler
            completionHandler) {
        URI uri = ApiUtil.buildKubernetesUri(kubernetesSelfLink, context);

        sendRequest(Action.DELETE, uri, null, context, completionHandler);
    }

    public void fetchLogs(String logLink, KubernetesContext context, CompletionHandler
            completionHandler) {

        URI uri = ApiUtil.buildKubernetesUri(logLink, context);

        sendRequest(Action.GET, uri, null, context, completionHandler);
    }

    public void inspectEntity(String kubernetesSelfLink, KubernetesContext context,
            CompletionHandler completionHandler) {

        URI uri = ApiUtil.buildKubernetesUri(kubernetesSelfLink, context);

        sendRequest(Action.GET, uri, null, context, completionHandler);
    }

    private void sendRequest(Service.Action action, URI uri, Object body, KubernetesContext context,
            CompletionHandler completionHandler) {
        Operation op = Operation.createGet(uri)
                .setAction(action)
                .setBody(body)
                .setCompletion(completionHandler);

        prepareRequest(op, context);
        serviceClient.send(op);
    }

    private boolean isSecure(URI uri) {
        assertNotNull(uri, "uri");
        return UriUtils.HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme());
    }

}
