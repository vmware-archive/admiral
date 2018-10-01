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
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.vmware.admiral.test.util.HostType;
import com.vmware.admiral.test.util.SshCommandExecutor;
import com.vmware.admiral.test.util.SshCommandExecutor.CommandResult;

public class NimbusPhotonProvider implements ContainerHostProvider {

    private final Logger LOG = Logger.getLogger(getClass().getName());

    private final String SSH_USERNAME = "root";
    private final String SSH_PASSWORD = "VMware1!";
    private final int DEPLOY_TEMPLATE_TIMEOUT_SECONDS = 720;
    private final int KILL_VM_TIMEOUT_SECONDS = 120;
    private final String NIMBUS_TARGET = "nimbus-gateway.eng.vmware.com";
    private final String OVF_PATH = "/templates/admiral-test-photon-v1/admiral-test-photon-v1.ovf";
    private final String VM_NAME_PREFIX = "-admiral-ui-test-";
    private final SshCommandExecutor NIMBUS_EXECUTOR;
    private final String NIMBUS_USERNAME;
    private String vmName;
    SshCommandExecutor executor;
    private ContainerHost host;

    public NimbusPhotonProvider() {
        String nimbusUsername = System.getProperty("nimbus.username");
        Objects.requireNonNull(nimbusUsername,
                () -> "System property 'nimbus.username' must be set");
        NIMBUS_USERNAME = nimbusUsername;
        String nimbusPassword = System.getProperty("nimbus.password");
        Objects.requireNonNull(nimbusPassword,
                () -> "System property 'nimbus.password' must be set");
        NIMBUS_EXECUTOR = SshCommandExecutor.createWithPasswordAuthentication(NIMBUS_TARGET,
                NIMBUS_USERNAME, nimbusPassword);
    }

    @Override
    public ContainerHost provide(boolean useServerCertificate, boolean useClientCertificate) {
        if (useServerCertificate == false && useClientCertificate == true) {
            throw new IllegalArgumentException(
                    "The option 'useClientCertificate' can be used only with conjunction with the 'useServerCertificate' option");
        }
        if (Objects.isNull(host)) {
            host = deployContainerHost(useServerCertificate, useClientCertificate);
        }
        return host;
    }

    protected ContainerHost deployContainerHost(Boolean useServerSideSsl,
            boolean useClientCertificate) {
        this.vmName = NIMBUS_USERNAME + VM_NAME_PREFIX + UUID.randomUUID();
        LOG.info("Deploying PhotonOs host with name: " + this.vmName);
        String command = String.format("nimbus-ovfdeploy --lease 1 %s %s", this.vmName, OVF_PATH);
        CommandResult result = NIMBUS_EXECUTOR.execute(command, DEPLOY_TEMPLATE_TIMEOUT_SECONDS);
        validateResult(result, "Could not deploy PhotonOs host on nimbus");
        String hostIp = getIpFromLogs(result.getOutput());
        LOG.info(String.format("Successfully deployed PhotonOs host with name: [%s] and ip: [%s]",
                this.vmName, hostIp));
        ContainerHost containerHost = new ContainerHost();
        containerHost.setIp(hostIp);
        containerHost.setHostType(HostType.DOCKER);
        containerHost.setSshCredentials(SSH_USERNAME, SSH_PASSWORD, AuthKeyType.PASSWORD);
        executor = SshCommandExecutor.createWithPasswordAuthentication(
                hostIp, SSH_USERNAME, SSH_PASSWORD);
        if (useServerSideSsl) {
            containerHost.setPort(SECURE_PORT);
            configureServerCertificates(containerHost);
            if (useClientCertificate) {
                configureClientCertificates(containerHost);
            }
            LOG.info("Restarting the docker daemon and service");
            command = "systemctl daemon-reload && systemctl restart docker";
            validateResult(executor.execute(command, 120),
                    "Could not start the docker service");
        } else {
            containerHost.setPort(INSECURE_PORT);
        }
        return containerHost;
    }

    @Override
    public void killContainerHost() {
        LOG.info("Killing PhotonOs host with name: " + vmName);
        CommandResult result = null;
        try {
            result = NIMBUS_EXECUTOR.execute("nimbus-ctl kill " + vmName,
                    KILL_VM_TIMEOUT_SECONDS);
        } catch (Throwable e) {
            LOG.warning(String.format("Could not kill VM with name '%s', error:%n%s ", vmName,
                    ExceptionUtils.getStackTrace(e)));
            return;
        }
        if (result.getExitStatus() != 0) {
            LOG.warning("Could not kill VM with name: " + vmName + ", error: "
                    + result.getErrorOutput());
        } else {
            LOG.info("Successfully killed VM with name: " + vmName);
        }
    }

