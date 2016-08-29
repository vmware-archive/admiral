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

package com.vmware.admiral.adapter.etcd.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.vmware.admiral.adapter.etcd.service.KVStoreService.KVNode;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * Stateless services that emulates the etcd API for KV store management.
 * See https://coreos.com/etcd/docs/latest/api.html
 */
public class EtcdEmulatorService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_ETCD_KV;

    private static final String WAIT_PARAM = "wait";
    private static final String WAIT_INDEX_PARAM = "waitIndex";

    private volatile KVStoreSubscriptionManager subscriptionManager;

    public static class EtcdNode {
        public String key;
        public String value;
        public Boolean dir;
        public Date expiration;
        public Long ttl;
        public Long modifiedIndex;
        public Long createdIndex;
        public ArrayList<EtcdNode> nodes;
    }

    public static class EtcdNodeResult {
        String action;
        EtcdNode node;
        EtcdNode prevNode;
    }

    public static class EtcdNodeError {
        int errorCode;
        String message;
        String cause;
    }

    public EtcdEmulatorService() {
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    public void handleStart(Operation startPost) {
        subscriptionManager = new KVStoreSubscriptionManager((e) -> {
            if (e != null) {
                startPost.fail(e);
            } else {
                startPost.complete();
            }
        }, this);
    }

    @Override
    public void handleGet(Operation get) {
        final String key = EtcdUtils.getEtcdKey(get);

        if ((key == null) || (key.isEmpty())) {
            handleGetSearch("", (r, e) -> {
                if (e != null) {
                    get.fail(e);
                    return;
                }
                EtcdUtils.processResultAndComplete(get, r.documents);
            });
            return;
        }

        Map<String, String> queryParams = UriUtils.parseUriQueryParams(get.getUri());
        if (queryParams.containsKey(WAIT_PARAM) || queryParams.containsKey(WAIT_INDEX_PARAM)) {
            waitForChangeOnLinkAndSubpath(KVStoreFactoryService.SELF_LINK + key, (v) -> {
                handleGetActual(get, key);
            });
        } else {
            handleGetActual(get, key);
        }
    }

    private void handleGetActual(Operation get, String key) {

        sendRequest(Operation.createGet(this, KVStoreFactoryService.SELF_LINK + key)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        if (e instanceof ServiceNotFoundException) {
                            handleGetSearch(key, (res, ex) -> {
                                if (ex != null) {
                                    get.fail(ex);
                                    return;
                                }
                                EtcdUtils.processResultAndComplete(get, res.documents);
                            });
                            return;
                        } else {
                            get.fail(e);
                            return;
                        }
                    }
                    handleGetSearch(key, (res, ex) -> {
                        if (ex != null) {
                            get.fail(ex);
                            return;
                        }

                        if (res.documents != null) {
                            EtcdUtils.processResultAndComplete(get, res.documents);
                        } else {
                            EtcdUtils.processResultAndComplete(get, o.getBody(KVNode.class), null);
                        }
                    });
                }));
    }

    private void handleGetSearch(String key,
            BiConsumer<ServiceDocumentQueryResult, Throwable> callback) {

        final String searchKey = KVStoreFactoryService.SELF_LINK + key;

        URI u = UriUtils.buildDocumentQueryUri(getHost(),
                UriUtils.buildUriPath(searchKey, UriUtils.URI_WILDCARD_CHAR),
                true,
                false,
                EnumSet.of(ServiceOption.NONE));

        Operation query = Operation.createGet(u).setCompletion(
                (o, e) -> {
                    if (e != null) {
                        callback.accept(null, e);
                        return;
                    }
                    callback.accept(o.getBody(ServiceDocumentQueryResult.class), null);
                });

        sendRequest(query);
    }

    @Override
    public void handlePut(Operation put) {

        String key = EtcdUtils.getEtcdKey(put);

        if (isRootDir(put, key)) {
            return;
        }

        sendRequest(Operation.createGet(this, KVStoreFactoryService.SELF_LINK + key)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                if (e instanceof ServiceNotFoundException) {
                                    doPut(put, null);
                                    return;
                                } else {
                                    put.fail(e);
                                    return;
                                }
                            }
                            doPut(put, o.getBody(KVNode.class));
                        }));
    }

    private void doPut(Operation put, KVNode prevNode) {
        KVNode node = EtcdUtils.getKVNode(put);

        sendRequest(OperationUtil
                .createForcedPost(this, KVStoreFactoryService.SELF_LINK)
                .setBody(node)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        put.fail(e);
                        return;
                    }
                    EtcdUtils.processResultAndComplete(put, o.getBody(KVNode.class),
                            prevNode);
                }));
    }

    @Override
    public void handleDelete(Operation delete) {

        String key = EtcdUtils.getEtcdKey(delete);

        if (isRootDir(delete, key)) {
            return;
        }

        sendRequest(Operation.createGet(this, KVStoreFactoryService.SELF_LINK + key)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        if (e instanceof ServiceNotFoundException) {
                            EtcdUtils.processNoResults(delete, key);
                            return;
                        } else {
                            delete.fail(e);
                            return;
                        }
                    }
                    doDelete(delete, key, o.getBody(KVNode.class));
                }));
    }

    private void doDelete(Operation delete, String key, KVNode prevNode) {
        sendRequest(Operation.createDelete(this, KVStoreFactoryService.SELF_LINK + key)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        delete.fail(e);
                        return;
                    }
                    EtcdUtils.processResultAndComplete(delete, null, prevNode);
                }));
    }

    private static boolean isRootDir(Operation op, String key) {
        if ("".equals(key) || "/".equals(key)) {
            EtcdNodeError error = EtcdUtils.newErrorRootReadOnly();
            op.setBodyNoCloning(error);
            op.setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON);
            op.setStatusCode(Operation.STATUS_CODE_FORBIDDEN);
            op.complete();
            return true;
        }
        return false;
    }

    @Override
    public void handlePost(Operation post) {
        validateAndComplete(post);
    }

    @Override
    public void handleOptions(Operation op) {
        validateAndComplete(op);
    }

    @Override
    public void handlePatch(Operation op) {
        validateAndComplete(op);
    }

    private void validateAndComplete(Operation op) {
        if (!op.getUri().getPath().startsWith(getSelfLink())) {
            op.fail(new IllegalArgumentException("request must start with self link"));
            return;
        }
        op.complete();
    }

    private void waitForChangeOnLinkAndSubpath(String link, Consumer<Void> callback) {
        subscriptionManager.addSubscriber((node) -> {
            if (node.documentSelfLink.startsWith(link)) {
                callback.accept(null);
                return true;
            }
            return false;
        });
    }

    private static class KVStoreSubscriptionManager {
        private List<Function<KVNode, Boolean>> subscribers = new ArrayList<>();

        public KVStoreSubscriptionManager(Consumer<Throwable> callback, Service service) {
            service.getHost().registerForServiceAvailability(
                    (o, e) -> {
                        if (e != null) {
                            callback.accept(e);
                            return;
                        }

                        CompletionHandler h = (op, ex) -> {
                            callback.accept(ex);
                        };

                        Operation subscribeToKvStore = Operation
                                .createPost(
                                        UriUtils.buildSubscriptionUri(service.getHost(),
                                                KVStoreFactoryService.SELF_LINK))
                                .setCompletion(h)
                                .setReferer(service.getUri());
                        service.getHost().startSubscriptionService(subscribeToKvStore,
                                handlKVStoreNotification());
                    }, KVStoreFactoryService.SELF_LINK);
        }

        public void addSubscriber(Function<KVNode, Boolean> subscriber) {
            subscribers.add(subscriber);
        }

        private Consumer<Operation> handlKVStoreNotification() {
            return (notifyOp) -> {
                if (notifyOp.hasBody()) {
                    KVNode body = notifyOp.getBody(KVNode.class);

                    Iterator<Function<KVNode, Boolean>> it = subscribers.iterator();
                    while (it.hasNext()) {
                        if (it.next().apply(body)) {
                            it.remove();
                        }
                    }
                }
            };
        }

    }
}