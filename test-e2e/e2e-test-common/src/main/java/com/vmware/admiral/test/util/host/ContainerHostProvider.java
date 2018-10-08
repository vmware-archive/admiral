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

package com.vmware.admiral.test.util.host;

public interface ContainerHostProvider {

    public final int INSECURE_PORT = 2375;
    public final int SECURE_PORT = 2376;

    public ContainerHost provide(boolean useServerCertificate, boolean useClientCertificate);

    public void killContainerHost();

}
