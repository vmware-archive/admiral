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

import org.openqa.selenium.By;

public class NetworkTabLocators extends CreateContainerPageLocators {

    private final By PUBLISH_AlL_PORTS_CHECKBOX = By
            .cssSelector(".container-ports-publish-input .checkbox-control");
    private final By LAST_HOST_PORT_INPUT = By.cssSelector(
            ".container-ports-input .multicolumn-input:last-child .inline-input.form-control[name='hostPort']");
    private final By LAST_CONTAINER_PORT_INPUT = By.cssSelector(
            ".container-ports-input .multicolumn-input:last-child .inline-input.form-control[name='containerPort']");
    private final By ADD_PORT_BINDING_BUTTON = By.cssSelector(
            ".container-ports-input .multicolumn-input:last-child .fa-plus");
    private final By HOSTNAME_INPUT = By.cssSelector(".container-hostname-input .form-control");
    private final By NETWORK_MODE_OPTION = By
            .cssSelector(".container-network-mode-input .form-control");
    private final By ADD_NETWORK_BUTTON = By.cssSelector(
            ".container-networks-input .multicolumn-input:last-child .btn.btn-circle.fa.fa-plus");
    private final By LAST_NETWORK_SELECT = By
            .cssSelector(
                    ".container-networks-input .multicolumn-input:last-child .inline-input[name='network']");
    private final By LAST_NETWORK_ALIASES = By
            .cssSelector(
                    ".container-networks-input .multicolumn-input:last-child .inline-input[name='aliases']");
    private final By LAST_NETWORK_IPV4 = By
            .cssSelector(
                    ".container-networks-input .multicolumn-input:last-child .inline-input[name='ipv4_address']");
    private final By LAST_NETWORK_IPV6 = By
            .cssSelector(
                    ".container-networks-input .multicolumn-input:last-child .inline-input[name='ipv6_address']");

    public By publishAllPortsCheckbox() {
        return PUBLISH_AlL_PORTS_CHECKBOX;
    }

    public By lastContainerPortInput() {
        return LAST_CONTAINER_PORT_INPUT;
    }

    public By lastHostPortInput() {
        return LAST_HOST_PORT_INPUT;
    }

    public By addPortBindingButton() {
        return ADD_PORT_BINDING_BUTTON;
    }

    public By hostnameInput() {
        return HOSTNAME_INPUT;
    }

    public By networkModeOption() {
        return NETWORK_MODE_OPTION;
    }

    public By lastNetworkSelectOption() {
        return LAST_NETWORK_SELECT;
    }

    public By lastNetworkAliasesInput() {
        return LAST_NETWORK_ALIASES;
    }

    public By lastNetworkIpV4Input() {
        return LAST_NETWORK_IPV4;
    }

    public By addNetworkButton() {
        return ADD_NETWORK_BUTTON;
    }

    public By lastNetworkIpV6Input() {
        return LAST_NETWORK_IPV6;
    }

}
