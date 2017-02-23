/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.auth.lightwave.pc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

/**
 * Auth test helper.
 */
public class AuthTestHelper {
    public static final String TENANT = "lotus_tenant";
    public static final String USER = "Administrator@lotus";
    public static final String LOOKUP_SERVICE_URL = "https://1.1.1.1:7444/lookupservice/sdk";
    public static final String PASSWORD = "lotus_password";
    public static RSAPrivateKey privateKey;
    public static RSAPublicKey publicKey;
    public static Date issueTime;
    public static Date expirationTime;

    static {
        KeyPairGenerator keyGenerator;
        try {
            keyGenerator = KeyPairGenerator.getInstance("RSA");
            keyGenerator.initialize(1024, new SecureRandom());
            KeyPair keyPair = keyGenerator.genKeyPair();
            privateKey = (RSAPrivateKey) keyPair.getPrivate();
            publicKey = (RSAPublicKey) keyPair.getPublic();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        long lifetimeSeconds = 300;
        issueTime = new Date();
        expirationTime = new Date(issueTime.getTime() + (lifetimeSeconds * 1000L));
    }

}
