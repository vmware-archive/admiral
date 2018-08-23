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

package com.vmware.admiral.test.ui.pages.containers.create;

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class NetworkTab extends BasicPage<NetworkTabValidator, NetworkTabLocators> {

    public NetworkTab(By[] iFrameLocators, NetworkTabValidator validator,
            NetworkTabLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void addPortBinding(String hostPort, String containerPort) {
        LOG.info(String.format("Adding port binding [%s:%s]", hostPort, containerPort));
        String lastHostPort = pageActions()
                .getAttribute("value", locators().lastHostPortInput())
                .trim();
        String lastContainerPort = pageActions()
                .getAttribute("value", locators().lastContainerPortInput())
                .trim();
        if (!lastHostPort.isEmpty() || !lastContainerPort.isEmpty()) {
            pageActions().click(locators().addPortBindingButton());
        }
        if (Objects.nonNull(hostPort) && !hostPort.trim().isEmpty()) {
            pageActions().sendKeys(hostPort, locators().lastHostPortInput());
        }
        if (Objects.nonNull(containerPort) && !containerPort.trim().isEmpty()) {
            pageActions().sendKeys(containerPort, locators().lastContainerPortInput());
        }
    }

    public void setPublishAllPorts(boolean publishAllPorts) {
        LOG.info(String.format("Setting the publish all ports checkbox to [%b]", publishAllPorts));
        pageActions().setCheckbox(publishAllPorts, locators().publishAllPortsCheckbox());
    }

    public void setHostname(String hostname) {
        LOG.info(String.format("Setting hostname [%s]", hostname));
        pageActions().clear(locators().hostnameInput());
        pageActions().sendKeys(hostname, locators().hostnameInput());
    }

    public void setNetworkMode(NetworkMode mode) {
        LOG.info(String.format("Setting network mode [%s]", mode.toString()));
        pageActions().selectOptionByValue(mode.toString(), locators().networkModeOption());
    }

    public void linkNetwork(String networkName, String aliases, String ipV4, String ipV6) {
        LOG.info(String.format("Connecting to network [%s]", networkName));
        String value = pageActions().getAttribute("value", locators().lastNetworkSelectOption());
        if (!value.trim().isEmpty()) {
            pageActions().click(locators().addNetworkButton());
        }
        pageActions().click(locators().lastNetworkSelectOption());
        pageActions().selectOptionByValue(networkName, locators().lastNetworkSelectOption());
        if (Objects.nonNull(aliases) && !aliases.trim().isEmpty()) {
            pageActions().sendKeys(aliases, locators().lastNetworkAliasesInput());
        }
        if (Objects.nonNull(ipV4) && !ipV4.trim().isEmpty()) {
            pageActions().sendKeys(ipV4, locators().lastNetworkIpV4Input());
        }
        if (Objects.nonNull(ipV6) && !ipV6.trim().isEmpty()) {
            pageActions().sendKeys(ipV6, locators().lastNetworkIpV6Input());
        }
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
    }

    public static enum NetworkMode {
        bridge, none, host
    }

}
