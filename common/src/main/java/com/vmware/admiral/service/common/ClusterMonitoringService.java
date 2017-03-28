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

package com.vmware.admiral.service.common;

import static com.vmware.xenon.services.common.NodeState.NodeStatus.AVAILABLE;
import static com.vmware.xenon.services.common.NodeState.NodeStatus.UNAVAILABLE;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.NodeSelectorState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.NodeGroupService.NodeGroupState;
import com.vmware.xenon.services.common.NodeGroupService.UpdateQuorumRequest;
import com.vmware.xenon.services.common.NodeState;
import com.vmware.xenon.services.common.NodeState.NodeStatus;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Simple stateless service that follows the ConsistentHashingNodeSelectorService pattern of having
 * a subscription to changes in the node group information to update the quorum to one in case
 * something goes wrong on a cluster and there's only one node available.
 */
public class ClusterMonitoringService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.CONFIG + "/cluster-monitoring";

    // Cached node group state. Refreshed during maintenance
    private NodeGroupState cachedGroupState;

    // Cached initial state. This service has "soft" state: Its configured on start and then its
    // state is immutable.
    // If the service host restarts, all state is lost, by design.
    // Note: This is not a recommended pattern! Regular services must not use instanced fields
    private NodeSelectorState cachedState;

    @Override
    public void handleStart(Operation start) {
        if (DeploymentProfileConfig.getInstance().isTest()) {
            start.complete();
            return;
        }

        logInfo("handleStart");

        NodeSelectorState state = null;
        if (!start.hasBody()) {
            state = new NodeSelectorState();
            state.nodeGroupLink = ServiceUriPaths.DEFAULT_NODE_GROUP;
        } else {
            state = start.getBody(NodeSelectorState.class);
        }
        state.documentSelfLink = getSelfLink();
        state.documentKind = Utils.buildKind(NodeSelectorState.class);
        state.documentOwner = getHost().getId();
        this.cachedState = state;
        startHelperServices(start);
    }

    private void startHelperServices(Operation op) {
        AtomicInteger remaining = new AtomicInteger(2);
        CompletionHandler h = (o, e) -> {
            if (e != null) {
                op.fail(e);
                return;
            }
            if (remaining.decrementAndGet() != 0) {
                return;
            }
            op.complete();
        };

        Operation subscribeToNodeGroup = Operation.createPost(
                UriUtils.buildSubscriptionUri(getHost(), this.cachedState.nodeGroupLink))
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_SKIPPED_NOTIFICATIONS)
                .setCompletion(h)
                .setReferer(getUri());
        getHost().startSubscriptionService(subscribeToNodeGroup, handleNodeGroupNotification());

        // we subscribe to avoid GETs on node group state, per operation, but we need to have the
        // initial node group state, before service is available.
        sendRequest(Operation.createGet(this, this.cachedState.nodeGroupLink).setCompletion(
                (o, e) -> {
                    if (e == null) {
                        NodeGroupState ngs = o.getBody(NodeGroupState.class);
                        updateCachedNodeGroupState(ngs, null);
                    } else {
                        logSevere(e);
                    }
                    h.handle(o, e);
                }));
    }

    private Consumer<Operation> handleNodeGroupNotification() {
        return (notifyOp) -> {
            notifyOp.complete();

            NodeGroupState ngs = null;
            if (notifyOp.getAction() == Action.PATCH) {
                UpdateQuorumRequest bd = notifyOp.getBody(UpdateQuorumRequest.class);
                if (UpdateQuorumRequest.KIND.equals(bd.kind)) {
                    updateCachedNodeGroupState(null, bd);
                    return;
                }
            } else if (notifyOp.getAction() != Action.POST) {
                return;
            }

            ngs = notifyOp.getBody(NodeGroupState.class);
            if (ngs.nodes == null || ngs.nodes.isEmpty()) {
                return;
            }
            updateCachedNodeGroupState(ngs, null);
        };
    }

    private void updateCachedNodeGroupState(NodeGroupState ngs, UpdateQuorumRequest quorumUpdate) {

        Operation updateQuorumOperation = null;

        if (ngs != null) {
            NodeGroupState currentState = this.cachedGroupState;

            if (currentState != null && currentState.nodes.size() != ngs.nodes.size()) {
                logInfo("Node count update: %d", ngs.nodes.size());
            }

            int nowUnavailable = countNodesWithStatus(ngs, UNAVAILABLE, true);
            int beforeUnavailable = countNodesWithStatus(currentState, UNAVAILABLE, true);
            int nowAvailable = countNodesWithStatus(ngs, AVAILABLE, true);

            if ((nowUnavailable > 0) // some node is unavailable now
                    && (nowUnavailable > beforeUnavailable) // it wasn't unavailable before
                    && isThisSingleNodeAvailable(ngs)) { // am I the only one available?

                // One working node left now and it's me... setting quorum to 1. Other scenarios
                // should be handled by Xenon.

                updateQuorumOperation = createUpdateQuorumOperation(nowAvailable);

            // xenon removes unavailable nodes before excution of this task
            } else if (beforeUnavailable > nowUnavailable) {
                updateQuorumOperation = createUpdateQuorumOperation(nowAvailable);
            }
        } else {
            logInfo("Quorum update: %d", quorumUpdate.membershipQuorum);
        }

        try {
            long now = Utils.getNowMicrosUtc();
            synchronized (this.cachedState) {
                if (quorumUpdate != null) {
                    this.cachedState.documentUpdateTimeMicros = now;
                    this.cachedState.membershipQuorum = quorumUpdate.membershipQuorum;
                    if (this.cachedGroupState != null) {
                        this.cachedGroupState.nodes.get(
                                getHost().getId()).membershipQuorum = quorumUpdate.membershipQuorum;
                    }
                    return;
                }

                if (this.cachedGroupState == null) {
                    this.cachedGroupState = ngs;
                }

                if (this.cachedGroupState.documentUpdateTimeMicros <= ngs.documentUpdateTimeMicros) {
                    this.cachedState.documentUpdateTimeMicros = now;
                    this.cachedState.membershipUpdateTimeMicros = ngs.membershipUpdateTimeMicros;
                    this.cachedGroupState = ngs;
                }
            }
        } finally {
            if (updateQuorumOperation != null) {
                logInfo("Sending update quorum request...");
                sendRequest(updateQuorumOperation);
            }
        }
    }

    private int countNodesWithStatus(NodeGroupState group, NodeStatus status, boolean equals) {
        if (group == null || group.nodes == null) {
            return 0;
        }
        int count = 0;
        for (NodeState node : group.nodes.values()) {
            if (((node.status == status) && equals) || ((node.status != status) && !equals)) {
                count++;
            }
        }
        return count;
    }

    private boolean isThisSingleNodeAvailable(NodeGroupState group) {
        if (countNodesWithStatus(group, UNAVAILABLE, false) == 1) {
            String thisHostUri = getHost().getUri().toString();
            for (NodeState node : group.nodes.values()) {
                if ((node.status != UNAVAILABLE)
                        && node.groupReference.toString().startsWith(thisHostUri)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Operation createUpdateQuorumOperation(int availableNodes) {

        UpdateQuorumRequest request = new UpdateQuorumRequest();
        request.isGroupUpdate = true;
        request.kind = UpdateQuorumRequest.KIND;
        request.membershipQuorum = (availableNodes / 2) + 1;

        logInfo("Updating membershipQuorum to %d", request.membershipQuorum);

        return Operation.createPatch(this, this.cachedState.nodeGroupLink)
                .setBody(request)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logSevere(e);
                            } else {
                                logInfo("Update quorum request sent!");
                            }
                        });
    }

}
