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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class DownloadService extends StatelessService {

    public static final String SELF_LINK = UnikernelManagementURIParts.DOWNLOAD;

    public DownloadService() {
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    public void handleGet(Operation get) {

        String stringPath = getPathFromURI(get.getUri().toString());
        String platform = stringPath.substring(stringPath.lastIndexOf('.') + 1);
        System.out.println(platform);
        Path path = Paths.get(stringPath);
        byte[] data;

        try {
            data = Files.readAllBytes(path);
            get.setContentType("application/" + platform);
            get.setBody(data);
            String contentDisposition = /* "attachment" */ "inline" + "; filename=\"unikernel."
                    + platform + "\"";
            get.addResponseHeader("Content-Disposition", contentDisposition);
            get.complete();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getPathFromURI(String URI) {
        return URI.substring(URI.indexOf(UnikernelManagementURIParts.DOWNLOAD)
                + UnikernelManagementURIParts.DOWNLOAD.length());
    }
}
