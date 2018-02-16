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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.vmware.admiral.test.util.AuthContext;
import com.vmware.admiral.test.util.SSHCommandExecutor.CommandResult;

public class CreateVchRule implements TestRule {

    private final Logger LOG = Logger.getLogger(getClass().getName());

    private static final AtomicInteger vlanId = new AtomicInteger(0);

    private String datacenterName = "vcqaDC";
    private String dvsName = "vic-bridge-dvs";
    private String datastoreName = "sharedVmfs-0";

    private int hostsCount;
    private String namePrefix;
    private boolean keepOnSuccess = false;

    private final String[] hostsIps;

    private VchUtil vchUtil;

    public CreateVchRule(AuthContext vicOvaAuthContext, AuthContext vcenterAuthContext,
            String namePrefix,
            int hostsCount) {
        this.hostsCount = hostsCount;
        hostsIps = new String[hostsCount];
        this.namePrefix = namePrefix;
        vchUtil = new VchUtil(vicOvaAuthContext, vcenterAuthContext);
    }

    public CreateVchRule datacenterName(String datacenterName) {
        this.datacenterName = datacenterName;
        return this;
    }

    public CreateVchRule dvsName(String dvsName) {
        this.dvsName = dvsName;
        return this;
    }

    public CreateVchRule datastoreName(String datastoreName) {
        this.datacenterName = datastoreName;
        return this;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                createPortGroupsAndVchs(namePrefix);
                base.evaluate();
                if (!keepOnSuccess) {
                    deleteAllHostsAndPortgroups(namePrefix);
                }
            }
        };
    }

    public String[] getHostsIps() {
        return hostsIps.clone();
    }

    protected void createPortGroupsAndVchs(String testName) {
        for (int i = 0; i < hostsCount; i++) {
            String portgroupName = testName + "-" + (i + 1);
            createPortgroup(portgroupName);
            String vchName = testName + "-" + (i + 1);
            String ip = createVch(vchName, portgroupName);
            hostsIps[i] = ip;
        }
    }

    private void createPortgroup(String name) {
        LOG.info("Creating portgroup with name: " + name);
        CommandResult result = vchUtil.createDvsPortGroup(name, datacenterName, dvsName, 128,
                vlanId.incrementAndGet());
        if (result.getExitStatus() != 0) {
            String error = String.format(
                    "Creating portgroup failed, command exit status [%d], command output:%n%s",
                    result.getExitStatus(), result.getErrorOutput());
            LOG.severe(error);
            throw new RuntimeException(error);
        }
        LOG.info("Successfully created portgroup with name: " + name);
    }

    private void deletePortgroup(String name) {
        LOG.info("Deleting portgroup with name: " + name);
        CommandResult result = vchUtil.deleteDvsPortgroup(name, datacenterName, dvsName);
        if (result.getExitStatus() != 0) {
            String error = String.format(
                    "Deleting portgroup failed, command exit status [%d], command output:%n%s",
                    result.getExitStatus(), result.getErrorOutput());
            LOG.severe(error);
        } else {
            LOG.info("Successfully deleted portgroup with name: " + name);
        }
    }

    private String createVch(String name, String portgroupName) {
        LOG.info("Creating VCH with name: " + name);
        CommandResult result = vchUtil.createVch(name, datastoreName, portgroupName);
        if (result.getExitStatus() != 0) {
            String error = String.format(
                    "Creating vch failed, command exit status [%d], command output:%n%s",
                    result.getExitStatus(), result.getOutput());
            LOG.severe(error);
            throw new RuntimeException(error);
        } else {
            String ip = getIpFromLogs(result.getOutput());
            LOG.info(String.format("Successfully created a VCH with name [%s] with ip [%s]", name,
                    ip));
            return ip;
        }
    }

    private void deleteVch(String name) {
        LOG.info("Deleting VCH with name: " + name);
        CommandResult result = vchUtil.deleteVch(name);
        if (result.getExitStatus() == 0) {
            LOG.info("Successfully deleted VCH with name: " + name);
        } else {
            LOG.warning(String.format("Could not delete VCH with name: %s, command output is:%n%s",
                    name, result.getOutput()));
        }
    }

    private String getIpFromLogs(String logs) {
        List<String> lines = Arrays.asList(logs.split("\n"));
        String line = lines.stream()
                .filter(l -> l.contains("Obtained IP address for client interface"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(""));
        String ip = line.substring(line.indexOf("\\\"") + 2, line.lastIndexOf("\\\""));
        return ip;
    }

    private void deleteAllHostsAndPortgroups(String testName) {
        for (int i = 0; i < hostsCount; i++) {
            deleteVch(testName + "-" + (i + 1));
            deletePortgroup(testName + "-" + (i + 1));
        }
    }

    public CreateVchRule keepOnSuccess() {
        this.keepOnSuccess = true;
        return this;
    }

}
