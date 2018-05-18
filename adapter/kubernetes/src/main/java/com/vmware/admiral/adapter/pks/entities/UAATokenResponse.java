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

package com.vmware.admiral.adapter.pks.entities;

import com.google.gson.annotations.SerializedName;

public class UAATokenResponse {

    @SerializedName("access_token")
    public String accessToken;

    @SerializedName("token_type")
    public String tokenType;

    @SerializedName("refresh_token")
    public String refreshToken;

    @SerializedName("expires_in")
    public String expiresIn;

    @SerializedName("scope")
    public String scope;

    @SerializedName("jti")
    public String jti;

}
