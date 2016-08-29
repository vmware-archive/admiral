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

package com.vmware.admiral.common;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Configure java logging from values passed in system properties.
 *
 * To activate this configurer pass
 * -Djava.util.logging.config.class=com.vmware.admiral.common.SystemPropertyLoggingConfigurer
 *
 * Then configure the console handler via these properties: -Djava.util.logging.config.level=FINE
 *
 * By default this configurer creates a ConsoleHandler with a default format and level (INFO)
 */
public class SystemPropertyLoggingConfigurer {
    public static final String LEVEL_PROP_NAME = "java.util.logging.config.level";

    public SystemPropertyLoggingConfigurer() {
        final ConsoleHandler consoleHandler = new ConsoleHandler();

        String levelStr = System.getProperty(LEVEL_PROP_NAME);
        if (levelStr != null) {
            Level level = Level.parse(levelStr);
            consoleHandler.setLevel(level);
            consoleHandler.setFormatter(new SimpleFormatter());
        }

        // add the root logger
        Logger logger = Logger.getLogger("");
        logger.addHandler(consoleHandler);
        logger.setLevel(Level.ALL);
    }
}
