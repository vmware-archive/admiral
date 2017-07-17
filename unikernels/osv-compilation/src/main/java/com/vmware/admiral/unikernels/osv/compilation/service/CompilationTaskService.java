/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.unikernels.osv.compilation.service;

import java.io.IOException;

import com.vmware.admiral.unikernels.osv.compilation.CommandExecutor;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.services.common.TaskService;

public class CompilationTaskService
        extends TaskService<CompilationTaskService.CompilationTaskServiceState> {

    public static final CommandExecutor executor = new CommandExecutor();

    public enum SubStage {
        PULLING_SOURCES, CREATING_CAPSTAN, COMPILING, TARGETING_IMAGE
    }

    public static final String FACTORY_LINK = UnikernelManagementURIParts.COMPILE_TASK;

    public static class CompilationTaskServiceState extends TaskService.TaskServiceState {

        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public SubStage subStage;

        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public CompilationData data;
    }

    public CompilationTaskService() {
        super(CompilationTaskServiceState.class);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.INSTRUMENTATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);

    }

    @Override
    protected CompilationTaskServiceState validateStartPost(Operation taskOperation) {

        CompilationTaskServiceState task = super.validateStartPost(taskOperation);

        CompilationData data = task.data;
        if (data != null) {
            if (!data.isSet()) {
                taskOperation.fail(
                        new IllegalArgumentException(
                                "Not all the required data is supplied for completing the task"));
                return null;
            }
        } else {
            taskOperation.fail(
                    new IllegalArgumentException(
                            "Not all the required data is supplied for completing the task"));
        }
        if (ServiceHost.isServiceCreate(taskOperation)) {
            if (task.subStage != null) {
                taskOperation.fail(
                        new IllegalArgumentException("Do not specify subStage: internal use only"));
                return null;
            }
        }
        return task;
    }

    @Override
    protected void initializeState(CompilationTaskServiceState task, Operation taskOperation) {
        super.initializeState(task, taskOperation);
        task.subStage = SubStage.PULLING_SOURCES;
    }

    @Override
    public void handlePatch(Operation patch) {
        CompilationTaskServiceState currentTask = getState(patch);
        CompilationTaskServiceState patchBody = getBody(patch);
        if (!validateTransition(patch, currentTask, patchBody)) {
            return;
        }

        updateState(currentTask, patchBody);
        patch.complete();

        switch (patchBody.taskInfo.stage) {
        case CREATED:
            break;
        case STARTED:
            handleSubstage(patchBody);
            break;
        case CANCELLED:
            logInfo("Task canceled: not implemented, ignoring");
            break;
        case FINISHED:
            System.out.println("Task finished successfully");
            sendCB(patch, patchBody.data.successCB); // test
            logInfo("Task finished successfully");

            break;
        case FAILED:
            sendCB(patch, patchBody.data.failureCB); // test
            logWarning("Task failed: %s", (patchBody.failureMessage == null ? "No reason given"
                    : patchBody.failureMessage));
            break;
        default:
            logWarning("Unexpected stage: %s", patchBody.taskInfo.stage);
            break;
        }
    }

    private void sendCB(Operation patch, String cbLink) {
        Operation request = Operation.createPatch(this, cbLink)
                .setReferer(getSelfLink())
                .setBody(cbLink) // replace with download link
                .setCompletion((o, e) -> {
                    if (e != null) {
                        patch.fail(e);
                    } else {
                        patch.complete();
                    }
                });
        sendRequest(request);
    }

    private void handleSubstage(CompilationTaskServiceState task) {
        switch (task.subStage) {
        case PULLING_SOURCES:
            handlePullSources(task);
            break;
        case CREATING_CAPSTAN:
            handleCreateCapstan(task);
            break;
        case COMPILING:
            handleCompilingCapstan(task);
            break;
        case TARGETING_IMAGE:
            handleTargetingImage(task);
            break;
        default:
            logWarning("Unexpected sub stage: %s", task.subStage);
            break;
        }
    }

    private void handlePullSources(CompilationTaskServiceState task) {
        String checkoutCommand = "cd ; git clone " + task.data.sources; // pull in main folder
        try {
            executor.execute(new String[] { "bash", "-c", checkoutCommand });
        } catch (IOException e) {
            task.taskInfo.stage = TaskStage.FAILED;
            e.printStackTrace();
        }

        System.out.println("PULLING SOURCES");
        sendSelfPatch(task, TaskStage.STARTED, SubStage.CREATING_CAPSTAN);
    }

    private void handleCreateCapstan(CompilationTaskServiceState task) {
        String[] parsedLink = task.data.sources.split("/");
        String folderName = parsedLink[parsedLink.length - 1];
        folderName = folderName.substring(0, folderName.length() - 4); // remove .git extension

        String creationCommand = "cd ~/" + folderName + " ; touch Capstanfile ; printf '"
                + task.data.capstanfile + "' >> Capstanfile";
        try {
            executor.execute(new String[] { "bash", "-c", creationCommand });
        } catch (Exception e) {
            task.taskInfo.stage = TaskStage.FAILED;
            e.printStackTrace();
        }

        System.out.println("CREATING CAPSTAN");
        sendSelfPatch(task, TaskStage.STARTED, SubStage.COMPILING);
    }

    private void handleCompilingCapstan(CompilationTaskServiceState task) {
        String[] parsedLink = task.data.sources.split("/");
        String folderName = parsedLink[parsedLink.length - 1];
        folderName = folderName.substring(0, folderName.length() - 4); // remove .git extension
        String compilationCommand = "cd ~/" + folderName + " ; capstan build ";

        if (!task.data.compilationPlatform.equals("")) {
            compilationCommand = compilationCommand + "-p " + task.data.compilationPlatform;
        }

        try {
            executor.execute(new String[] { "bash", "-c", compilationCommand });
        } catch (Exception e) {
            task.taskInfo.stage = TaskStage.FAILED;
            e.printStackTrace();
        }

        System.out.println("COMPILING");
        sendSelfPatch(task, TaskStage.STARTED, SubStage.TARGETING_IMAGE);
    }

    private void handleTargetingImage(CompilationTaskServiceState task) {
        //String folderName = getFolderNameFromPath(task.data.sources);
        String targetQuery = "cd ~/.capstan/repository ; ls";

        try {
            executor.execute(new String[] { "bash", "-c", targetQuery });
        } catch (Exception e) {
            task.taskInfo.stage = TaskStage.FAILED;
            e.printStackTrace();
        }

        System.out.println("TARGETING");
        sendSelfPatch(task, TaskStage.FINISHED, SubStage.TARGETING_IMAGE);
    }
    /*
    private String getFolderNameFromPath(String path) {
        String[] parsedLink = path.split("/");
        String folderName = parsedLink[parsedLink.length - 1];
        return folderName.substring(0, folderName.length() - 4); // remove .git extension
    } */

    private void sendSelfPatch(CompilationTaskServiceState task, TaskStage stage,
            SubStage subStage) {
        if (task.taskInfo == null) {
            task.taskInfo = new TaskState();
        }
        task.taskInfo.stage = stage;
        task.subStage = subStage;
        sendTaskPatch(task);
    }

    private void sendTaskPatch(CompilationTaskServiceState task) {
        Operation patch = Operation.createPatch(getUri())
                .setBody(task)
                .setCompletion(
                        (op, ex) -> {
                            if (ex != null) {
                                logWarning("Failed to send patch, task has failed: %s",
                                        ex.getMessage());
                            }
                        });
        sendRequest(patch);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        CompilationTaskServiceState template = new CompilationTaskServiceState();
        template.data = new CompilationData();
        template.data.capstanfile = "Capstanfile";
        template.data.sources = "Sources";
        template.data.compilationPlatform = "Platform";
        template.data.successCB = "";
        template.data.failureCB = "";
        return template;
    }

}
