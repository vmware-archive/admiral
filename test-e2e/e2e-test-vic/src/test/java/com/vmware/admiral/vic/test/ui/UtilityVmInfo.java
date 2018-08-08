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

package com.vmware.admiral.vic.test.ui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import com.vmware.admiral.test.util.ResourceUtil;

public class UtilityVmInfo {

    public static final String INFO_FILE_RELATIVE_PATH = "target/utility-vm.json";
    public static final String IP_FIELD_NAME = "ip";
    public static final String USERNAME_FIELD_NAME = "username";
    public static final String PASSWORD_FIELD_NAME = "password";
    public static final String VM_NAME_FIED_NAME = "vmName";

    private static String ip;
    private static String username;
    private static String password;
    private static String name;

    public static void readInfo() {
        String vmInfo = ResourceUtil.readFileAsString(INFO_FILE_RELATIVE_PATH);
        JsonObject json = new Gson().fromJson(vmInfo, JsonObject.class);
        ip = json.get(IP_FIELD_NAME).getAsString();
        username = json.get(USERNAME_FIELD_NAME).getAsString();
        password = json.get(PASSWORD_FIELD_NAME).getAsString();
        name = json.get(VM_NAME_FIED_NAME).getAsString();
    }

    public static String getIp() {
        return ip;
    }

    public static String getUsername() {
        return username;
    }

    public static String getPassword() {
        return password;
    }

    public static String getVmName() {
        return name;
    }

}