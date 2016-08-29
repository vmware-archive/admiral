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

package com.vmware.admiral.adapter.docker.util.ssh;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adapt JSch logger to JULI
 */
public class JSchLoggerAdapter implements com.jcraft.jsch.Logger {
    private final Logger logger;

    public JSchLoggerAdapter(Logger logger) {
        this.logger = logger;
    }

    private Level mapLevel(int level) {
        switch (level) {
        case DEBUG:
            return Level.FINEST;
        case INFO:
            // JSch is pretty verbose with INFO, and by default INFO is logged to the console, so
            // turn it down to FINE
            return Level.FINE;
        case WARN:
            return Level.WARNING;
        case ERROR:
        case FATAL:
            return Level.SEVERE;
        default:
            throw new IllegalArgumentException("Unexpected logging level: " + level);
        }
    }

    @Override
    public boolean isEnabled(int level) {
        return logger.isLoggable(mapLevel(level));
    }

    @Override
    public void log(int level, String message) {
        logger.log(mapLevel(level), message);
    }

}
