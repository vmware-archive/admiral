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

package com.vmware.admiral.adapter.pks;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import com.vmware.admiral.adapter.pks.entities.UAATokenResponse;
import com.vmware.admiral.compute.pks.PKSEndpointService;
import com.vmware.xenon.common.Utils;

public class PKSContext {

    public URI pksUAAUri;
    public URI pksAPIUri;
    public String accessToken;
    public String refreshToken;
    public long expireMillisTime;

    public static PKSContext create(PKSEndpointService.Endpoint endpoint,
            UAATokenResponse uaaTokenResponse) {
        PKSContext pksContext = new PKSContext();
        pksContext.accessToken = uaaTokenResponse.accessToken;
        pksContext.refreshToken = uaaTokenResponse.refreshToken;
        pksContext.pksUAAUri = URI.create(endpoint.uaaEndpoint);
        pksContext.pksAPIUri = URI.create(endpoint.apiEndpoint);
        pksContext.expireMillisTime = calculateExpireTime(uaaTokenResponse.expiresIn);

        return pksContext;
    }

    /**
     * Builds expiration time in milliseconds.
     *
     * @param expiresIn string field from token response containing token validity in seconds.
     *                 If value is not valid number, 0 is used
     * @return expiration time in milliseconds
     */
    private static long calculateExpireTime(String expiresIn) {
        long l = 0;
        try {
            l = Long.parseLong(expiresIn);
        } catch (Exception e) {
            Utils.logWarning("Invalid format for response token expires_in field: %s", expiresIn);
        }
        return System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(l);
    }

}
