/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.ui.pages.networks;

import static com.codeborne.selenide.Selenide.$;

import com.codeborne.selenide.Condition;

import com.vmware.admiral.test.ui.pages.common.BasicClass;
import com.vmware.admiral.test.ui.pages.common.FailableActionValidator;
import com.vmware.admiral.test.ui.pages.main.HomeTabSelectors;

public class CreateNetworkValidator extends BasicClass implements FailableActionValidator {

    @Override
    public void expectSuccess() {
        executeInFrame(0, () -> $(HomeTabSelectors.CHILD_PAGE_SLIDE).should(Condition.disappear));
    }

    @Override
    public void expectFailure() {
        throw new UnsupportedOperationException("Not implemented");
    }

}
