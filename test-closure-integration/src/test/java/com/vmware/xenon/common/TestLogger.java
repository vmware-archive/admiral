/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 */

package com.vmware.xenon.common;

import java.io.File;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TestLogger {
    private static final LogFormatter LOG_FORMATTER = new LogFormatter();

    private final Logger logger;

    /*
     * System property that can override the default target folder (e.g. in Jenkins).
     */
    private final String buildDirectory = System.getProperty("buildDirectory");

    private static final String LOG_FILE = "it-test.log";
    private static final int LIMIT = 1024 * 1024 * 10;

    public TestLogger(Class<?> testClass) {
        this.logger = Logger.getLogger(testClass.getName());
        try {
            File logFile;
            if (buildDirectory == null || buildDirectory.isEmpty()) {
                // file in the default target directory
                logFile = new File("./target/" + LOG_FILE);
            } else {
                // file in the provided build directory
                logFile = new File(buildDirectory, LOG_FILE);
            }

            FileHandler handler;
            try {
                handler = new FileHandler(logFile.getAbsolutePath(), LIMIT, 1);
            } catch (Exception e) {
                // last attempt, file in the default directory
                logFile = new File(LOG_FILE);
                handler = new FileHandler(logFile.getAbsolutePath(), LIMIT, 1);
            }

            handler.setFormatter(LOG_FORMATTER);
            this.logger.getParent().addHandler(handler);

            for (java.util.logging.Handler h : this.logger.getParent().getHandlers()) {
                h.setFormatter(LOG_FORMATTER);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void error(String fmt, Object... args) {
        Utils.log(this.logger, 3, "TEST", Level.SEVERE, fmt, args);
    }

    public void warning(String fmt, Object... args) {
        Utils.log(this.logger, 3, "TEST", Level.WARNING, fmt, args);
    }

    public void info(String fmt, Object... args) {
        Utils.log(this.logger, 3, "TEST", Level.INFO, fmt, args);
    }

}
