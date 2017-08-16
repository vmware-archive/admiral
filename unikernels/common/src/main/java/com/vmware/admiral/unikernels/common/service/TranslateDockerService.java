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

package com.vmware.admiral.unikernels.common.service;

import com.vmware.admiral.unikernels.common.exceptions.DockerFileFormatException;
import com.vmware.admiral.unikernels.common.service.UnikernelCreationTaskService.UnikernelCreationTaskServiceState;
import com.vmware.admiral.unikernels.common.translator.DescriptiveFileReference;
import com.vmware.admiral.unikernels.common.translator.Parser;
import com.vmware.admiral.unikernels.common.translator.Platform;
import com.vmware.admiral.unikernels.common.translator.Translator;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;

public class TranslateDockerService extends StatelessService {

    public static final String SELF_LINK = UnikernelManagementURIParts.TRANSLATION;

    private Parser parser = new Parser();

    @Override
    public void handlePost(Operation post) {
        TranslationData data = post.getBody(TranslationData.class);
        CompilationData forwardedData = null;
        try {
            String capstan = getTranslatedDfrString(data);
            forwardedData = createCompilationData(data, capstan);
        } catch (DockerFileFormatException e1) {
            logWarning(e1.getMessage());
        }

        UnikernelCreationTaskServiceState wrappedData = new UnikernelCreationTaskServiceState();
        wrappedData.data = forwardedData;

        Operation request = Operation
                .createPost(this, UnikernelManagementURIParts.CREATION)
                .setReferer(getSelfLink())
                .setBody(wrappedData)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        post.fail(e);
                    } else {
                        post.complete();
                    }
                });

        sendRequest(request);
    }

    private String getTranslatedDfrString(TranslationData data) throws DockerFileFormatException {
        parser.readString(data.dockerfile);
        DescriptiveFileReference dfr = parser.parseDocker();
        Translator translator = new Translator(dfr);
        DescriptiveFileReference translatedDfr = translator.translate(Platform.OSv);
        return translatedDfr.getDocumentString();
    }

    private CompilationData createCompilationData(TranslationData translationData, String capstan) {
        CompilationData data = new CompilationData();
        data.capstanfile = capstan;
        data.compilationPlatform = translationData.compilationPlatform;
        data.sources = translationData.sources;
        data.successCB = translationData.successCB;
        data.failureCB = translationData.failureCB;
        return data;
    }
}
