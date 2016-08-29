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

import java.util.Map;

/**
 * Thrown when trying to connect to an untrusted server
 */
public class SshUntrustedServerException extends UntrustedServerException {
    private static final long serialVersionUID = 1653791770737731254L;

    public static final String HOST_PROP_NAME = "ssh.host";
    public static final String KEY_TYPE_PROP_NAME = "ssh.keyType";
    public static final String COMMENT_PROP_NAME = "ssh.comment";
    public static final String FINGERPRINT_PROP_NAME = "ssh.fingerprint";
    public static final String HOST_KEY_PROP_NAME = "ssh.hostKey";

    public SshUntrustedServerException() {
        super();
    }

    public SshUntrustedServerException(Map<String, String> identification) {
        super(identification);
    }
}
