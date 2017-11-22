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

package com.vmware.admiral.test.ui.pages.applications;

import java.util.Objects;

import com.vmware.admiral.test.ui.pages.common.PageProxy;
import com.vmware.admiral.test.ui.pages.templates.CreateTemplatePage;
import com.vmware.admiral.test.ui.pages.templates.EditTemplatePage;
import com.vmware.admiral.test.ui.pages.templates.TemplatesPage;

public class CreateApplicationPage extends CreateTemplatePage {

    private EditTemplatePage editTemplatePage;

    public CreateApplicationPage(PageProxy parentProxy) {
        super(parentProxy);
    }

    @Override
    protected EditTemplatePage getEditTemplatePage() {
        if (Objects.isNull(editTemplatePage)) {
            editTemplatePage = new EditTemplatePage(new PageProxy(new TemplatesPage()));
        }
        return editTemplatePage;
    }

}
