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

package com.vmware.admiral.test.util.host;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.vmware.admiral.test.util.HostType;
import com.vmware.admiral.test.util.SshCommandExecutor;
import com.vmware.admiral.test.util.SshCommandExecutor.CommandResult;

public class VcenterVchProvider implements ContainerHostProvider {

    private final Logger LOG = Logger.getLogger(getClass().getName());

    // private final String SSH_USERNAME = "root";
    // private final String SSH_PASSWORD = "VMware1!";
    private final int SSH_COMMAND_TIMEOUT = 600; // 10 min
    private final String VIC_MACHINE_TIMEOUT = "10m0s";
    private final String VCENTER_IP;
    private final String VCENTER_USERNAME;
    private final String VCENTER_PASSWORD;
    private final String VCENTER_DATACENTER_NAME;
    private final String VCENTER_DATASTORE_NAME;
    private final String VCENTER_DVS_NAME;
    private final String COMMAND_OUTPUT_FOLDER = "/tmp/deployed-hosts/";
    private final String GENERATED_CERTS_RELATIVE_PATH = "/certs/";
    private final Random RANDOM = new Random();
    private final int PORTS_COUNT = 128;
    private final SshCommandExecutor EXECUTOR;
    private final String VIC_MACHINE_CLI_PATH;
    private final DvsPortgroupUtil PORTGROUP_UTIL;
    private String vmName;
    private ContainerHost host;

    private static final AtomicInteger VLAN_ID = new AtomicInteger(1);

    public VcenterVchProvider() {
        VCENTER_IP = System.getProperty("vcenter.ip");
        Objects.requireNonNull(VCENTER_IP, () -> "System property 'vcenter.ip' must be set");
        VCENTER_USERNAME = System.getProperty("vcenter.username");
        Objects.requireNonNull(VCENTER_USERNAME,
                () -> "System property 'vcenter.username' must be set");
        VCENTER_PASSWORD = System.getProperty("vcenter.password");
        Objects.requireNonNull(VCENTER_PASSWORD,
                () -> "System property 'vcenter.password' must be set");
        VCENTER_DATACENTER_NAME = System.getProperty("datacenter.name");
        Objects.requireNonNull(VCENTER_DATACENTER_NAME,
                () -> "System property 'datacenter.name' must be set");
        VCENTER_DATASTORE_NAME = System.getProperty("datastore.name");
        Objects.requireNonNull(VCENTER_DATASTORE_NAME,
                () -> "System property 'datastore.name' must be set");
        VCENTER_DVS_NAME = System.getProperty("dvs.name");
        Objects.requireNonNull(VCENTER_DVS_NAME, () -> "System property 'dvs.name' must be set");
        String vicMachineVmIp = System.getProperty("vic.machine.vm.ip");
        Objects.requireNonNull(vicMachineVmIp,
                () -> "System property 'vic.machine.vm.ip' must be set");
        String vicMachineVmSshUsername = System.getProperty("vic.machine.vm.ssh.username");
        Objects.requireNonNull(vicMachineVmSshUsername,
                () -> "System property 'vic.machine.vm.ssh.username' must be set");
        String vicMachineVmSshPassword = System.getProperty("vic.machine.vm.ssh.password");
        Objects.requireNonNull(vicMachineVmSshPassword,
                () -> "System property 'vic.machine.vm.ssh.password' must be set");
        VIC_MACHINE_CLI_PATH = System.getProperty("vic.machine.cli.path");
        Objects.requireNonNull(VIC_MACHINE_CLI_PATH,
                () -> "System property 'vic.machine.cli.path' must be set");
        EXECUTOR = SshCommandExecutor
                .createWithPasswordAuthentication(vicMachineVmIp, vicMachineVmSshUsername,
                        vicMachineVmSshPassword);
        this.PORTGROUP_UTIL = new DvsPortgroupUtil(VCENTER_IP, VCENTER_USERNAME, VCENTER_PASSWORD);
    }

