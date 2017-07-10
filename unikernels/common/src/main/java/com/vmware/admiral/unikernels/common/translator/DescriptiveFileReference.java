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

public abstract class DescriptiveFileReference {

    protected Platform platform;
    protected String base;
    protected String CMD;
    protected String language;
    protected String workDir;
    protected String executableName;
    protected String givenName;

    // Do not include FROM tag or base: tag or any tag inside the actual used base
    protected String getOnlyBase(String baseLine) {
        String[] separatedLine = baseLine.split("\\s+");

        if (separatedLine.length > 1)
            return separatedLine[1];
        else
            return "";
    }

    public String getBase() {
        return base;
    }

    public String getCMD() {
        return CMD;
    }

    public String getLanguage() {
        return language;
    }

    public String getWorkDir() {
        return workDir;
    }

    public String getExecutableName() {
        return executableName;
    }

    public String getGivenName() {
        return givenName;
    }

    public Platform getPlatform() {
        return platform;
    }

    public void identifyLanguage() {
        if (CMD.contains("jar"))
            language = "java";
        else if (CMD.contains("node") || CMD.contains(".js"))
            language = "node";
    }

    public abstract List<String> getDocument();

}
