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

package com.vmware.admiral.request;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.graph.ComponentRequestVisitor;
import com.vmware.admiral.request.graph.ContainerRequestVisitor;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.QueryTaskClientHelper;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.serialization.JsonMapper;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * Search for templates (CompositeDescriptions and container images)
 */
public class RequestBrokerGraphService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.REQUEST_GRAPH;
    public static final String QUERY_PARAM = "requestId";
    public static final String HOST_PARAM = "xenonHost";

    public static class Response {
        List<TaskServiceDocumentHistory> tasks;
        RequestBrokerState request;
        List<Object> componentInfos;
    }

    public static class TaskServiceDocumentHistory {
        public String documentSelfLink;
        long createdTimeMicros;
        public List<TaskServiceStageWithLink> stages;
    }

    private static class TaskServiceStage extends ServiceDocument {
        public Object taskSubStage;
        public ServiceTaskCallback serviceTaskCallback;
        public TaskState taskInfo;
        public JsonObject properties;
    }

    public static class TaskServiceStageWithLink {
        public Object taskSubStage;
        public TaskState taskInfo;
        public String documentSelfLink;
        public long documentUpdateTimeMicros;
        public TransitionSource transitionSource;
        public JsonObject properties;
    }

    public static class TransitionSource {
        public String documentSelfLink;
        public Object subStage;
        public long documentUpdateTimeMicros;
    }

    private static class TaskServiceDocumentHistoryInternal {
        String documentSelfLink;
        long createdTimeMicros;
        List<TaskServiceStage> stages;
    }

    @Override
    public void handleStart(Operation startPost) {
        super.handleStart(startPost);

        Utils.registerCustomJsonMapper(TaskServiceStage.class,
                new JsonMapper((b) -> b.registerTypeAdapter(TaskServiceStage.class,
                        new TaskServiceStageDeserializer())));
    }

    @Override
    public void handleGet(Operation get) {
        Map<String, String> queryParams = UriUtils.parseUriQueryParams(get.getUri());

        String requestId = queryParams.remove(QUERY_PARAM);
        String host = queryParams.remove(HOST_PARAM);

        AssertUtil.assertNotEmpty(requestId, QUERY_PARAM);

        List<TaskServiceDocumentHistoryInternal> foundTasks = new ArrayList<>();

        retrieveAllFromContext(requestId, host, foundTasks, (ex) -> {
            if (ex != null) {
                get.fail(ex);
            } else {
                Response r = new Response();
                r.tasks = convert(foundTasks);
                populateRequestInfos(r, r.tasks, requestId);
                get.setBody(r);
                get.complete();
            }
        });
    }

    private void retrieveAllFromContext(String requestId, String host,
            List<TaskServiceDocumentHistoryInternal> foundTasks,
            Consumer<Throwable> callback) {
        String selfLinkQuery = String.format("/request*/%s*", requestId);
        Query.Builder queryBuilder = Query.Builder.create()
                .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK, selfLinkQuery,
                        MatchType.WILDCARD)
                .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK, "/request-status/*",
                        MatchType.WILDCARD, Occurance.MUST_NOT_OCCUR);

        QueryTask q = QueryTask.Builder.create().setQuery(queryBuilder.build())
                .addOption(QueryOption.INCLUDE_ALL_VERSIONS)
                .addOption(QueryOption.INCLUDE_DELETED)
                .addOption(QueryOption.EXPAND_CONTENT).build();

        Map<String, List<TaskServiceStage>> taskVersionsBySelfLink = new HashMap<>();

        QueryTaskClientHelper<TaskServiceStage> h = QueryTaskClientHelper
                .create(TaskServiceStage.class)
                .setQueryTask(q)
                .setResultHandler((r, e) -> {
                    if (e != null) {
                        callback.accept(e);
                    } else if (r.hasResult()) {
                        TaskServiceStage result = r.getResult();
                        List<TaskServiceStage> taskVersions = taskVersionsBySelfLink
                                .get(result.documentSelfLink);
                        if (taskVersions == null) {
                            taskVersions = new ArrayList<>();
                            taskVersionsBySelfLink.put(result.documentSelfLink, taskVersions);
                        }

                        taskVersions.add(result);
                    } else {
                        if (taskVersionsBySelfLink.isEmpty()) {
                            callback.accept(null);
                        } else {
                            for (Entry<String, List<TaskServiceStage>> entry : taskVersionsBySelfLink
                                    .entrySet()) {
                                TaskServiceDocumentHistoryInternal tsdh = convert(entry.getKey(),
                                        entry.getValue());
                                foundTasks.add(tsdh);
                            }

                            callback.accept(null);
                        }
                    }

                });

        if (host != null) {
            try {
                h.setBaseUri(new URI(host));
            } catch (URISyntaxException e1) {
                callback.accept(e1);
                return;
            }
        }

        h.sendWith(getHost());
    }

    private static TaskServiceDocumentHistoryInternal convert(String taskDocumentSelfLink,
            List<TaskServiceStage> taskVersions) {
        TaskServiceDocumentHistoryInternal result = new TaskServiceDocumentHistoryInternal();
        result.documentSelfLink = taskDocumentSelfLink;

        result.stages = taskVersions.stream().sorted((t1, t2) -> {
            if (t1.documentVersion > t2.documentVersion) {
                return 1;
            } else if (t1.documentVersion < t2.documentVersion) {
                return -1;
            } else {
                return 0;
            }
        }).collect(Collectors.toList());

        TaskServiceStage firstTaskVersion = result.stages.get(0);
        result.createdTimeMicros = firstTaskVersion.documentUpdateTimeMicros;

        return result;
    }

    private static List<TaskServiceDocumentHistory> convert(
            List<TaskServiceDocumentHistoryInternal> tasks) {
        tasks.sort((t1, t2) -> {
            if (t1.createdTimeMicros > t2.createdTimeMicros) {
                return 1;
            } else if (t1.createdTimeMicros < t2.createdTimeMicros) {
                return -1;
            } else {
                return 0;
            }
        });

        List<TaskServiceDocumentHistory> result = new ArrayList<>();

        for (TaskServiceDocumentHistoryInternal t : tasks) {
            result.add(convert(t, tasks, result));
        }

        return result;
    }

    private static TaskServiceDocumentHistory convert(
            TaskServiceDocumentHistoryInternal taskHistory,
            List<TaskServiceDocumentHistoryInternal> tasks,
            List<TaskServiceDocumentHistory> currentlyConvertedTasks) {
        TaskServiceDocumentHistory result = new TaskServiceDocumentHistory();
        result.createdTimeMicros = taskHistory.createdTimeMicros;
        result.documentSelfLink = taskHistory.documentSelfLink;
        result.createdTimeMicros = taskHistory.createdTimeMicros;

        result.stages = new ArrayList<>();

        TaskServiceStage firstStage = taskHistory.stages.get(0);
        TaskServiceStageWithLink firstStageWithLink = new TaskServiceStageWithLink();
        firstStageWithLink.documentSelfLink = firstStage.documentSelfLink;
        firstStageWithLink.documentUpdateTimeMicros = firstStage.documentUpdateTimeMicros;
        firstStageWithLink.taskSubStage = firstStage.taskSubStage;
        firstStageWithLink.taskInfo = firstStage.taskInfo;
        firstStageWithLink.properties = firstStage.properties;

        if (firstStage.serviceTaskCallback != null && !firstStage.serviceTaskCallback.isEmpty()) {
            TransitionSource transitionSource = getTransitionSource(
                    firstStage.serviceTaskCallback.serviceSelfLink, taskHistory.createdTimeMicros,
                    tasks);
            firstStageWithLink.transitionSource = transitionSource;
        }

        result.stages.add(firstStageWithLink);

        TransitionSource lastTransitionSource = new TransitionSource();
        lastTransitionSource.documentSelfLink = result.documentSelfLink;
        Object lastTransitionStage = firstStageWithLink.taskSubStage;
        long lastDocumentUpdateTimeMicros = firstStageWithLink.documentUpdateTimeMicros;

        for (int i = 1; i < taskHistory.stages.size(); i++) {
            TaskServiceStage stage = taskHistory.stages.get(i);
            TaskServiceStageWithLink stageWithLink = new TaskServiceStageWithLink();
            stageWithLink.documentSelfLink = stage.documentSelfLink;
            stageWithLink.documentUpdateTimeMicros = stage.documentUpdateTimeMicros;
            stageWithLink.taskSubStage = stage.taskSubStage;
            stageWithLink.taskInfo = stage.taskInfo;
            stageWithLink.properties = stage.properties;

            TransitionSource transitionSource = new TransitionSource();
            transitionSource.documentSelfLink = result.documentSelfLink;
            transitionSource.subStage = lastTransitionStage;
            transitionSource.documentUpdateTimeMicros = lastDocumentUpdateTimeMicros;

            stageWithLink.transitionSource = transitionSource;

            result.stages.add(stageWithLink);

            lastTransitionStage = stageWithLink.taskSubStage;
            lastDocumentUpdateTimeMicros = stageWithLink.documentUpdateTimeMicros;

            if (stage.serviceTaskCallback != null
                    && !stage.serviceTaskCallback.isEmpty()
                    && (TaskState.isFinished(stage.taskInfo)
                            || TaskState.isFailed(stage.taskInfo))) {

                TransitionSource fixTransitionSource = new TransitionSource();
                fixTransitionSource.documentSelfLink = result.documentSelfLink;
                fixTransitionSource.subStage = lastTransitionStage;
                fixTransitionSource.documentUpdateTimeMicros = lastDocumentUpdateTimeMicros;

                fixupTransitionSource(stage.serviceTaskCallback, fixTransitionSource,
                        currentlyConvertedTasks);
            }
        }

        return result;
    }

    private static void fixupTransitionSource(ServiceTaskCallback callback,
            TransitionSource transitionSource,
            List<TaskServiceDocumentHistory> currentlyConvertedTasks) {
        for (TaskServiceDocumentHistory t : currentlyConvertedTasks) {
            if (t.documentSelfLink.equals(callback.serviceSelfLink)) {
                fixupTransitionSource(callback, transitionSource, t);
            }
        }
    }

    private static void fixupTransitionSource(ServiceTaskCallback callback,
            TransitionSource transitionSource, TaskServiceDocumentHistory task) {
        ServiceTaskCallbackResponse finishedResponse = callback.getFinishedResponse();
        ServiceTaskCallbackResponse failedResponseResponse = callback
                .getFailedResponse((ServiceErrorResponse) null);
        for (TaskServiceStageWithLink stage : task.stages) {
            if (equalsStages(stage, finishedResponse) ||
                    equalsStages(stage, failedResponseResponse)) {
                stage.transitionSource = transitionSource;
            }
        }
    }

    private static boolean equalsStages(TaskServiceStageWithLink stage,
            ServiceTaskCallbackResponse response) {
        return stage.taskSubStage.equals(response.taskSubStage)
                && equalsTaskStateStages(stage.taskInfo, response.taskInfo);
    }

    private static boolean equalsTaskStateStages(TaskState state1, TaskState state2) {
        if (state1 != null && state2 != null) {
            return state1.stage.equals(state2.stage);
        }

        return false;
    }

    private static TransitionSource getTransitionSource(String taskCallbackDocumentSelfLink,
            long creationTimeMicros, List<TaskServiceDocumentHistoryInternal> tasks) {
        Object lastStage = null;
        long lastDocumentUpdateTimeMicros = 0;
        TaskServiceDocumentHistoryInternal taskHistory = getTaskByLink(taskCallbackDocumentSelfLink,
                tasks);

        if (taskHistory == null || taskHistory.stages == null) {
            System.out.println("ss");
            return null;
        }

        for (TaskServiceStage task : taskHistory.stages) {
            if (task.documentUpdateTimeMicros > creationTimeMicros) {
                break;
            }

            lastStage = task.taskSubStage;
            lastDocumentUpdateTimeMicros = task.documentUpdateTimeMicros;
        }

        AssertUtil.assertNotNull(lastStage, "lastStage");

        TransitionSource source = new TransitionSource();
        source.documentSelfLink = taskCallbackDocumentSelfLink;
        source.subStage = lastStage;
        source.documentUpdateTimeMicros = lastDocumentUpdateTimeMicros;

        return source;
    }

    private static TaskServiceDocumentHistoryInternal getTaskByLink(String documentSelfLink,
            List<TaskServiceDocumentHistoryInternal> tasks) {
        for (TaskServiceDocumentHistoryInternal t : tasks) {
            if (t.documentSelfLink.equals(documentSelfLink)) {
                return t;
            }
        }

        return null;
    }

    public static final class TaskServiceStageDeserializer
            implements JsonDeserializer<TaskServiceStage> {
        @Override
        public TaskServiceStage deserialize(JsonElement json, Type type,
                JsonDeserializationContext context) throws JsonParseException {

            JsonObject jobject = (JsonObject) json;

            TaskServiceStage stage = new TaskServiceStage();
            try {
                stage.documentSelfLink = jobject.get("documentSelfLink").getAsString();
                stage.serviceTaskCallback = Utils.fromJson(jobject.get("serviceTaskCallback"),
                        ServiceTaskCallback.class);
                stage.taskInfo = Utils.fromJson(jobject.get("taskInfo"), TaskState.class);
                stage.taskSubStage = jobject.get("taskSubStage").getAsString();
                stage.documentUpdateTimeMicros = jobject.get("documentUpdateTimeMicros")
                        .getAsLong();
                stage.documentVersion = jobject.get("documentVersion").getAsLong();
                stage.properties = jobject;

                stage.properties.remove("documentSelfLink");
                stage.properties.remove("serviceTaskCallback");
                stage.properties.remove("taskInfo");
                stage.properties.remove("taskSubStage");
                stage.properties.remove("documentVersion");
                stage.properties.remove("documentUpdateTimeMicros");
                stage.properties.remove("documentEpoch");
                stage.properties.remove("documentKind");
                stage.properties.remove("documentUpdateAction");
                stage.properties.remove("documentExpirationTimeMicros");
                stage.properties.remove("documentOwner");
            } catch (Exception e) {
                System.out.println(e);
            }

            return stage;
        }
    }

    private static void populateRequestInfos(Response response,
            List<TaskServiceDocumentHistory> tasks, String requestId) {
        Map<String, TaskServiceStageWithLink> allStages = new HashMap<>();

        String requestLink = ManagementUriParts.REQUESTS + "/" + requestId;

        for (TaskServiceDocumentHistory task : tasks) {
            for (TaskServiceStageWithLink stage : task.stages) {
                allStages.put(ComponentRequestVisitor.getStageId(stage), stage);
            }

            if (task.documentSelfLink.equals(requestLink)) {
                if (task.stages.size() > 0) {
                    TaskServiceStageWithLink lastStage = task.stages.get(task.stages.size() - 1);
                    response.request = Utils.fromJson(lastStage.properties,
                            RequestBrokerState.class);
                }
            }
        }

        List<TaskServiceStageWithLink> sortedStages = new ArrayList<>(allStages.values());
        sortedStages.sort((t1, t2) -> {
            if (t1.documentUpdateTimeMicros < t2.documentUpdateTimeMicros) {
                return 1;
            } else if (t1.documentUpdateTimeMicros > t2.documentUpdateTimeMicros) {
                return -1;
            } else {
                return 0;
            }
        });

        response.componentInfos = getRequestInfos(sortedStages, allStages);
    }

    private static List<Object> getRequestInfos(List<TaskServiceStageWithLink> sortedStages,
            Map<String, TaskServiceStageWithLink> allStages) {

        List<ComponentRequestVisitor> visitors = Arrays.asList(new ContainerRequestVisitor());
        // TODO: implement other visitors for network, compute, etc 

        List<Object> result = new ArrayList<>();

        for (TaskServiceStageWithLink stage : sortedStages) {
            for (ComponentRequestVisitor visitor : visitors) {
                if (visitor.accepts(stage)) {
                    result.add(visitor.visit(stage, allStages));
                }
            }
        }

        return result;
    }

}
