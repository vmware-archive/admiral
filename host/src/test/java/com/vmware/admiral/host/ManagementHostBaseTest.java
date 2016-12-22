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

package com.vmware.admiral.host;

import java.util.logging.Level;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

public class ManagementHostBaseTest extends ServiceHost {

    /**
     * Class for test purposes, mainly when authorization of host is enabled. If @param
     * 'initializeTestDocuments' is equals to 'true' the created ServiceHost comes with set of
     * documents which have been created when host starts using system authorization context.
     *
     * @param args
     *            - arguments which will be parsed from host,
     * @param initializeTestDocuments
     *            - boolean flag indicates whether additional test documents to be created.
     * @return ManagementHost Object
     * @throws Throwable
     */
    static ManagementHost createManagementHost(String[] args, boolean initializeTestDocuments)
            throws Throwable {

        ManagementHost h = createManagementHost(args);

        if (initializeTestDocuments) {
            h.setAuthorizationContext(h.getSystemAuthorizationContext());
            TestServiceDocumentsInitializer.startServices(h);
            h.setAuthorizationContext(null);
        }

        return h;
    }

    static ManagementHost createManagementHost(String[] args) throws Throwable {
        ManagementHost h = new ManagementHost();
        h.initialize(args);

        h.start();

        h.setAuthorizationContext(h.getSystemAuthorizationContext());

        h.log(Level.INFO, "**** Management host starting ... ****");

        h.startFabricServices();

        h.startManagementServices();

        h.log(Level.INFO, "**** Management host started. ****");

        h.setAuthorizationContext(null);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            h.log(Level.WARNING, "Host stopping ...");
            h.stop();
            h.log(Level.WARNING, "Host is stopped");
        }));

        return h;
    }

    /**
     * Helper class for test purposes. It is used when authentication is enabled on host/s in order
     * to start the services which create ServiceDocuments using the same authorization context.
     */
    private static class TestServiceDocumentsInitializer extends HostInitServiceHelper {

        public static void startServices(ManagementHost host) {
            startServices(host, TestAuthServiceDocumentHelper.class);

            // start initialization of test documents
            host.sendRequest(Operation.createPost(
                    UriUtils.buildUri(host, TestAuthServiceDocumentHelper.class))
                    .setReferer(host.getUri())
                    .setBody(new ServiceDocument()));
        }
    }

}
