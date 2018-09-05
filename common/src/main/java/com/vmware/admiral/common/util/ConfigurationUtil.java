/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import static com.vmware.admiral.common.ManagementUriParts.CONFIG_PROPS;

import java.util.function.Consumer;
import java.util.logging.Logger;

import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

// TODO - Remove/refactor this class since it may introduce some inconsistent behavior.
// See comments below.
public class ConfigurationUtil {
    private static final Logger logger = Logger
            .getLogger(ConfigurationUtil.class.getName());

    public static final String UI_PROXY_FORWARD_HEADER = "x-forwarded-for";
    public static final String UI_FRAME_OPTIONS_HEADER = "x-frame-options";
    public static final String CACHE_CONTROL_HEADER = "Cache-Control";

    public static final String EMBEDDED_MODE_PROPERTY = "embedded";
    public static final String VIC_MODE_PROPERTY = "vic";
    public static final String ALLOW_SSH_CONSOLE_PROPERTY = "allow.browser.ssh.console";
    public static final String ALLOW_HOST_EVENTS_SUBSCRIPTIONS = "allow.host.events.subscription";

    public static final String VCH_MIN_VERSION_INCLUSIVE_PROPERTY = "embedded.mode.vch.min.version.inclusive";
    public static final String VCH_MAX_VERSION_EXCLUSIVE_PROPERTY = "embedded.mode.vch.max.version.exclusive";

    // used for IT test in order to simulate this kind of exception
    public static final String THROW_IO_EXCEPTION = "throw.io.exception";

    public static final String URL_CONNECTION_READ_TIMEOUT  = "admiral.adapter.url.connection.read.timeout";

    private static ConfigurationState[] configProperties;

    /**
     * Initializes the cache of configuration property values. See {@link #getProperty(String)}.
     */
    @Deprecated
    public static void initialize(ConfigurationState... cs) {
        configProperties = cs;
    }

    /**
     * Retrieves the property value from a cache loaded with, a priori, the same configuration
     * properties files values.
     *
     * The value of the property will be valid as long as:
     * - It is not updated at runtime (the configuration state will be updated, this cache no).
     * - (in cluster) All the nodes start with the same configuration properties files and their
     * corresponding values.
     */
    @Deprecated
    public static String getProperty(String propertyName) {
        if (configProperties == null || propertyName == null) {
            return null;
        }
        for (ConfigurationState config : configProperties) {
            if (propertyName.equals(config.key)) {
                return config.value;
            }
        }

        return null;
    }

    /**
     * Returns whether Admiral is running on embedded mode or not based on the "embedded"
     * configuration property.
     */
    public static boolean isEmbedded() {
        return Boolean.valueOf(ConfigurationUtil.getProperty(EMBEDDED_MODE_PROPERTY));
    }

    /**
     * Retrieves the property value from the configuration properties service.
     */
    public static void getConfigProperty(Service service, String propName,
            Consumer<String> callback) {
        service.sendRequest(Operation
                .createGet(service, UriUtils.buildUriPath(CONFIG_PROPS, propName))
                .setCompletion((res, ex) -> {
                    if (ex != null) {
                        logger.warning(String.format("Unable to get config property: %s", ex.getMessage()));
                        callback.accept(null);
                        return;
                    }
                    ConfigurationState body = res.getBody(ConfigurationState.class);
                    callback.accept(body.value);
                }));
    }

    /**
     * Retrieves the property value from the configuration properties service.
     */
    public static void getConfigProperty(ServiceHost host, String propName,
            Consumer<String> callback) {
        host.sendRequest(Operation
                .createGet(host, UriUtils.buildUriPath(CONFIG_PROPS, propName))
                .setReferer(host.getUri())
                .setCompletion((res, ex) -> {
                    if (ex != null) {
                        logger.warning(String.format("Unable to get config property: %s", ex.getMessage()));
                        callback.accept(null);
                        return;
                    }
                    ConfigurationState body = res.getBody(ConfigurationState.class);
                    callback.accept(body.value);
                }));
    }
}
