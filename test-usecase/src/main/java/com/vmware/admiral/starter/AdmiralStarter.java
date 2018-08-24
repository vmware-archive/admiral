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

package com.vmware.admiral.starter;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.lang3.StringUtils;

public class AdmiralStarter {

    private static final Logger LOG =
            Logger.getLogger(AdmiralStarter.class.getName());

    private String jarFilePath, localUsersFilePath, configPropsFilePath, sandboxPath;
    private int port;
    private ExecuteWatchdog watchdog;

    public AdmiralStarter(String jarFilePath, int port, String configPropsFilePath, String localUsersFilePath) {
        this(jarFilePath, port, localUsersFilePath, configPropsFilePath, null);
    }

    public AdmiralStarter(String jarFilePath, int port, String configPropsFilePath,
            String localUsersFilePath, String sandboxPath) {

        if(StringUtils.isBlank(jarFilePath)) {
            throw new IllegalArgumentException("\"jarFilePath\" is mandatory");
        }

        this.jarFilePath = jarFilePath;

        if(port == 0) {
            this.port = 8282;
        } else {
            this.port = port;
        }

        this.configPropsFilePath = configPropsFilePath;
        this.localUsersFilePath = localUsersFilePath;
        this.sandboxPath = sandboxPath;
    }

    public boolean start() {
        boolean started = false;

        if(watchdog == null) {

            // TODO Add flag fpr debug configuration
            //CommandLine cmd = CommandLine.parse("java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8888");
            // Build command line
            CommandLine cmd = CommandLine.parse("java");
            if(configPropsFilePath != null) {
                cmd.addArgument("-Dconfiguration.properties=" + configPropsFilePath);
            }

            cmd.addArgument("-jar");
            cmd.addArgument(jarFilePath);
            cmd.addArgument("--bindAddress=127.0.0.1");
            cmd.addArgument("--port=" + port);

            if(localUsersFilePath != null) {
                cmd.addArgument("--localUsers=" + localUsersFilePath);
            }

            if(sandboxPath != null) {
                cmd.addArgument("--sandbox=" + sandboxPath);
            }

            LOG.info("Command that will be executed: " + cmd.toString());

            // Start process
            DefaultExecutor executor = new DefaultExecutor();
            DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
            executor.setWatchdog(
                    watchdog = new ExecuteWatchdog(Long.MAX_VALUE));

            try {
                executor.execute(cmd, resultHandler);
                started = true;
            } catch (IOException e) {
                LOG.log(Level.SEVERE, e.getMessage(), e);
            }

        } else if(watchdog.isWatching()){
            started = true;
        } else {
            watchdog = null;
        }

        return started;
    }

    public boolean isRunning() {
        return watchdog != null && watchdog.isWatching();
    }

    public boolean stop() {

        boolean stopped = false;

        if(watchdog != null) {
            if(watchdog.isWatching()) {
                watchdog.destroyProcess();

                while(watchdog.isWatching()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                }
            }
            watchdog = null;
            stopped = true;
        } else {
            stopped = true;
        }

        return stopped;
    }

    public int getPort() {
        return port;
    }
}