    @Override
    public ContainerHost provide(boolean useServerCertificate, boolean useClientCertificate) {
        if (useServerCertificate == false && useClientCertificate == true) {
            throw new IllegalArgumentException(
                    "The option 'useClientCertificate' can be used only with conjunction with the 'useServerCertificate' option");
        }
        if (Objects.isNull(host)) {
            this.vmName = "VCH-" + Long.toString(System.currentTimeMillis()) + "-"
                    + String.format("%04d", RANDOM.nextInt(10000));
            createDvsPortGroup(vmName, VCENTER_DATACENTER_NAME, VCENTER_DVS_NAME, PORTS_COUNT,
                    VLAN_ID.getAndIncrement());
            host = deployVch(useServerCertificate, useClientCertificate);
            return host;
        }
        return host;
    }

    @Override
    public void killContainerHost() {
        deleteVch();
        deleteDvsPortgroup();
    }

    private ContainerHost deployVch(boolean useServerSideSsl, boolean useClientCertificate) {
        LOG.info("Deploying VCH with name: " + vmName);
        StringBuilder createVchCommand = new StringBuilder();
        createVchCommand
                .append("mkdir -p " + COMMAND_OUTPUT_FOLDER)
                .append("cd /tmp/deployed-hosts/ && " + VIC_MACHINE_CLI_PATH + " create")
                .append(" --target " + VCENTER_IP)
                .append(" --user " + VCENTER_USERNAME)
                .append(" --password " + VCENTER_PASSWORD)
                .append(" --bridge-network " + vmName)
                .append(" --image-store " + VCENTER_DATASTORE_NAME + "/" + vmName + "/" + "images")
                .append(" --force")
                .append(" --name " + vmName)
                .append(" --volume-store " + "'" + VCENTER_DATASTORE_NAME + "'/" + vmName + "/"
                        + "volumes/:default")
                .append(" --insecure-registry=*")
                .append(" --tls-cert-path " + COMMAND_OUTPUT_FOLDER + vmName
                        + GENERATED_CERTS_RELATIVE_PATH)
                .append(" --timeout " + VIC_MACHINE_TIMEOUT);
        ContainerHost host = new ContainerHost();
        host.setHostType(HostType.VCH);
        if (!useServerSideSsl) {
            createVchCommand.append(" --no-tls");
            host.setPort(INSECURE_PORT);
        } else {
            host.setPort(SECURE_PORT);
            if (useClientCertificate) {
                createVchCommand.append(" --tls-cname=*");
            } else {
                createVchCommand.append(" --no-tlsverify");
            }
        }
        CommandResult result = EXECUTOR.execute(createVchCommand.toString(), SSH_COMMAND_TIMEOUT);
        if (result.getExitStatus() != 0) {
            throw new RuntimeException(
                    "Could not deploy VCH, error output: " + result.getOutput());
        }
        String ip = getIpFromLogs(result.getOutput());
        host.setIp(ip);
        if (useServerSideSsl) {
            String command = "cat " + COMMAND_OUTPUT_FOLDER + vmName + GENERATED_CERTS_RELATIVE_PATH
                    + "server-cert.pem";
            result = EXECUTOR.execute(command, 20);
            if (result.getExitStatus() != 0) {
                throw new RuntimeException(
                        "Could not read generated vch certificate, error: "
                                + result.getErrorOutput());
            }
            host.setServerCertificate(result.getOutput());
            if (useClientCertificate) {
                command = "cat /tmp/deployed-hosts/" + vmName + GENERATED_CERTS_RELATIVE_PATH
                        + "cert.pem";
                result = EXECUTOR.execute(command, 20);
                if (result.getExitStatus() != 0) {
                    throw new RuntimeException(
                            "Could not read generated client certificate, error: "
                                    + result.getErrorOutput());
                }
                String clientCert = result.getOutput();
                command = "cat /tmp/deployed-hosts/" + vmName + GENERATED_CERTS_RELATIVE_PATH
                        + "key.pem";
                result = EXECUTOR.execute(command, 20);
                if (result.getExitStatus() != 0) {
                    throw new RuntimeException(
                            "Could not read generated client key, error: "
                                    + result.getErrorOutput());
                }
                String clientKey = result.getOutput();
                host.setClientKeyAndCertificate(clientKey, clientCert);
            }
        }
        // enableSshAccess(vmName);
        LOG.info(String.format("Successfully deployed VCH with name [%s] and ip [%s]", vmName, ip));
        return host;
    }

