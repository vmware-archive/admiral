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

package com.vmware.admiral.vic.test.ui.util;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import com.vmware.admiral.test.util.AuthContext;
import com.vmware.admiral.test.util.SSHCommandExecutor;
import com.vmware.admiral.test.util.SSHCommandExecutor.CommandResult;

public class VchUtil {

    private static boolean isEnvironmentReady = false;

    private final Logger LOG = Logger.getLogger(getClass().getName());

    private final String SCRIPT_FILENAME = "create_delete_portgroup.py";
    private final String LOCAL_SCRIPT_PATH = "src" + File.separator + "test" + File.separator
            + "resources"
            + File.separator + SCRIPT_FILENAME;
    private final String REMOTE_TEMP_DIRECTORY = "/tmp/temporary_test_files/";
    private final String SCRIPT_REMOTE_PATH = REMOTE_TEMP_DIRECTORY + SCRIPT_FILENAME;
    private final String REMOTE_VIC_MACHINE_PATH = REMOTE_TEMP_DIRECTORY + "vic_machine";
    private final String REMOTE_VIC_MACHINE_TAR_PATH = "/opt/vmware/fileserver/files/vic*.tar.gz";
    private final String VIC_MACHINE_TOOL_REMOTE_PATH = REMOTE_VIC_MACHINE_PATH
            + "/vic/vic-machine-linux";
    private final String HARBOR_CERTIFICATE_REMOTE_PATH = "/storage/data/harbor/ca_download/ca.crt";

    private final SSHCommandExecutor executor;

    private AuthContext vcenterAuthContext;

    public VchUtil(AuthContext vicOvaAuthContext, AuthContext vcenterAuthContext) {
        this.vcenterAuthContext = vcenterAuthContext;
        this.executor = new SSHCommandExecutor(vicOvaAuthContext, 22);
        if (!isEnvironmentReady) {
            prepareEnvironment();
            isEnvironmentReady = true;
        }
    }

    public CommandResult createVch(String vchName, String datastoreName, String portgroupName) {
        StringBuilder createVchCommand = new StringBuilder();
        createVchCommand.append(VIC_MACHINE_TOOL_REMOTE_PATH + " create")
                .append(" --target " + vcenterAuthContext.getTarget())
                .append(" --user " + vcenterAuthContext.getUsername())
                .append(" --password " + vcenterAuthContext.getPassword())
                .append(" --bridge-network " + portgroupName)
                .append(" --image-store " + datastoreName + "/" + vchName + "/" + "images")
                .append(" --no-tlsverify --force")
                .append(" --name " + vchName)
                .append(" --volume-store " + "'" + datastoreName + "'/" + vchName + "/"
                        + "volumes/:default")
                .append(" --registry-ca " + HARBOR_CERTIFICATE_REMOTE_PATH);
        return executor.execute(createVchCommand.toString(), 360);
    }

    public CommandResult deleteVch(String vchName) {
        StringBuilder deleteVchCommand = new StringBuilder();
        deleteVchCommand.append(VIC_MACHINE_TOOL_REMOTE_PATH + " delete")
                .append(" --target " + vcenterAuthContext.getTarget())
                .append(" --user " + vcenterAuthContext.getUsername())
                .append(" --password " + vcenterAuthContext.getPassword())
                .append(" --force")
                .append(" --name " + vchName);
        return executor.execute(deleteVchCommand.toString(), 360);
    }

    public CommandResult createDvsPortGroup(String portgroupName, String datacenterName,
            String dvsName, int portsCount) {
        StringBuilder createDVSPortgroup = new StringBuilder();
        createDVSPortgroup.append("python " + SCRIPT_REMOTE_PATH)
                .append(" --command create")
                .append(" --target " + vcenterAuthContext.getTarget())
                .append(" --username " + vcenterAuthContext.getUsername())
                .append(" --password " + vcenterAuthContext.getPassword())
                .append(" --datacenter " + datacenterName)
                .append(" --dvswitch " + dvsName)
                .append(" --portgroup " + portgroupName)
                .append(" --numports " + "128");
        return executor.execute(createDVSPortgroup.toString(), 360);
    }

