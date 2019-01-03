/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.pks;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.net.ssl.TrustManager;

import com.vmware.admiral.adapter.pks.entities.PKSCluster;
import com.vmware.admiral.adapter.pks.entities.PKSErrorResponse;
import com.vmware.admiral.adapter.pks.entities.PKSPlan;
import com.vmware.admiral.adapter.pks.entities.UAATokenResponse;
import com.vmware.admiral.common.util.DeferredUtils;
import com.vmware.admiral.common.util.DelegatingX509KeyManager;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.kubernetes.entities.config.KubeConfig;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class PKSRemoteClientService {

    private static final Logger logger = Logger.getLogger(PKSRemoteClientService.class.getName());

    private static final String UAA_GRANT_TYPE = "grant_type";
    private static final String UAA_GRANT_TYPE_PASSWORD = "password";
    private static final String UAA_RESPONSE_TYPE = "response_type";
    private static final String UAA_RESPONSE_TYPE_TOKEN = "token";
    private static final String UAA_USERNAME = "username";
    private static final String UAA_PASSWORD = "password";
    private static final String UAA_CLIENT_ID = "client_id";
    private static final String UAA_CLIENT_ID_CLI = "pks_cli";
    private static final String UAA_CLIENT_SECRET = "client_secret";
    private static final String UAA_CLIENT_SECRET_CLI = "";
    private static volatile PKSRemoteClientService INSTANCE = null;
    private final ServiceClient serviceClient;
    private final DelegatingX509KeyManager keyManager = new DelegatingX509KeyManager();
    private ServerX509TrustManager trustManager;
    private ServiceHost host;

    public PKSRemoteClientService(TrustManager trustManager, ServiceHost host) {
        if (INSTANCE != null) {
            throw new IllegalStateException("PKS client has been already instantiated");
        }

        this.serviceClient = ServiceClientFactory.createServiceClient(trustManager, keyManager);

        if (trustManager instanceof ServerX509TrustManager) {
            this.trustManager = (ServerX509TrustManager) trustManager;
        }
        this.host = host;

        INSTANCE = this;
    }

    public static PKSRemoteClientService getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("PKS client is not initialized");
        }
        return INSTANCE;
    }

    private static String encodeParameters(HashMap<String, String> m) {
        StringBuilder sb = new StringBuilder();

        if (m != null) {
            m.entrySet().forEach(entry -> sb.append(encodeQueryParam(entry)));
            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
            }
        }

        return sb.toString();
    }

    private static String encodeQueryParam(Map.Entry<String, String> kv) {
        return encode(kv.getKey())
                + UriUtils.URI_QUERY_PARAM_KV_CHAR
                + encode(kv.getValue())
                + UriUtils.URI_QUERY_PARAM_LINK_CHAR;
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, Utils.CHARSET);
        } catch (UnsupportedEncodingException e) {
            // We cannot reach here because we are supplying supported encoding name as parameter.
        }
        return null;
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, Utils.CHARSET).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            // We cannot reach here because we are supplying supported encoding name as parameter.
        }
        return null;
    }

    public DeferredResult<List<PKSCluster>> getClusters(PKSContext ctx) {
        try {
            URI uri = UriUtils.buildUri(ctx.pksAPIUri, "v1/clusters");
            Operation op = buildGetOperation(uri, ctx);

            return sendWithDeferredResult(op)
                    .thenApply(o -> {
                        PKSCluster[] clusters = o.getBody(PKSCluster[].class);
                        logger.fine(() -> String.format("Got response from %s for clusters : %s",
                                ctx.pksAPIUri, Utils.toJson(clusters)));
                        return Arrays.asList(clusters);
                    })
                    .exceptionally(t -> {
                        throw DeferredUtils.logErrorAndThrow(t,
                                e -> String.format("Error getting PKS clusters from %s, reason: %s",
                                        ctx.pksAPIUri, e.getMessage()),
                                getClass());
                    });
        } catch (Exception e) {
            logger.severe(String.format("Error getting PKS clusters from %s, reason: %s",
                    ctx != null ? ctx.pksAPIUri : "null-context", e.getMessage()));
            return DeferredResult.failed(e);
        }
    }

    public DeferredResult<PKSCluster> getCluster(PKSContext ctx, String cluster) {
        try {
            URI uri = UriUtils.buildUri(ctx.pksAPIUri, "v1/clusters", urlEncode(cluster));
            Operation op = buildGetOperation(uri, ctx);

            return sendWithDeferredResult(op)
                    .thenApply(o -> {
                        PKSCluster result = o.getBody(PKSCluster.class);
                        logger.fine(() -> String.format("Got response from %s for cluster %s : %s",
                                ctx.pksAPIUri, cluster, Utils.toJson(result)));
                        return result;
                    })
                    .exceptionally(t -> {
                        throw DeferredUtils.logErrorAndThrow(t,
                                e -> String.format("Error getting PKS cluster '%s' from %s,"
                                        + " reason: %s",
                                        cluster, ctx.pksAPIUri, e.getMessage()),
                                getClass());
                    });
        } catch (Exception e) {
            logger.severe(String.format("Error getting PKS cluster from %s, reason: %s",
                    ctx != null ? ctx.pksAPIUri : "null-context", e.getMessage()));
            return DeferredResult.failed(e);
        }
    }

    public DeferredResult<Void> deleteCluster(PKSContext ctx, String cluster) {
        try {
            URI uri = UriUtils.buildUri(ctx.pksAPIUri, "v1/clusters", urlEncode(cluster));
            Operation op = buildDeleteOperation(uri, ctx);

            return sendWithDeferredResult(op)
                    .thenAccept(o -> {
                        if (o.getStatusCode() == HttpURLConnection.HTTP_NO_CONTENT
                                || o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                            return;
                        } else {
                            String msg = String.format("Delete PKS cluster failed, status code: %d",
                                    o.getStatusCode());
                            PKSException e = new PKSException(msg, null, o.getStatusCode());
                            throw DeferredUtils.logErrorAndThrow(e, t -> msg, getClass());
                        }
                    })
                    .exceptionally(t -> {
                        throw DeferredUtils.logErrorAndThrow(t,
                                e -> String.format("Error deleting PKS cluster '%s' from %s,"
                                        + " reason: %s",
                                        cluster, ctx.pksAPIUri, e.getMessage()),
                                getClass());
                    });
        } catch (Exception e) {
            logger.severe(String.format("Error deleting PKS cluster from %s, reason: %s",
                    ctx != null ? ctx.pksAPIUri : "null-context", e.getMessage()));
            return DeferredResult.failed(e);
        }
    }

    public DeferredResult<List<PKSPlan>> getPlans(PKSContext ctx) {
        try {
            URI uri = UriUtils.buildUri(ctx.pksAPIUri, "v1/plans");
            Operation op = buildGetOperation(uri, ctx);

            return sendWithDeferredResult(op)
                    .thenApply(o -> {
                        PKSPlan[] plans = o.getBody(PKSPlan[].class);
                        logger.fine(() -> String.format("Got response from %s for plans : %s",
                                ctx.pksAPIUri, Utils.toJson(plans)));
                        return Arrays.asList(plans);
                    })
                    .exceptionally(t -> {
                        throw DeferredUtils.logErrorAndThrow(t,
                                e -> String.format("Error getting PKS plans from %s, reason: %s",
                                        ctx.pksAPIUri, e.getMessage()),
                                getClass());
                    });
        } catch (Exception e) {
            logger.severe(String.format("Error getting PKS plans from %s, reason: %s",
                    ctx != null ? ctx.pksAPIUri : "null-context", e.getMessage()));
            return DeferredResult.failed(e);
        }
    }

    public DeferredResult<KubeConfig> createUser(PKSContext ctx, String cluster) {
        try {
            URI uri = UriUtils.buildUri(ctx.pksAPIUri, "v1/clusters", urlEncode(cluster), "binds");
            Operation op = buildPostOperation(uri, ctx);

            return sendWithDeferredResult(op)
                    .thenApply(o -> {
                        KubeConfig config = o.getBody(KubeConfig.class);
                        logger.fine(() -> String.format("Got response from %s for create user : %s",
                                ctx.pksAPIUri, Utils.toJson(config)));
                        return config;
                    })
                    .exceptionally(t -> {
                        throw DeferredUtils.logErrorAndThrow(t,
                                e -> String.format("Error creating user from %s, reason: %s",
                                        ctx.pksAPIUri, e.getMessage()),
                                getClass());
                    });
        } catch (Exception e) {
            logger.severe(String.format("Error creating user from %s, reason: %s",
                    ctx != null ? ctx.pksAPIUri : "null-context", e.getMessage()));
            return DeferredResult.failed(e);
        }
    }

    public DeferredResult<PKSCluster> createCluster(PKSContext ctx, PKSCluster cluster) {
        try {
            URI uri = UriUtils.buildUri(ctx.pksAPIUri, "v1/clusters");
            Operation op = buildPostOperation(uri, ctx)
                    .setBody(cluster);

            return sendWithDeferredResult(op)
                    .thenApply(o -> {
                        if (o.getStatusCode() == Operation.STATUS_CODE_ACCEPTED) {
                            PKSCluster result = o.getBody(PKSCluster.class);
                            logger.fine(
                                    () -> String.format("Got response from %s for cluster %s : %s",
                                            ctx.pksAPIUri, cluster, Utils.toJson(result)));
                            return result;
                        }
                        String msg = String.format("Create PKS cluster failed, status code: %d",
                                o.getStatusCode());

                        PKSException e = new PKSException(msg, null, o.getStatusCode());
                        throw DeferredUtils.logErrorAndThrow(e, t -> msg, getClass());
                    })
                    .exceptionally(t -> {
                        throw DeferredUtils.logErrorAndThrow(t,
                                e -> String.format("Error creating PKS cluster '%s' from %s,"
                                        + " reason: %s",
                                        cluster.name, ctx.pksAPIUri, e.getMessage()),
                                getClass());
                    });
        } catch (Exception e) {
            logger.severe(String.format("Error getting PKS cluster from %s, reason: %s",
                    ctx != null ? ctx.pksAPIUri : "null-context", e.getMessage()));
            return DeferredResult.failed(e);
        }
    }

    public DeferredResult<Void> resizeCluster(PKSContext ctx, PKSCluster cluster) {
        try {
            URI uri = UriUtils.buildUri(ctx.pksAPIUri, "v1/clusters", urlEncode(cluster.name));
            Operation op = buildPatchOperation(uri, ctx)
                    .setBody(cluster.parameters);

            return sendWithDeferredResult(op)
                    .thenAccept(o -> {
                        if (o.getStatusCode() == HttpURLConnection.HTTP_ACCEPTED) {
                            return;
                        } else {
                            String msg = String.format(
                                    "Failed to resize PKS cluster, status code: %d",
                                    o.getStatusCode());
                            PKSException e = new PKSException(msg, null, o.getStatusCode());
                            throw DeferredUtils.logErrorAndThrow(e, t -> msg, getClass());
                        }
                    })
                    .exceptionally(t -> {
                        throw DeferredUtils.logErrorAndThrow(t,
                                e -> String.format("Error resizing PKS cluster '%s' from %s,"
                                        + " reason: %s",
                                        cluster, ctx.pksAPIUri, e.getMessage()),
                                getClass());
                    });
        } catch (Exception e) {
            logger.severe(String.format("Error resizing PKS cluster from %s, reason: %s",
                    ctx != null ? ctx.pksAPIUri : "null-context", e.getMessage()));
            return DeferredResult.failed(e);
        }
    }

    /**
     * Obtains token from UAA service.
     *
     * @param uaaEndpoint
     *            UAA service address
     * @param user
     *            username
     * @param pass
     *            password
     * @return {@link DeferredResult} with {@link UAATokenResponse} instance
     */
    public DeferredResult<UAATokenResponse> login(String uaaEndpoint, String user, String pass) {
        final DeferredResult<UAATokenResponse> deferredResult = new DeferredResult<>();

        try {
            URI uri = URI.create(uaaEndpoint);
            uri = UriUtils.buildUri(uri, "oauth/token");

            HashMap<String, String> params = new HashMap<>();
            params.put(UAA_GRANT_TYPE, UAA_GRANT_TYPE_PASSWORD);
            params.put(UAA_RESPONSE_TYPE, UAA_RESPONSE_TYPE_TOKEN);
            params.put(UAA_USERNAME, user);
            params.put(UAA_PASSWORD, pass);
            params.put(UAA_CLIENT_ID, UAA_CLIENT_ID_CLI);
            params.put(UAA_CLIENT_SECRET, UAA_CLIENT_SECRET_CLI);

            String body = encodeParameters(params);

            Operation op = buildLoginOperation(uri)
                    .setBodyNoCloning(body)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            logger.severe(() -> String.format("Error getting token for %s from %s,"
                                    + " reason: %s - %s", user, uaaEndpoint,
                                    e.getClass().getSimpleName(), e.getMessage()));
                            deferredResult.fail(e);
                            return;
                        }

                        try {
                            UAATokenResponse response = o.getBody(UAATokenResponse.class);
                            logger.fine(() -> String.format("Received token for %s from %s",
                                    user, uaaEndpoint));

                            deferredResult.complete(response);
                        } catch (Exception e1) {
                            deferredResult.fail(e1);
                        }
                    });

            serviceClient.sendRequest(op);
        } catch (Exception e) {
            logger.severe(() -> String.format("Error sending login request for %s to %s,"
                    + " reason: %s", user, uaaEndpoint, e.getMessage()));

            deferredResult.fail(e);
        }

        return deferredResult;
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

    /**
     * Creates <code>GET</code> operation initialized with authorization header.
     *
     * @param uri
     *            operation uri
     * @param ctx
     *            PKS context with the token
     * @return operation instance
     */
    private Operation buildGetOperation(URI uri, PKSContext ctx) {
        return buildOperation(Operation.createGet(uri), ctx);
    }

    /**
     * Creates <code>POST</code> operation initialized with authorization header.
     *
     * @param uri
     *            operation uri
     * @param ctx
     *            PKS context with the token
     * @return operation instance
     */
    private Operation buildPostOperation(URI uri, PKSContext ctx) {
        return buildOperation(Operation.createPost(uri), ctx);
    }

    /**
     * Creates <code>DELETE</code> operation initialized with authorization header.
     *
     * @param uri
     *            operation uri
     * @param ctx
     *            PKS context with the token
     * @return operation instance
     */
    private Operation buildDeleteOperation(URI uri, PKSContext ctx) {
        return buildOperation(Operation.createDelete(uri), ctx);
    }

    /**
     * Creates <code>PATCH</code> operation initialized with authorization header.
     *
     * @param uri
     *            operation uri
     * @param ctx
     *            PKS context with the token
     * @return operation instance
     */
    private Operation buildPatchOperation(URI uri, PKSContext ctx) {
        return buildOperation(Operation.createPatch(uri), ctx);
    }

    /**
     * Creates operation initialized with authorization header.
     *
     * @param op
     *            operation to modify
     * @param ctx
     *            PKS context with the token
     * @return operation instance
     */
    private Operation buildOperation(Operation op, PKSContext ctx) {
        return op.addRequestHeader(Operation.ACCEPT_HEADER, Operation.MEDIA_TYPE_APPLICATION_JSON)
                .addRequestHeader(Operation.AUTHORIZATION_HEADER, "Bearer " + ctx.accessToken)
                .addRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER, "")
                .setReferer(host.getPublicUri())
                .forceRemote();
    }

    /**
     * Creates operation to obtain UAA token. In case of error (status code >= 400) xenon overwrites
     * the operation body with own message and this the original response body is lost. Treat this
     * special case by appending the original response to the message of the
     * {@link ServiceErrorResponse} object.
     *
     * @param uri
     *            operation uri
     * @return operation instance
     */
    private Operation buildLoginOperation(URI uri) {
        Operation o = new Operation() {
            @Override
            public Operation setBodyNoCloning(Object b) {
                if (b instanceof ServiceErrorResponse) {
                    ((ServiceErrorResponse) b).message += " original body: " + super.getBodyRaw();
                }
                return super.setBodyNoCloning(b);
            }
        };

        o.setUri(uri)
                .setAction(Service.Action.POST)
                .setContentType(Operation.MEDIA_TYPE_APPLICATION_X_WWW_FORM_ENCODED)
                .addRequestHeader(Operation.ACCEPT_HEADER, Operation.MEDIA_TYPE_APPLICATION_JSON)
                .addRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER, "")
                .setReferer(host.getPublicUri())
                .forceRemote();

        return o;
    }

    private DeferredResult<Operation> sendWithDeferredResult(Operation op) {
        DeferredResult<Operation> deferred = new DeferredResult<>();
        op.nestCompletion((response, e) -> {
            if (e != null) {
                String errorMessage = response.getBody(PKSErrorResponse.class).message;
                PKSException p = new PKSException(
                        String.format("%s,  Error: %s", e.getMessage(), errorMessage), e,
                        response.getStatusCode());
                deferred.fail(p);
            } else {
                deferred.complete(response);
            }
        });
        serviceClient.send(op);
        return deferred;
    }

}
