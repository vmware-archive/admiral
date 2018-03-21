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

public class HttpHealthConfigSubTab extends TcpHealthConfigSubTab {

    public HttpHealthConfigSubTab(By[] iframeLocators,
            HttpHealthConfigSubTabLocators pageLocators) {
        super(iframeLocators, pageLocators);
    }

    public void setHttpMethod(HttpMethod method) {
        pageActions().selectOptionByText(method.toString(), locators().httpMethodSelect());
    }

    public void setUrl(String url) {
        pageActions().clear(locators().urlInput());
        pageActions().sendKeys(url, locators().urlInput());
    }

    public void setHttpversion(HttpVersion version) {
        pageActions().selectOptionByValue(version.toString(), locators().httpVersionSelect());
    }

    @Override
    protected HttpHealthConfigSubTabLocators locators() {
        return (HttpHealthConfigSubTabLocators) super.locators();
    }

    public static enum HttpMethod {
        GET, POST, OPTIONS;
    }

    public static enum HttpVersion {
        HTTP_v1_1, HTTP_v2;
    }
}
