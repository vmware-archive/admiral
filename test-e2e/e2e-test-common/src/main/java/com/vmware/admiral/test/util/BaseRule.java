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

import java.io.File;

import org.junit.rules.TestWatcher;

public class BaseRule extends TestWatcher {

    protected String getPathFromClassAndMethodName(String className, String methodName) {
        String[] dirs = className.split("\\.");
        StringBuilder builder = new StringBuilder();
        for (String str : dirs) {
            builder.append(str + File.separator);
        }
        builder.append(methodName + File.separator);
        return builder.toString();
    }

}
