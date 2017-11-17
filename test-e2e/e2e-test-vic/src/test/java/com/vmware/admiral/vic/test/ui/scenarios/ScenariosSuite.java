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

package com.vmware.admiral.vic.test.ui.scenarios;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.vic.test.ui.BaseSuite;
import com.vmware.admiral.vic.test.ui.PropertiesNames;
import com.vmware.admiral.vic.test.ui.util.IdentitySourceConfigurator;

@RunWith(Suite.class)
@SuiteClasses({
        RBACAndItemsProjectAwareness.class })
public class ScenariosSuite extends BaseSuite {

    @BeforeClass
    public static void configureActiveDirectories() throws IOException {
        Properties props = FileUtil.getProperties("/" + PropertiesNames.PROPERTIES_FILE_NAME, true);
        String vcenterAddress = props.getProperty(PropertiesNames.VCENTER_IP_PROPERTY);
        Objects.requireNonNull(vcenterAddress);
        String adminUsername = props.getProperty(PropertiesNames.DEFAULT_ADMIN_USERNAME_PROPERTY);
        Objects.requireNonNull(adminUsername);
        String adminPassword = props.getProperty(PropertiesNames.DEFAULT_ADMIN_PASSWORD_PROPERTY);
        IdentitySourceConfigurator identityConfigurator = new IdentitySourceConfigurator(
                vcenterAddress, adminUsername, adminPassword);
        List<String> adSpecFilenames = Arrays
                .asList(properties
                        .getProperty(PropertiesNames.ACTIVE_DIRECTORIES_SPEC_FILES_CSV_PROPERTY)
                        .split(","));
        for (String fileName : adSpecFilenames) {
            String body = FileUtil.getResourceAsString("/" + fileName.trim(), true);
            identityConfigurator.addIdentitySource(body);
        }
    }

}
