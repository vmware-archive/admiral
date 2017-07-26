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

package com.vmware.admiral.unikernels.common.service;

public interface UnikernelManagementURIParts {

    String TRANSLATION = "/unikernel/translate";
    String CREATION = "/unikernel/create";
    String SUCCESS_CB = "/unikernel/receive/success";
    String FAILURE_CB = "/unikernel/receive/failure";
    String DOWNLOAD = "/unikernel/download";

    String EXTERNAL =  "http://localhost:8000";
    String COMPILATION_EXTERNAL = "/route";
    String DOWNLOAD_EXTERNAL = "/download";
}
