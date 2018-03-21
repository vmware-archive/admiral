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

import java.util.logging.Logger;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import com.vmware.xenon.common.Utils;

public class TestStatusLoggerRule extends TestWatcher {

    Logger LOG = Logger.getLogger(getClass().getName());

    @Override
    protected void starting(Description description) {
        LOG.info(getMessage("Starting test: " + description.getClassName() + "::"
                + description.getMethodName()));
    }

    @Override
    protected void failed(Throwable e, Description description) {
        LOG.warning(getMessage(
                "Failure: " + description.getClassName() + "::" + description.getMethodName()));
        LOG.warning(Utils.toString(e));
    }

    @Override
    protected void succeeded(Description description) {
        LOG.info(getMessage(
                "Success: " + description.getClassName() + "::" + description.getMethodName()));
    }

    private String getMessage(String message) {
        String pretty = "# " + message + " #";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < pretty.length(); i++) {
            builder.append("#");
        }
        return "\n" + builder.toString() + "\n" + pretty + "\n" + builder.toString();
    }

}
