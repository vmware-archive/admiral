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

package com.vmware.admiral.tiller;

import org.microbean.helm.HelmException;

public class TillerFactoryException extends HelmException {

    private static final long serialVersionUID = 1L;

    protected TillerFactoryException() {
        super();
    }

    protected TillerFactoryException(String message) {
        super(message);
    }

    protected TillerFactoryException(Throwable cause) {
        super(cause);
    }

    protected TillerFactoryException(String message, Throwable cause) {
        super(message, cause);
    }

}
