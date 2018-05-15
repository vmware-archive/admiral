/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.vic.test.ui.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.logging.Logger;

import org.junit.runner.Description;

import com.vmware.admiral.test.util.AuthContext;
import com.vmware.admiral.test.util.BaseRule;
import com.vmware.admiral.test.util.SSHCommandExecutor.CommandResult;

public class VchPoolRule extends BaseRule {

    private static Stack<String> availableHosts = new Stack<>();
    private static int counter = 1;

    private final Logger LOG = Logger.getLogger(getClass().getName());
    private final String DATACENTER_NAME = "vcqaDC";
    private final String DVS_NAME = "vic-bridge-dvs";
    private final String VCH_NAME_PREFIX = "vch-";
    private final String DVS_PORTGROUP_NAME_PREFIX = "vch-pg-";

    private String DATASTORE_NAME = "sharedVmfs-0";
    private VchUtil vchUtil;
    private List<String> testUsedHosts = new ArrayList<>();

    public VchPoolRule() {
        AuthContext vicOvaAuthContext = new AuthContext(TestProperties.vicIp(),
                TestProperties.vicSshUsername(), TestProperties.vicSshPassword());
        AuthContext vcenterAuthContext = new AuthContext(
                TestProperties.vcenterIp(),
                TestProperties.defaultAdminUsername(), TestProperties.defaultAdminPassword());
        vchUtil = new VchUtil(vicOvaAuthContext, vcenterAuthContext);
    }

    @Override
    protected void succeeded(Description description) {
        for (int i = 0; i < testUsedHosts.size(); i++) {
            availableHosts.push(testUsedHosts.get(i));
        }
    }

    public String getHostFromThePool() {
        LOG.info("Requested a VCH from the pool");
        if (availableHosts.isEmpty()) {
            LOG.info("The pool is empty, creating a new VCH");
            return createVch(null);
        } else {
            String ip = availableHosts.pop();
            LOG.info(String.format("Providing a VCH from the pool with ip [%s]", ip));
            testUsedHosts.add(ip);
            return ip;
        }
    }

    public String createVch(String additionalParameters) {
        String vchName = VCH_NAME_PREFIX + counter;
        String portgroupName = DVS_PORTGROUP_NAME_PREFIX + counter;
        createPortgroup(portgroupName, counter);
        counter++;
        LOG.info("Creating VCH with name: " + vchName);
        CommandResult result = vchUtil.createVch(vchName, DATASTORE_NAME, portgroupName,
                additionalParameters);
        if (result.getExitStatus() != 0) {
            String error = String.format(
                    "Creating vch failed, command exit status [%d], command output:%n%s",
                    result.getExitStatus(), result.getOutput());
            LOG.severe(error);
            throw new RuntimeException(error);
        } else {
            String ip = getIpFromLogs(result.getOutput());
            LOG.info(
                    String.format("Successfully created a VCH with name [%s] with ip [%s]", vchName,
                            ip));
            testUsedHosts.add(ip);
            return ip;
        }
    }

    private void createPortgroup(String name, int vlanId) {
        LOG.info("Creating portgroup with name: " + name);
        CommandResult result = vchUtil.createDvsPortGroup(name, DATACENTER_NAME, DVS_NAME, 128,
                vlanId);
        if (result.getExitStatus() != 0) {
            String error = String.format(
                    "Creating portgroup failed, command exit status [%d], command output:%n%s",
                    result.getExitStatus(), result.getErrorOutput());
            LOG.severe(error);
            throw new RuntimeException(error);
        }
        LOG.info("Successfully created portgroup with name: " + name);
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

}
