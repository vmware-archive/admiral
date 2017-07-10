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

import java.util.ArrayList;
import java.util.List;

import com.vmware.admiral.unikernels.common.exceptions.DockerFileFormatException;

public class DockerFileReference extends DescriptiveFileReference {

    // Empty DockerFile creation
    public DockerFileReference() {
        platform = Platform.Docker;
    }

    // Configured DockerFile creation
    public DockerFileReference(String baseLine, String CMD, String copy, String workdir)
            throws DockerFileFormatException {
        platform = Platform.Docker;
        this.base = getOnlyBase(baseLine);
        this.CMD = CMD;
        identifyLanguage();
        setElements(copy, workdir, CMD);

    }

    public DockerFileReference(String base, String language, String workDir, String executableName,
            String givenName) {
        platform = Platform.Docker;
        this.base = base;
        this.language = language;
        this.workDir = workDir;
        this.executableName = executableName;

        if (givenName != null)
            this.givenName = givenName;
        else
            givenName = "/my-app";

        configCMD();
    }

    // Parse the read commands in order to set the descriptive elements
    private void setElements(String copy, String workDir, String CMD)
            throws DockerFileFormatException {
        try {
            String[] parsedCopy = copy.split("\\s+");

            // COPY . <NAME>
            givenName = parsedCopy[parsedCopy.length - 1];

            String[] parsedWorkDir = workDir.split("\\s+");

            // WORKIDIR <DIRECTORY> -> second element strip off the given folder name in the
            if (workDir != "")
                this.workDir = parsedWorkDir[1].replace(givenName, "");
            else
                this.workDir = "/";

            String[] parsedCMD = CMD.split("\\s+");
            executableName = parsedCMD[parsedCMD.length - 1];
            nonEmptyCreation();
        } catch (Exception e) {
            throw new DockerFileFormatException();
        }

    }

    private void configCMD() {
        if (language.equals("java")) {
            CMD = "CMD java -jar " + this.executableName;
        }
    }

    private void nonEmptyCreation() throws DockerFileFormatException {
        if (base.equals("") || CMD.equals(""))
            throw new DockerFileFormatException();
    }

    @Override
    public List<String> getDocument() {
        List<String> fileLines = new ArrayList<String>();
        fileLines.add("FROM " + base);
        fileLines.add("COPY . " + workDir);
        fileLines.add("WORKDIR " + workDir);
        fileLines.add(CMD);
        return fileLines;
    }

}
