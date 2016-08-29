/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Thrown when trying to connect to an untrusted server
 */
public class UntrustedServerException extends RuntimeException {
    private static final long serialVersionUID = 827532512726207795L;

    private final Map<String, String> identification;

    public UntrustedServerException() {
        this(new HashMap<>());
    }

    public UntrustedServerException(Map<String, String> identification) {
        this.identification = identification;
    }

    public Map<String, String> getIdentification() {
        return identification;
    }

}
