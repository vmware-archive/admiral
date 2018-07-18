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

package com.vmware.admiral.tiller.client;

import com.vmware.admiral.tiller.client.TillerClient;
import com.vmware.admiral.tiller.client.TillerClientProvider;
import com.vmware.admiral.tiller.client.TillerConfig;

public class MockTillerClientProvider implements TillerClientProvider {

    @Override
    public TillerClient createTillerClient(TillerConfig tillerConfig) {
        return new MockTillerClient();
    }

}
