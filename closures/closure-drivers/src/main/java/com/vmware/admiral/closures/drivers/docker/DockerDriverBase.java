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

package com.vmware.admiral.closures.drivers.docker;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.admiral.closures.drivers.ClosureDockerClient;
import com.vmware.admiral.closures.drivers.ClosureDockerClientFactory;
import com.vmware.admiral.closures.drivers.ContainerConfiguration;
import com.vmware.admiral.closures.drivers.ExecutionDriver;
import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.closures.util.ClosureUtils;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Base of execution drivers using docker service.
 *
 */
public abstract class DockerDriverBase implements ExecutionDriver {

    private final ServiceHost serviceHost;
    private final ClosureDockerClientFactory dockerClientFactory;

    public abstract String getDockerImage();

    public DockerDriverBase(ServiceHost serviceHost, ClosureDockerClientFactory dockerClientFactory) {
        this.serviceHost = serviceHost;
        this.dockerClientFactory = dockerClientFactory;
    }

    @Override
    public void executeClosure(Closure closure, ClosureDescription closureDesc, String token, Consumer<Throwable>
            errorHandler) {
        ClosureDockerClient dockerClient = dockerClientFactory.getClient();

        String containerImage = getDockerImage();
        String containerName = generateContainerName(closure);

        logInfo("Creating container with name: %s image: %s", containerName, containerImage);

        ContainerConfiguration configuration = new ContainerConfiguration(containerName);

        configuration.memoryMB = closureDesc.resources.ramMB;
        configuration.cpuShares = closureDesc.resources.cpuShares;

        configuration.logConfiguration = closureDesc.logConfiguration;
        configuration.sourceURL = closureDesc.sourceURL;
        configuration.placementLink = closureDesc.placementLink;
        configuration.dependencies = closureDesc.dependencies;

        List<String> vars = populateEnvs(closure, token);
        configuration.envVars = vars.toArray(new String[vars.size()]);
        logInfo("Creating closure with envs: %s", vars.get(0));

        dockerClient.createAndStartContainer(closure.documentSelfLink, containerImage, configuration, errorHandler);
        logInfo("Code execution request sent.");
    }

    @Override
    public void cleanClosure(Closure closure, Consumer<Throwable> errorHandler) {
        ClosureDockerClient dockerClient = dockerClientFactory.getClient();
        if (dockerClient == null) {
            Utils.logWarning("No available docker clients found! Unable to clean execution container!");
            return;
        }

        if (closure.resourceLinks == null || closure.resourceLinks.size() <= 0) {
            errorHandler.accept(new Exception("No resource to clean for closure: " + closure.documentSelfLink));
            return;
        }

        logInfo("Killing container with for closure: %s", closure.documentSelfLink);
        for (String containerLink : closure.resourceLinks) {
            try {
                logInfo("Removing container with Id: %s", containerLink);
                dockerClient.removeContainer(containerLink, errorHandler);
                logInfo("Closure cancelled: %s", closure.documentSelfLink);
            } catch (Exception ex) {
                Utils.logWarning(
                        "Unable to clean containers corresponding to cancelled closure: " + closure.documentSelfLink,
                        ex);
            }
        }
    }

    @Override
    public void cleanImage(String imageName, String computeStateLink, Consumer<Throwable> errorHandler) {
        ClosureDockerClient dockerClient = dockerClientFactory.getClient();
        if (dockerClient == null) {
            errorHandler.accept(new Exception("No available docker clients found! Unable to clean docker image!"));
            return;
        }

        if (computeStateLink == null || computeStateLink.length() <= 0) {
            errorHandler.accept(new Exception("No compute state provided!"));
            return;
        }
        if (imageName == null || imageName.length() <= 0) {
            errorHandler.accept(new Exception("No image name provided!"));
            return;
        }

        dockerClient.cleanImage(imageName, computeStateLink, errorHandler);
    }

    @Override
    public void inspectImage(String imageName, String imageRequestLink, Consumer<Throwable> errorHandler) {
        ClosureDockerClient dockerClient = dockerClientFactory.getClient();
        if (dockerClient == null) {
            errorHandler.accept(new Exception("No available docker clients found! Unable to clean docker image!"));
            return;
        }

        if (imageRequestLink == null || imageRequestLink.length() <= 0) {
            String errorMsg = "No image request link provided!";
            Utils.logWarning(errorMsg);
            errorHandler.accept(new Exception(errorMsg));
            return;
        }

        dockerClient.inspectImage(imageName, imageRequestLink, errorHandler);
    }

    @Override
    public ServiceHost getServiceHost() {
        return serviceHost;
    }

    private String generateContainerName(Closure closure) {
        String taskID = Service.getId(closure.documentSelfLink);
        return taskID + "_" + closure.documentVersion;
    }

    private List<String> populateEnvs(Closure closure, String token) {
        List<String> vars = new ArrayList<>();

        URI taskUri = prepareTaskUri(closure);
        vars.add("TASK_URI=" + taskUri);
        logInfo("Setting TASK_URI %s for closure: %s", taskUri, closure.descriptionLink);
        if (!ClosureUtils.isEmpty(token)) {
            vars.add("TOKEN=" + token);
        }
        return vars;
    }

    private URI prepareTaskUri(Closure closure) {
        ServiceHost serviceHost = getServiceHost();
        return UriUtils.buildPublicUri(serviceHost, closure.documentSelfLink);
    }

    private void logInfo(String message, Object... values) {
        Utils.log(getClass(), getClass().getSimpleName(), Level.INFO, message, values);
    }

}
