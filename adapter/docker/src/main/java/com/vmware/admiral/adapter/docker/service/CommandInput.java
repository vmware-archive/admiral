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

package com.vmware.admiral.adapter.docker.service;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Provide input to container adapter commands
 */
public class CommandInput {

    public CommandInput() {
        // default constructor
    }

    /**
     * Copy Constructor
     *
     * @param toCopy
     */
    public CommandInput(CommandInput toCopy) {
        withDockerUri(toCopy.getDockerUri())
                .withCredentials(toCopy.getCredentials())
                .withProperties(toCopy.getProperties());
    }

    /**
     * URI of the docker remote API (e.g.: https://10.20.30.40:2376)
     */
    private URI dockerUri;

    /**
     * Contains the client private key and certificate to authenticat the client to the docker
     * server
     */
    private AuthCredentialsServiceState credentials;

    /**
     * Command specific parameters
     */
    private final Map<String, Object> properties = new HashMap<>();

    /**
     * @return the dockerUri
     */
    public URI getDockerUri() {
        return dockerUri;
    }

    /**
     * @param dockerUri
     *            the dockerUri to set
     * @return
     */
    public CommandInput withDockerUri(URI dockerUri) {
        this.dockerUri = dockerUri;
        return this;
    }

    /**
     * @return the credentials
     */
    public AuthCredentialsServiceState getCredentials() {
        return credentials;
    }

    /**
     * @param credentials
     *            the credentials to set
     * @return
     */
    public CommandInput withCredentials(AuthCredentialsServiceState credentials) {
        this.credentials = credentials;
        return this;
    }

    /**
     * @return the properties
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    public CommandInput withProperty(String key, Object value) {
        this.properties.put(key, value);
        return this;
    }

    public CommandInput withPropertyIfNotNull(String key, Object value) {
        if (value != null) {
            withProperty(key, value);
        }
        return this;
    }

    public CommandInput withProperties(Map<String, Object> properties) {
        this.properties.putAll(properties);
        return this;
    }
}
