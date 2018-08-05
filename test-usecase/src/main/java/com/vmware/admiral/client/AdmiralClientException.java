/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.client;

public class AdmiralClientException extends Exception {
    private static final long serialVersionUID = 6847286904705231034L;

    private int status;

    public AdmiralClientException(String message) {
        super(message);
    }

    public AdmiralClientException(Throwable cause) {
        super(cause);
    }

    public AdmiralClientException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
