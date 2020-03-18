/*
 * Copyright (c) 2017-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.closure.runtime;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Context object passed to java runtime.
 */
public interface Context {

    /**
     * Returns inputs as json string
     *
     * @return string representation of json object
     */
    public Map<String, Object> getInputs();

    /**
     * Returns outputs as json string
     *
     * @return string representation of json object
     */
    public String getOutputsAsString();

    /**
     * Sets an output parameter
     *
     * @param key
     * @param value
     */
    public void setOutput(String key, Object value);

    /**
     * Execute HTTP requests to admiral itself
     *
     * @param link resouce link
     * @param operation HTTP method
     * @param body body of the HTTP request
     * @param handler function to handle HTTP response
     * @throws Exception thrown in case of error
     */
    public void execute(String link, String operation, String body, Consumer<String> handler) throws
            Exception;

}
