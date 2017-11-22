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

package com.vmware.admiral.test.ui.pages.logs;

import java.util.Objects;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class LogsPage extends BasicPage<LogsPage, LogsPageValidator> {

    private LogsPageValidator validator;

    @Override
    public LogsPageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new LogsPageValidator();
        }
        return validator;
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
    }

    @Override
    public LogsPage getThis() {
        return this;
    }

}
