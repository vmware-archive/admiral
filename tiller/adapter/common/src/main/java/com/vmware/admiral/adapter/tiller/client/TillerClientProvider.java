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

/**
 * A utility that can creates {@link TillerClient} instances. Implementations can be registered by
 * the means of standard java service provider interfaces and then loaded by calls to
 * {@link TillerClientProviderUtil#getTillerClientProvider()}.
 */
public interface TillerClientProvider {

    /**
     * Creates a {@link TillerClient} instance that connects to the Tiller specified in the passed
     * {@link TillerConfig}
     *
     * @param tillerConfig
     *            details for the Tiller instance and K8s cluster where it can be found
     * @return a fully functional {@link TillerClient}
     * @throws TillerClientException
     *             on failure to initialize the client
     */
    public TillerClient createTillerClient(TillerConfig tillerConfig);

}
