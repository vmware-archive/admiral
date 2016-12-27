/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Collectors;

public class FileUtil {

    /**
     * The path where user defined resources are located. Such resources include recommended images
     * definition and custom container image icons.
     */
    public static String USER_RESOURCES_PATH_VARIABLE = "container.user.resources.path";

    public static Properties getProperties(String resourceFile, boolean isResource) {
        Properties props = new Properties();
        String propertiesString = getResourceAsString(resourceFile, isResource);
        try {
            props.load(new StringReader(propertiesString));
        } catch (IOException e) {
            throw new RuntimeException("Unable to load resources from string", e);
        }

        return props;
    }

    public static String getResourceAsString(String resourceFile, boolean isResource) {
        try (InputStream is = getInputStream(resourceFile, isResource);
                BufferedReader buffer = new BufferedReader(new InputStreamReader(is))) {
            return buffer.lines().collect(Collectors.joining(System.lineSeparator()));

        } catch (IOException e) {
            throw new RuntimeException("Unable to load resources from disk", e);
        }
    }

    public static String getClasspathResourceAsString(String resourceFile) {
        try (InputStream is = FileUtil.class.getResourceAsStream(resourceFile);
                BufferedReader buffer = new BufferedReader(new InputStreamReader(is))) {
            return buffer.lines().collect(Collectors.joining(System.lineSeparator()));

        } catch (IOException e) {
            throw new RuntimeException("Unable to load resources from disk", e);
        }
    }

    /**
     * @return a {@link String} representation of the {@link Path} specified. It is guaranteed that
     *         the result will contain only forward slashes (and no back slashes). If
     *         <code>path</code> is <code>null</code>, then <code>null</code> is returned.
     */
    public static String getForwardSlashesPathString(Path path) {
        if (path == null) {
            return null;
        } else {
            return path.toString().replace('\\', '/');
        }
    }

    private static InputStream getInputStream(String resourceFile, boolean isResource)
            throws FileNotFoundException {
        InputStream inputStream = null;
        if (isResource) {
            inputStream = FileUtil.class.getResourceAsStream(resourceFile);
        } else {
            inputStream = new FileInputStream(resourceFile);
        }
        return inputStream;
    }
}
