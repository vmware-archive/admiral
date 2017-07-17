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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import com.vmware.admiral.unikernels.common.exceptions.UntranslatableFileException;

public class Writer {

    File outputFile;
    FileWriter fileWriter;
    BufferedWriter bw;

    public Writer(String path) throws IOException {
        outputFile = new File(path);
        fileWriter = new FileWriter(outputFile, false);
        bw = new BufferedWriter(fileWriter);
    }

    public void write(List<String> messages) throws IOException, UntranslatableFileException {
        for (String s : messages) {
            if (s == null) {
                throw new UntranslatableFileException();
            }

            bw.write(s);
            bw.newLine();
        }

        bw.close();
    }
}