    private void deleteVch() {
        LOG.info("Killing VCH with name: " + vmName);
        StringBuilder deleteVchCommand = new StringBuilder();
        deleteVchCommand.append(VIC_MACHINE_CLI_PATH + " delete")
                .append(" --target " + VCENTER_IP)
                .append(" --user " + VCENTER_USERNAME)
                .append(" --password " + VCENTER_PASSWORD)
                .append(" --force")
                .append(" --name " + vmName)
                .append(" --timeout " + VIC_MACHINE_TIMEOUT);
        CommandResult result = null;
        try {
            result = EXECUTOR.execute(deleteVchCommand.toString(),
                    SSH_COMMAND_TIMEOUT);
        } catch (Throwable e) {
            LOG.warning(String.format("Could not kill VM with name '%s', error:%n%s", vmName,
                    ExceptionUtils.getStackTrace(e)));
            return;
        }
        if (result.getExitStatus() != 0) {
            LOG.warning(String.format("Could not kill VM with name '%s', error:%n%s", vmName,
                    result.getOutput()));
            return;
        }
        LOG.info(String.format("Successfully killed VM with name '%s'", vmName));
    }

    // private void enableSshAccess(String vmName) {
    // StringBuilder debugVchCommand = new StringBuilder();
    // debugVchCommand.append(VIC_MACHINE_CLI_PATH + " debug")
    // .append(" --target " + VCENTER_IP)
    // .append(" --user " + VCENTER_USERNAME)
    // .append(" --password " + VCENTER_PASSWORD)
    // .append(" --enable-ssh")
    // .append(" --rootpw " + SSH_PASSWORD)
    // .append(" --name " + vmName);
    // LOG.info("Enabling ssh access on the VCH");
    // String thumbprint = EXECUTOR.execute(
    // debugVchCommand.toString() + " | sed -rn 's/(.*thumbprint=)([^,]+)(\\).*)/\\2/p'",
    // 20).getOutput();
    // debugVchCommand.append(" --thumbprint " + thumbprint);
    // CommandResult result = EXECUTOR.execute(debugVchCommand.toString(), 60);
    // if (result.getExitStatus() != 0) {
    // LOG.warning("Could not enable ssh access on the VCH, errror: " + result.getOutput());
    // }
    // }

    private void createDvsPortGroup(String portgroupName, String datacenterName,
            String dvsName, int portsCount, int vlanId) {
        PORTGROUP_UTIL.createDvsPortgroup(VCENTER_DATACENTER_NAME, VCENTER_DVS_NAME, portgroupName,
                portsCount, vlanId);
    }

    private void deleteDvsPortgroup() {
        PORTGROUP_UTIL.deleteDvsPortgroup(VCENTER_DATACENTER_NAME, vmName);
    }

    private String getIpFromLogs(String logs) {
        List<String> lines = Arrays.asList(logs.split("\n"));
        String line = lines.stream()
                .filter(l -> l.contains("Obtained IP address for client interface"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "Could not obtain the machine ip from the logs"));
        String ip = line.substring(line.indexOf("\\\"") + 2, line.lastIndexOf("\\\""));
        return ip;
    }

    @Override
    public String getVmName() {
        return vmName;
    }

}
