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
import java.util.Arrays;
import java.util.List;

import com.vmware.admiral.unikernels.common.exceptions.CapstanFileFormatException;
import com.vmware.admiral.unikernels.common.exceptions.DockerFileFormatException;

public class CapstanFileReference extends DescriptiveFileReference {

    // Empty CapstanFile creation
    public CapstanFileReference() {
        platform = Platform.OSv;
    }

    // Configured CapstanFile creation
    public CapstanFileReference(String baseLine, String CMD, String fileCommand)
            throws CapstanFileFormatException {
        platform = Platform.OSv;
        this.base = getOnlyBase(baseLine);
        this.CMD = CMD;
        identifyLanguage();
        setElements(fileCommand);
    }

    // Translated CapstanFile creation
    public CapstanFileReference(String base, String language, String workDir, String executableName,
            String givenName) {
        platform = Platform.OSv;
        this.base = base;
        this.language = language;
        this.workDir = workDir;
        this.executableName = executableName;

        if (givenName != null && !givenName.equals("."))
            this.givenName = givenName;
        else if (language.equals("java"))
            this.givenName = "/app.jar";

        configCMD();
    }

    // Parse the commands in order to set all the descriptive elements
    private void setElements(String files) throws CapstanFileFormatException {
        try {
            String[] parsedFiles = files.split("\\s+|\\/");

            // remove whitespaces in the begging (apparent for all capstans)
            parsedFiles = Arrays.copyOfRange(parsedFiles, 2, parsedFiles.length);

            // name is always parsed "<name>:" and is required "/<name>"
            givenName = "/" + parsedFiles[0].substring(0, parsedFiles[0].length() - 1);
            executableName = parsedFiles[parsedFiles.length - 1];
            String path = "";
            for (int i = 1; i < parsedFiles.length - 1; i++) {
                path = path + "/" + parsedFiles[i];
            }
            workDir = path;
            nonEmptyCreation();
        } catch (Exception e) {
            throw new CapstanFileFormatException();
        }
    }

    private void configCMD() {
        if (language.equals("java")) {
            CMD = "cmdline: /java.so -jar " + givenName;
        }
    }

    private void nonEmptyCreation() throws CapstanFileFormatException {
        if (base.equals("") || CMD.equals(""))
            throw new CapstanFileFormatException();
    }

    @Override
    public List<String> getDocument() {
        List<String> fileLines = new ArrayList<String>();
        fileLines.add("base: " + base);
        fileLines.add(CMD);
        fileLines.add("files:");

        if (workDir.equals("/")) // non-repetitive symbols for root
            fileLines.add("  " + givenName + ": " + workDir + executableName);
        else
            fileLines.add("  " + givenName + ": " + workDir + "/" + executableName);
        return fileLines;
    }

}
