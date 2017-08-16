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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Scanner;

import com.vmware.admiral.unikernels.common.exceptions.CapstanFileFormatException;
import com.vmware.admiral.unikernels.common.exceptions.DockerFileFormatException;

public class Parser {

    private File descriptiveFile;
    private Reader reader;
    private BufferedReader bR;
    private Scanner scanner;
    private String[] reading;

    public Parser(String path) throws FileNotFoundException {
        descriptiveFile = new File(path);
        reader = new FileReader(descriptiveFile);
        bR = new BufferedReader(reader);
        scanner = new Scanner(bR);
        readStream();
    }

    public Parser(InputStream stream) {
        reader = new InputStreamReader(stream);
        bR = new BufferedReader(reader);
        scanner = new Scanner(bR);
        readStream();
    }

    public Parser() {

    }

    private void readStream() {
        String readLine = "";
        StringBuffer sb = new StringBuffer();
        while (scanner.hasNextLine()) {
            readLine = scanner.nextLine();
            if (!readLine.trim().equals("")) {
                sb.append("\n");
                sb.append(readLine);
            }
        }

        readString(sb.toString());
    }

    public void readString(String fileContent) {
        reading = fileContent.split("\\r?\\n");
    }

    public String[] getReading() {
        String[] readingCopy = reading;
        return readingCopy;
    }

    public String getTagArgs(String tag) {
        for (String tagLine : reading) {
            if (tagLine.contains(tag)) {
                return tagLine;
            }
        }

        return "";
    }

    private String parseGithubSources(String gitSource) {
        if (gitSource.length() > 1) {
            return gitSource.trim().substring(1, gitSource.length());
        } else {
            return "";
        }
    }

    public CapstanFileReference parseCapstan() throws CapstanFileFormatException {
        // files are annotated below the files: tag with a
        // double space before
        CapstanFileReference cfr = new CapstanFileReference(getTagArgs("base"),
                getTagArgs("cmdline"),
                getTagArgs("  "));

        cfr.githubSources = parseGithubSources(getTagArgs("github"));
        return cfr;
    }

    public DockerFileReference parseDocker() throws DockerFileFormatException {
        DockerFileReference dfr = new DockerFileReference(getTagArgs("FROM"), getTagArgs("CMD"),
                getTagArgs("COPY"),
                getTagArgs("WORKDIR"));

        dfr.githubSources = parseGithubSources(getTagArgs("github"));
        return dfr;
    }
}
