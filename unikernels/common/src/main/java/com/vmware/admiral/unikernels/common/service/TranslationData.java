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

import com.vmware.admiral.unikernels.common.translator.Platform;

/*
 * All of the data is packed in this class rather than only
 * the dockerfile excluding the platform & capstanfile, just
 * in case for future extensions - multiple compilation
 * environments support.
 */
public class TranslationData {

    public String dockerfile;
    public String capstanfile;
    public Platform platform;
    public String sources;
    public String compilationPlatform;
    public String successCB;
    public String failureCB;

    public Boolean isSet() {
        return !(capstanfile == null || sources == null || compilationPlatform == null);
    }
}
