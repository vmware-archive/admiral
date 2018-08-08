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

package com.vmware.admiral.vic.test.ui;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import com.google.common.io.Files;
import com.google.gson.JsonObject;

import org.junit.Test;

import com.vmware.admiral.test.util.ResourceUtil;
import com.vmware.admiral.test.util.host.ContainerHost;
import com.vmware.admiral.test.util.host.NimbusPhotonProvider;
import com.vmware.admiral.vic.test.VicTestProperties;
import com.vmware.admiral.vic.test.util.IdentitySourceConfigurator;

public class SetupEnvironment {

    private final Logger LOG = Logger.getLogger(getClass().getName());

    @Test
    public void setupEnvironment() {
        configureActiveDirectories();
        deployUtilityVm();
    }

    private void configureActiveDirectories() {
        LOG.info("Configuring active directories in the vCenter PSC");
        String adCsv = VicTestProperties.activeDiroctorySpecFilesCsv().trim();
        if (adCsv.isEmpty()) {
            LOG.warning(
                    "No active direcories spec files were specified in the properties file, no active directories will be configured");
            return;
        }
        List<String> adSpecFilenames = Arrays.asList(adCsv.split(","));

        String vcenterIp = VicTestProperties.vcenterIp();
        Objects.requireNonNull(vcenterIp);
        String adminUsername = VicTestProperties.defaultAdminUsername();
        Objects.requireNonNull(adminUsername);
        String adminPassword = VicTestProperties.defaultAdminPassword();
        IdentitySourceConfigurator identityConfigurator = new IdentitySourceConfigurator(
                vcenterIp, adminUsername, adminPassword);

        for (String fileName : adSpecFilenames) {
            String body = null;
            try {
                body = ResourceUtil.readTestResourceAsString(fileName.trim());
            } catch (Exception e) {
                LOG.warning(
                        "Could not read resource file with filename: " + fileName);
                continue;
            }
            if (Objects.isNull(body) || body.trim().isEmpty()) {
                LOG.warning(String.format(
                        "Could not read AD spec body from file with filename: [%s], file is empty.",
                        fileName));
            }
            identityConfigurator.addIdentitySource(body);
        }
    }

    private void deployUtilityVm() {
        LOG.info("Deploying photon utility VM");
        NimbusPhotonProvider provider = new NimbusPhotonProvider();
        ContainerHost host = provider.provide(false, false);
        JsonObject json = new JsonObject();
        json.addProperty(UtilityVmInfo.IP_FIELD_NAME, host.getIp());
        json.addProperty(UtilityVmInfo.USERNAME_FIELD_NAME, host.getSshUsername());
        json.addProperty(UtilityVmInfo.PASSWORD_FIELD_NAME, host.getSshKey());
        json.addProperty(UtilityVmInfo.VM_NAME_FIED_NAME, provider.getVmName());
        File resourcesDir = new File(UtilityVmInfo.INFO_FILE_RELATIVE_PATH);
        try {
            LOG.info("Writing utility VM info to " + resourcesDir.getAbsolutePath());
            Files.write(json.toString().getBytes(), resourcesDir);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not write utility VM info to " + resourcesDir.getAbsolutePath());
        }
    }

}
