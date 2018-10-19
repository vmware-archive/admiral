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

import java.util.Objects;
import java.util.logging.Logger;

import com.google.gson.Gson;

import org.apache.commons.codec.binary.Base64;

import com.vmware.admiral.test.util.HostType;
import com.vmware.admiral.test.util.SshCommandExecutor;
import com.vmware.admiral.test.util.SshCommandExecutor.CommandResult;

public class PhotonDindProvider implements ContainerHostProvider {

    private final Logger LOG = Logger.getLogger(getClass().getName());
    private final SshCommandExecutor EXECUTOR;
    private final String SCRIPT_PATH = "/etc/dind/dind-photon.sh";
    private String containerId;

    private String provisioningId;

    public PhotonDindProvider() {
        String vmIp = System.getProperty("dind.vm.ip");
        Objects.requireNonNull(vmIp,
                () -> "System property 'dind.vm.ip' must be set");
        String vmUsername = System.getProperty("dind.vm.username");
        Objects.requireNonNull(vmUsername,
                () -> "System property 'dind.vm.username' must be set");
        String vmPassword = System.getProperty("dind.vm.password");
        Objects.requireNonNull(vmPassword,
                () -> "System property 'dind.vm.password' must be set");
        String vmPort = System.getProperty("dind.vm.port");
        Objects.requireNonNull(vmPort,
                () -> "System property 'dind.vm.port' must be set");
        int port = Integer.parseInt(vmPort);
        EXECUTOR = SshCommandExecutor.createWithPasswordAuthentication(vmIp,
                vmUsername, vmPassword, port);
    }

    @Override
    public ContainerHost provide(boolean useServerCertificate, boolean useClientCertificate)
            throws Exception {
        LOG.info("Provisioning dind host");
        String command = SCRIPT_PATH + " create-host";
        if (useServerCertificate) {
            command = command + " --tls";
        }
        if (useClientCertificate) {
            command = command + " --tlsverify";
        }
        CommandResult result = EXECUTOR.execute(command, 60);
        if (result.getExitStatus() != 0) {
            throw new Exception(
                    String.format("Could not run dind host, error output: %s", result.getOutput()));
        }
        LOG.info("Successfully provisioned dind host");
        Gson gson = new Gson();
        DindResponse response = gson.fromJson(result.getOutput(), DindResponse.class);
        provisioningId = response.provisioningId;
        DindHostInfo hostInfo = response.hostsInfo[0];
        containerId = hostInfo.containerId;
        ContainerHost host = new ContainerHost();
        host.setHostType(HostType.DOCKER);
        host.setPort(hostInfo.port);
        host.setIp(hostInfo.ip);
        if (useServerCertificate) {
            host.setServerCertificate(new String(Base64.decodeBase64(hostInfo.serverCertBase64)));
            if (useClientCertificate) {
                host.setClientKeyAndCertificate(
                        new String(Base64.decodeBase64(hostInfo.clientKeyBase64)),
                        new String(Base64.decodeBase64(hostInfo.clientCertBase64)));
            }
        }
        return host;
    }

    public CommandResult executeCommandInContainer(String command, int timeoutSeconds) {
        return EXECUTOR.execute(String.format("docker exec %s sh -c '%s'", containerId, command),
                timeoutSeconds);
    }

    @Override
    public void killContainerHost() throws Exception {
        String command = SCRIPT_PATH + " destroy --id " + provisioningId;
        CommandResult result = EXECUTOR.execute(command, 30);
        if (result.getExitStatus() != 0) {
            throw new Exception(String
                    .format("Could not kill dind host, error output: %s", result.getOutput()));
        }
    }

    private static class DindResponse {
        public String provisioningId;
        public DindHostInfo[] hostsInfo;
    }

    private static class DindHostInfo {
        public String containerId;
        public String ip;
        public int port;
        public String serverCertBase64;
        public String clientKeyBase64;
        public String clientCertBase64;
    }

}
