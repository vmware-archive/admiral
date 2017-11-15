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

package com.vmware.admiral.test.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Properties;

import org.apache.commons.io.IOUtils;

public class FileUtil {

    public static Properties loadProperties(String propertiesFile) {
        Properties properties = new Properties();
        Reader reader = loadFileContent(propertiesFile);
        try {
            properties.load(reader);
        } catch (IOException e) {
            throw new RuntimeException("Error while reading properties: ", e);
        }
        return properties;
    }

    public static String getFileContents(String fileName) {
        try {
            return IOUtils.toString(loadFileContent(fileName));
        } catch (IOException e) {
            throw new RuntimeException("Error while reading file: ", e);
        }
    }

    private static Reader loadFileContent(String filename) {
        String charsetName = "UTF-8";
        File file = new File(filename);
        try {
            InputStreamReader isr;
            if (file.exists()) {
                isr = new InputStreamReader(new FileInputStream(filename), charsetName);
            } else {
                isr = new InputStreamReader(
                        FileUtil.class.getResourceAsStream("/" + filename),
                        charsetName);
            }
            return isr;
        } catch (final NullPointerException | IOException e) {
            throw new RuntimeException("Error while reading file " + filename, e);
        }
    }

}
