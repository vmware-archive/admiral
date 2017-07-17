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

package com.vmware.admiral.unikernels.osv.compilation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class CommandExecutor {

    private Process startedProcess;

    public synchronized void execute(String[] command) throws IOException {

        ProcessBuilder pb = new ProcessBuilder(command);
        startedProcess = pb.start();
        try {
            startedProcess.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public synchronized String output() throws IOException, InterruptedException {

        if (startedProcess == null) {
            return "";
        }

        startedProcess.waitFor();

        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(startedProcess.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line + System.getProperty("line.separator"));
            }

            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return sb.toString();
    }
}
