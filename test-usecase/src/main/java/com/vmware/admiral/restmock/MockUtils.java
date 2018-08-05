/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.restmock;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

public class MockUtils {

    public static String fileToString(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), Charset.forName("UTF-8"));
    }

    public static String resourceToString(String path) throws Exception {
        return new String(Files.readAllBytes(
                Paths.get(MockUtils.class.getResource(path).toURI())), Charset.forName("UTF-8"));
    }

    public static int getAvailablePort() {
        int port = ThreadLocalRandom.current().nextInt(49152, 65535 + 1);
        try {
            ServerSocket s = new ServerSocket(0);
            port = s.getLocalPort();
            s.close();
        } catch (IOException e) {
            // Could not connect. Ignore.
        }
        return port;
    }
}
