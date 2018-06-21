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

package com.vmware.photon.controller.model.tasks;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceSubscriptionState.ServiceSubscriber;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.ReliableSubscriptionService;
import com.vmware.xenon.services.common.TaskService.TaskServiceState;

/**
 * Utility functions used in provisioning hosts.
 */
public class TaskUtils {

    /**
     * Verify if IP string is an IPv4 address.
     *
     * @param IP
     *            IP to verify
     * @throws IllegalArgumentException
     */
    public static void isValidInetAddress(String IP) throws IllegalArgumentException {

        // Opened issue #84 to track proper validation
        if (IP == null || IP.isEmpty()) {
            throw new IllegalArgumentException("IP is missing or empty");
        }

        if (IP.contains(":")) {
            // implement IPv6 validation
        } else {
            String[] segments = IP.split("\\.");
            if (segments.length != 4) {
                throw new IllegalArgumentException("IP does not appear valid:" + IP);
            }
            // it appears to be literal IP, its safe to use the getByName method
            try {
                InetAddress.getByName(IP);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    /*
     * method takes a string that can either be a subnet or ip address and ensures that it falls in
     * the range of RFC-1918 addresses
     */
    public static void isRFC1918(String subnetAddress) throws IllegalArgumentException {
        String address = null;
        InetAddress ipAddress;

        if (subnetAddress == null || subnetAddress.isEmpty()) {
            throw new IllegalArgumentException("IP or subnet is missing or empty");
        }

        if (subnetAddress.contains("/")) {
            String[] netAddr = subnetAddress.split("/");
            address = netAddr[0];
        }

        // validate the IP to start...
        isValidInetAddress(address);

        try {
            ipAddress = InetAddress.getByName(address);
        } catch (Throwable t) {
            throw new IllegalArgumentException(t.getMessage());
        }

        if (!ipAddress.isSiteLocalAddress()) {
            throw new IllegalArgumentException("must be an RFC-1918 address or CIDR");
        }
    }

    /**
     * Translate a MAC to its canonical format.
     *
     * @param mac
     * @throws java.lang.IllegalArgumentException
     */
    public static String normalizeMac(String mac) throws IllegalArgumentException {
        mac = mac.replaceAll("[:-]", "");
        mac = mac.toLowerCase();
        return mac;
    }

    /**
     * Verify if CIDR string is a valid CIDR address.
     *
     * @param network
     *            CIDR to verify
     * @throws IllegalArgumentException
     */
    public static void isCIDR(String network) throws IllegalArgumentException {
        String[] hostMask = network.split("/");
        if (hostMask.length != 2) {
            throw new IllegalArgumentException("subnetAddress is not a CIDR");
        }

        isValidInetAddress(hostMask[0]);

        // Mask must be < 32
        if (Integer.parseUnsignedInt(hostMask[1]) > 32) {
            throw new IllegalArgumentException("CIDR mask may not be larger than 32");
        }
    }

    /**
     * Issue a patch request to the specified service
     *
     * @param service
     *            Service to issue the patch to
     * @param body
     *            Patch body
     */
    public static void sendPatch(StatefulService service, Object body) {
        Operation patch = Operation
                .createPatch(service.getUri())
                .setBody(body);
        service.sendRequest(patch);
    }

    /**
     * Patch a service to failure after logging all errors
     *
     * @param service
     *            Service to patch
     * @param tList
     *            List of throwable objects
     */
    public static void sendFailurePatch(StatefulService service, TaskServiceState taskState,
            Collection<Throwable> tList) {
        Throwable errorToPatch = null;
        for (Throwable t : tList) {
            errorToPatch = t;
            service.logWarning(() -> String.format("Operation failed: %s", Utils.toString(t)));
        }
        sendFailurePatch(service, taskState, errorToPatch);
    }

    /**
     * Patch a service to failure
     *
     * @param service
     *            Service to patch
     * @param t
     *            Throwable object
     */
    public static void sendFailurePatch(StatefulService service, TaskServiceState taskState,
            Throwable t) {
        TaskState state = new TaskState();
        state.stage = TaskStage.FAILED;
        state.failure = Utils.toServiceErrorResponse(t);
        service.logWarning(() -> String.format("Operation failed: %s", Utils.toString(t)));
        taskState.taskInfo = state;
        sendPatch(service, taskState);
    }

    /**
     * Create a TaskState object with the specified stage
     *
     * @param stage
     *            Stage for the TaskState object
     * @return
     */
    public static TaskState createTaskState(TaskStage stage) {
        TaskState tState = new TaskState();
        tState.stage = stage;
        return tState;
    }

    public static URI getAdapterUri(StatefulService service, AdapterTypePath adapterTypePath,
            String endpointType) {
        return UriUtils.buildUri(
                ServiceHost.LOCAL_HOST,
                service.getHost().getPort(),
                adapterTypePath.adapterLink(endpointType), null);
    }

    public static void subscribeToNotifications(StatefulService service,
            Consumer<Operation> notificationConsumer, String taskLink) {
        ServiceSubscriber subscribeBody = new ServiceSubscriber();
        subscribeBody.replayState = true;
        subscribeBody.usePublicUri = true;
        Operation subscribeOp = Operation
                .createPost(service, taskLink)
                .setReferer(service.getUri())
                .setCompletion(
                        (regOp, regEx) -> {
                            if (regEx != null) {
                                sendFailureSelfPatch(service, regEx);
                                return;
                            }
                        });
        ReliableSubscriptionService notificationTarget = ReliableSubscriptionService.create(
                subscribeOp, subscribeBody, notificationConsumer);
        service.getHost().startSubscriptionService(subscribeOp,
                notificationTarget, subscribeBody);
    }

    /**
     * handle subscriptions from multiple services 1. Mark operation as complete 2. If the operation
     * is not PUT or POST, return 3. If the task has failed, send failure patch to service 4. Update
     * the list of services from which we have received notification 5. Unsubscribe from
     * notifications, optionally delete the task that raised the notification 6. If we have received
     * the expected number of notifications, patch the next state back
     *
     * @param service
     *            Stateful provisioning service
     * @param update
     *            Notification operation
     * @param notificationTaskLink
     *            Self link of the task that raised the notification
     * @param opTaskState
     *            TaskState of the service that raised the notification
     * @param expectedNotificationCount
     *            Expected number of notifications
     * @param returnState
     *            The next state for the StatefulService
     * @param finishedTaskLinks
     *            Set of self links for services from which we have received notification
     * @param deleteTask
     *            flag to delete the service that raised the notification
     */
    public static void handleSubscriptionNotifications(StatefulService service, Operation update,
            String notificationTaskLink, TaskState opTaskState,
            int expectedNotificationCount, Object returnState, Set<String> finishedTaskLinks,
            boolean deleteTask) {
        int finishedTaskCount;

        update.complete();

        if ((update.getAction() != Action.PATCH && update.getAction() != Action.PUT)) {
            return;
        }

        // Fail if task was cancelled or has failed
        if (TaskState.isCancelled(opTaskState)
                || TaskState.isFailed(opTaskState)) {
            if (update.getBodyRaw() != null) {
                sendFailureSelfPatch(service,
                        new IllegalStateException("Operation failed:"
                                + Utils.toJson(update.getBodyRaw())));
            } else {
                sendFailureSelfPatch(service,
                        new IllegalStateException("Operation failed:"
                                + "Operation cancelled or failed while deleting computes"));
            }

            if (deleteTask) {
                service.sendRequest(Operation
                        .createDelete(
                                service, notificationTaskLink)
                        .setBody(new ServiceDocument()));
            }
            return;
        }

        // Ignore if task has not finished yet
        if (!TaskState.isFinished(opTaskState)) {
            return;
        }

        // Ignore if task has already been seen
        synchronized (finishedTaskLinks) {
            if (!finishedTaskLinks.add(notificationTaskLink)) {
                return;
            }

            // Retrieve size in synchronized block to prevent racing
            finishedTaskCount = finishedTaskLinks.size();
        }

        Operation deleteOp = Operation.createDelete(service, notificationTaskLink)
                .setReferer(service.getUri())
                .setCompletion(
                        (delOp, delEx) -> {
                            if (delEx != null) {
                                service.logWarning(() -> String.format("Stopping subscriber failed"
                                                + " %s", Utils.toString(delEx)));
                                return;
                            }
                        });
        service.getHost().stopSubscriptionService(deleteOp,
                UriUtils.buildPublicUri(service.getHost(), update.getUri().getPath()));

        if (deleteTask) {
            service.sendRequest(Operation
                    .createDelete(
                            service, notificationTaskLink)
                    .setBody(new ServiceDocument()));
        }

        // patch service with return state when we have seen all tasks finish
        if (finishedTaskCount == expectedNotificationCount) {
            // Patch back the return state
            sendPatch(service, returnState);
        }
    }

    public static boolean isFailedOrCancelledTask(TaskServiceState state) {
        return state.taskInfo != null &&
                (TaskStage.FAILED == state.taskInfo.stage ||
                        TaskStage.CANCELLED == state.taskInfo.stage);
    }

    /**
     * Send a failure patch to the specified service
     *
     * @param service
     *            service to send the patch to
     * @param e
     *            Exception
     */
    private static void sendFailureSelfPatch(StatefulService service, Throwable e) {
        // It looks like Xenon can't handle correctly serializing abstract classes, so we have to
        // use a simple class which extend the abstract.
        StatefulTaskDocument body = new StatefulTaskDocument();
        body.taskInfo = new TaskState();
        body.taskInfo.stage = TaskStage.FAILED;
        body.taskInfo.failure = Utils.toServiceErrorResponse(e);
        service.logWarning(() -> String.format("Operation failed: %s", Utils.toString(e)));
        sendPatch(service, body);
    }

    private static class StatefulTaskDocument extends TaskServiceState {
    }

    /**
     * Inject user identity into operation context.
     *
     * @param service the service invoking the operation
     * @param op operation for which the auth context needs to be set
     * @param userServicePath user document link
     * @throws GeneralSecurityException any generic security exception
     */
    public static void assumeIdentity(StatefulService service, Operation op,
            String userServicePath)
            throws GeneralSecurityException {
        Claims.Builder builder = new Claims.Builder();
        builder.setSubject(userServicePath);
        Claims claims = builder.getResult();
        String token = service.getTokenSigner().sign(claims);

        // Setting the AuthContext to null, so that xenon uses the token instead.
        service.setAuthorizationContext(op, null);
        op.addRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER, token);
    }
}
