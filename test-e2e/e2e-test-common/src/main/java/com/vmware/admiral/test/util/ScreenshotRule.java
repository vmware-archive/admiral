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

import static com.codeborne.selenide.Selenide.screenshot;

import java.util.logging.Logger;

import org.junit.runner.Description;

public class ScreenshotRule extends BaseRule {

    private final Logger LOG = Logger.getLogger(getClass().getName());

    @Override
    protected void failed(Throwable e, Description description) {
        String path = getPathFromClassAndMethodName(description.getClassName(),
                description.getMethodName()) + "screenshot";
        String resultingFile = screenshot(path);
        LOG.info("Screenshot: " + resultingFile);
    }

}