    public CommandResult deleteDvsPortgroup(String portgroupNameName, String datacenterName,
            String dvsName) {
        StringBuilder createDVSPortgroup = new StringBuilder();
        createDVSPortgroup.append("python " + SCRIPT_REMOTE_PATH)
                .append(" --command delete")
                .append(" --target " + vcenterAuthContext.getTarget())
                .append(" --username " + vcenterAuthContext.getUsername())
                .append(" --password " + vcenterAuthContext.getPassword())
                .append(" --datacenter " + datacenterName)
                .append(" --dvswitch " + dvsName)
                .append(" --portgroup " + portgroupNameName);
        return executor.execute(createDVSPortgroup.toString(), 360);
    }

    private void prepareEnvironment() {
        LOG.info("Preparing the VIC deployment for managing VCHs");
        createTempDirectories();
        sendScriptFileToOva();
        installPyvmomi();
        installArgparse();
        copyAndUnarchiveVicMachine();
    }

    private void createTempDirectories() {
        String createDirectoryCommand = "mkdir -p " + REMOTE_VIC_MACHINE_PATH;
        LOG.info("Creating remote directory: " + REMOTE_VIC_MACHINE_PATH);
        CommandResult result = executor.execute(createDirectoryCommand, 10);
        if (result.getExitStatus() != 0) {
            throw new RuntimeException(
                    "Could not create remote directory: " + REMOTE_VIC_MACHINE_PATH);
        }
        LOG.info("Successfully created remote directory: " + REMOTE_VIC_MACHINE_PATH);
    }

    private void sendScriptFileToOva() {
        LOG.info("Sending script file to: " + REMOTE_TEMP_DIRECTORY);
        try {
            executor.sendFile(new File(LOCAL_SCRIPT_PATH), REMOTE_TEMP_DIRECTORY);
        } catch (IOException e) {
            LOG.severe("Could not send script file to: " + REMOTE_TEMP_DIRECTORY);
            throw new RuntimeException(e);
        }
        LOG.info("Successfully sent script file to: " + REMOTE_TEMP_DIRECTORY);
    }

    private void installPyvmomi() {
        LOG.info("Installing the VMware vSphere API Python Bindings");
        String installPyvmomiCommand = "yes | pip install pyvmomi";
        CommandResult result = executor.execute(installPyvmomiCommand, 120);
        if (result.getExitStatus() != 0) {
            String message = String.format(
                    "Could not install the VMware vSphere API Python Bindings:%ncommand was: [%s]%ncommand output:%n%s",
                    installPyvmomiCommand, result.getErrorOutput());
            LOG.severe(message);
            throw new RuntimeException(message);
        }
        LOG.info("Successfully installed the VMware vSphere API Python Bindings");
    }

    private void installArgparse() {
        LOG.info("Installing the argparse python library");
        String installArgparseCommand = "yes | pip install argparse";
        CommandResult result = executor.execute(installArgparseCommand, 120);
        if (result.getExitStatus() != 0) {
            String message = String.format(
                    "Could not install python argparse library:%ncommand was: [%s]%ncommand output:%n%s",
                    installArgparseCommand, result.getErrorOutput());
            LOG.severe(message);
            throw new RuntimeException(message);
        }
        LOG.info("Successfully installed the argparse python library");
    }

    private void copyAndUnarchiveVicMachine() {
        LOG.info("Unarchiving the vic-machine tool to: " + REMOTE_VIC_MACHINE_PATH);
        String untarVicMachine = String.format("tar -xf %s -C %s", REMOTE_VIC_MACHINE_TAR_PATH,
                REMOTE_VIC_MACHINE_PATH);
        CommandResult result = executor.execute(untarVicMachine, 120);
        if (result.getExitStatus() != 0) {
            String message = String.format(
                    "Could not unarchive the vic-machine tool to: %s%ncommand was: [%s]%ncommand output:%n%s",
                    REMOTE_VIC_MACHINE_PATH,
                    untarVicMachine, result.getErrorOutput());
            LOG.severe(message);
            throw new RuntimeException(message);
        }
        LOG.info("Successfully unarchived the vic-machine tool to: " + REMOTE_VIC_MACHINE_PATH);
    }

}
