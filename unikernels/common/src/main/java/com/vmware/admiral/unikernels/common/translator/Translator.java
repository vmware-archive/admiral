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

package com.vmware.admiral.unikernels.common.translator;

import java.util.List;

public class Translator {

    CompilationBasesDB baseDB = new CompilationBasesDB();
    DescriptiveFileReference fileToTranslate;
    DescriptiveFileReferenceFactory dfrFactory;

    public Translator(DescriptiveFileReference fileToTranslate) {
        this.fileToTranslate = fileToTranslate;
        dfrFactory = new DescriptiveFileReferenceFactory();
    }

    /**
     * Currently, the method is able to convert java applications with simple dockerfiles. The main
     * and only use case is converting jar containers to unikernels.
     *
     * @param platform
     *            to which you wish to convert (OSv, Docker)
     * @return the translated DFR
     */
    public DescriptiveFileReference translate(Platform platform) {
        DescriptiveFileReference translated = dfrFactory.getFileReferenceWithArgs(platform,
                getBaseEquivalent(platform),
                fileToTranslate.getLanguage(), fileToTranslate.getWorkDir(),
                fileToTranslate.getExecutableName(), fileToTranslate.getGivenName());

        return translated;
    }

    /**
     * @param platform
     *            to which you wish to convert (OSv, Docker) an existing base
     * @return the equivalent base if no equivalent is found return the same base
     */
    public String getBaseEquivalent(Platform platform) {
        int equivalenceIndex = -1;

        String usedBase = fileToTranslate.getBase();
        // Compiler platform bases from the file which we are translating
        List<String> cpbFrom = getCompilerPlatformBases(fileToTranslate.getPlatform());

        for (int i = 0; i < cpbFrom.size(); i++) {
            if (usedBase.contains(cpbFrom.get(i))) {
                equivalenceIndex = i;
                break;
            }
        }
        // Compiler platform bases from the file which we are creating
        List<String> cpbTo = getCompilerPlatformBases(platform);

        if (equivalenceIndex == -1)
            return usedBase;

        return cpbTo.get(equivalenceIndex);
    }

    public List<String> getCompilerPlatformBases(Platform platform) {
        int compilerPlatformCol = platform.ordinal();
        return baseDB.getDB().get(compilerPlatformCol);
    }

}
