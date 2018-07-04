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

package com.vmware.admiral.adapter.tiller.client;

public class TillerClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected TillerClientException() {
        super();
    }

    protected TillerClientException(String message) {
        super(message);
    }

    protected TillerClientException(Throwable cause) {
        super(cause);
    }

    protected TillerClientException(String message, Throwable cause) {
        super(message, cause);
    }
}