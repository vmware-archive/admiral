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

package com.vmware.admiral.test.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ResourceUtil {

    public static String readFileAsString(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String readTestResourceAsString(String filename) {
        return readFileAsString("src/test/resources/" + filename);
    }

    public static Properties readTestPropertiesFile(String filename) {
        try (InputStream stream = new FileInputStream("src/test/resources/" + filename)) {
            Properties properties = new Properties();
            properties.load(stream);
            return properties;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
