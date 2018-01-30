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

package com.vmware.admiral.test.ui.pages.common;

import org.openqa.selenium.By;

public abstract class BasicPage<V extends PageValidator<L>, L extends PageLocators>
        extends BasicClass<L> {

    private final V validator;

    public BasicPage(By[] iFrameLocators, V validator, L pageLocators) {
        super(iFrameLocators, pageLocators);
        this.validator = validator;
    }

    public V validate() {
        return validator;
    }

    public abstract void waitToLoad();

}
