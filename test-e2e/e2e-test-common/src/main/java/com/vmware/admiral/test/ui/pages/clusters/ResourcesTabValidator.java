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

package com.vmware.admiral.test.ui.pages.clusters;

import static com.codeborne.selenide.Selenide.Wait;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.clusters.ClustersPage.HostState;
import com.vmware.admiral.test.ui.pages.common.PageValidator;

public class ResourcesTabValidator extends PageValidator<ResourcesTabLocators> {

    public ResourcesTabValidator(By[] iFrameLocators, ResourcesTabLocators pageLocators) {
        super(iFrameLocators, pageLocators);
    }

    @Override
    public void validateIsCurrentPage() {
        element(locators().resourcesButton()).shouldHave(Condition.cssClass("active"));
    }

    public void validateHostState(String hostUrl, HostState... states) {
        Wait().withTimeout(10, TimeUnit.SECONDS)
                .until(d -> pageActions().getText(locators().hostStateByUrl(hostUrl))
                        .length() > 0);
        List<String> expectedStates = Arrays.asList(states).stream().map(s -> s.toString())
                .collect(Collectors.toList());
        String stateText = pageActions().getText(locators().hostStateByUrl(hostUrl));
        if (!expectedStates.contains(stateText)) {
            throw new AssertionError(
                    String.format("Host state mismatch, expected [%s], actual [%s]",
                            expectedStates.toString().replaceAll(", ", " or "), stateText));
        }
    }

}
