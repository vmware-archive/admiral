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

package com.vmware.admiral.test.closures;

import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;

import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closure.ClosureFactoryService;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescriptionFactoryService;
import com.vmware.admiral.closures.services.images.DockerImageFactoryService;
import com.vmware.admiral.closures.util.ClosureUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.UriUtils;

public class BaseClosurePerformanceTest extends BaseClosureProvisioningIT {

    protected void cleanResource(String targetLink, ServiceClient serviceClient) throws Exception {
        URI targetUri = URI.create(getBaseUrl() + buildServiceUri(targetLink));
        sendRequest(serviceClient, Operation.createDelete(targetUri));
    }

    protected Closure createClosure(ClosureDescription closureDescription,
            ServiceClient serviceClient)
            throws InterruptedException, ExecutionException, TimeoutException {
        Closure closureState = new Closure();
        closureState.descriptionLink = closureDescription.documentSelfLink;
        URI targetUri = URI.create(getBaseUrl()
                + buildServiceUri(ClosureFactoryService.FACTORY_LINK));
        Operation op = sendRequest(serviceClient,
                Operation.createPost(targetUri).setBody(closureState));
        return op.getBody(Closure.class);
    }

    protected ClosureDescription createClosureDescription(String taskDefPayload,
            ServiceClient serviceClient)
            throws InterruptedException, ExecutionException, TimeoutException {
        URI targetUri = URI.create(getBaseUrl()
                + buildServiceUri(ClosureDescriptionFactoryService.FACTORY_LINK));
        Operation op = sendRequest(serviceClient,
                Operation.createPost(targetUri).setBody(taskDefPayload));
        return op.getBody(ClosureDescription.class);
    }

    protected void executeClosure(Closure createdClosure, Closure closureRequest,
            ServiceClient serviceClient)
            throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException,
            KeyManagementException, InterruptedException, ExecutionException, TimeoutException {
        URI targetUri = URI.create(getBaseUrl() + buildServiceUri(createdClosure.documentSelfLink));
        Operation op = sendRequest(serviceClient,
                Operation.createPost(targetUri).setBody(closureRequest));
        Assert.assertNotNull(op);
    }

    protected static String createImageBuildRequestUri(String imageName, String computeStateLink) {
        String imageBuildRequestId = ClosureUtils.calculateHash(new String[] { imageName, "/",
                computeStateLink });

        return UriUtils.buildUriPath(DockerImageFactoryService.FACTORY_LINK, imageBuildRequestId);
    }

    protected boolean isTimeoutElapsed(long startTime, int timeout) {
        return System.currentTimeMillis() - startTime > TimeUnit.SECONDS.toMillis(timeout);
    }
}
