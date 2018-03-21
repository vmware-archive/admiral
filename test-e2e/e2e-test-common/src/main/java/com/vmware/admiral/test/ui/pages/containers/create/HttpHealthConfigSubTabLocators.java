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

public class HttpHealthConfigSubTabLocators extends TcpHealthConfigSubTabLocators {

    private final By HTTP_METHOD_SELECT = By
            .cssSelector("#health .container-health-path-input div:first-child select");
    private final By URL_INPUT = By.cssSelector("#health .container-health-path-input input");
    private final By HTTP_VERSION_SELECT = By
            .cssSelector("#health .container-health-path-input .httpVersion select");

    public By httpMethodSelect() {
        return HTTP_METHOD_SELECT;
    }

    public By urlInput() {
        return URL_INPUT;
    }

    public By httpVersionSelect() {
        return HTTP_VERSION_SELECT;
    }

}