    private void configureServerCertificates(ContainerHost containerHost) {
        LOG.info("Enabling SSL on the docker rest api");
        executor.execute("mkdir -p /etc/docker/certs", 10);
        StringBuilder commandBuilder = new StringBuilder();
        commandBuilder.append("openssl req -newkey rsa:4096 -nodes -sha256")
                .append(" -keyout /etc/docker/certs/ca.key -x509 -days 365")
                .append(String.format(" -out /etc/docker/certs/ca.crt -subj \"/CN=%s\"",
                        containerHost.getIp()))
                .append(" -extensions SAN -config <(cat /etc/ssl/openssl.cnf; printf \"[SAN]\nsubjectAltName=IP:"
                        + containerHost.getIp() + "\")")
                .append(String.format(
                        " && sed -i 's|-H=tcp://0.0.0.0:%s|-H=tcp://0.0.0.0:%s|' /lib/systemd/system/docker.service",
                        INSECURE_PORT, SECURE_PORT))
                .append(" && sed -i 's/ExecStart.*/& --tls --tlskey=\\/etc\\/docker\\/certs\\/ca.key --tlscert=\\/etc\\/docker\\/certs\\/ca.crt/g' /lib/systemd/system/docker.service")
                .append(String.format(
                        " && iptables -A INPUT -p tcp --dport %s -j ACCEPT && echo \"iptables -A INPUT -p tcp --dport %s -j ACCEPT\" >> /etc/systemd/scripts/iptables",
                        SECURE_PORT, SECURE_PORT));
        validateResult(executor.execute(commandBuilder.toString(), 20),
                "Could not configure the docker server certificates");
        String command = "cat /etc/docker/certs/ca.crt";
        CommandResult result = executor.execute(command, 20);
        validateResult(result, "Could not read the server certificate");
        String serverCertificate = result.getOutput();
        containerHost.setServerCertificate(serverCertificate);
        LOG.info("Successfully enabled SSL on the docker rest api");
    }

    private void configureClientCertificates(ContainerHost host) {
        LOG.info("Generating and signing client certificate pair");
        StringBuilder commandBuilder = new StringBuilder();
        commandBuilder.append("mkdir -p /etc/docker/certs")
                .append(" && cd /etc/docker/certs")
                .append(" && openssl genrsa -aes256 -out certificate-authority.pem -passout pass:qweasd 2048")
                .append(" && openssl req -new -x509 -days 365 -passin pass:qweasd -key certificate-authority.pem -sha256 -out certificate-authority.crt -subj \"/CN=*\"")
                .append(" && openssl genrsa -out client.pem 2048")
                .append(" && openssl req -subj \"/CN=*\" -new -key client.pem -out client.csr")
                .append(" && echo extendedKeyUsage = clientAuth > extfile.cnf")
                .append(" && openssl x509 -req -days 365 -in client.csr -CA certificate-authority.crt -CAkey certificate-authority.pem -CAcreateserial -out client.crt -extfile extfile.cnf -passin pass:qweasd")
                .append(" && sed -i 's/ExecStart.*/& --tlsverify --tlscacert \\/etc\\/docker\\/certs\\/certificate-authority.crt/g' /lib/systemd/system/docker.service");
        validateResult(executor.execute(commandBuilder.toString(), 20),
                "Could not generate and configure docker client certificates");
        String command = "cat /etc/docker/certs/client.pem";
        CommandResult result = executor.execute(command, 20);
        validateResult(result, "Could not read generated client key");
        String key = result.getOutput();
        command = "cat /etc/docker/certs/client.crt";
        result = executor.execute(command, 20);
        validateResult(result, "Could not read generated client certificate");
        String cert = result.getOutput();
        host.setClientKeyAndCertificate(key, cert);
        LOG.info("Successfully generated CA and client certificates");
    }

    private String getIpFromLogs(String logs) {
        Pattern pt = Pattern.compile(
                "--- start nimbus json results ---(.+?)--- end nimbus json results ---",
                Pattern.DOTALL);
        Matcher m = pt.matcher(logs);
        if (m.find()) {
            String json = m.group(1);
            return new Gson().fromJson(json, JsonObject.class).getAsJsonPrimitive("ip4")
                    .getAsString();
        } else {
            killContainerHost();
            throw new RuntimeException(
                    "Could not extract machine json info from nimbus command log");
        }
    }

    private void validateResult(CommandResult result, String errorMessage) {
        if (result.getExitStatus() != 0) {
            throw new RuntimeException(errorMessage + ", error output: " + result.getErrorOutput());
        }
    }

    @Override
    public String getVmName() {
        return vmName;
    }

}
