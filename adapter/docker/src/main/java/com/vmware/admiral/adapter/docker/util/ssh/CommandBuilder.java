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

package com.vmware.admiral.adapter.docker.util.ssh;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.vmware.admiral.common.util.ArrayUtils;

/**
 * Helper for building a command with switches and arguments
 */
public class CommandBuilder {

    private String command;
    private final List<String> switches = new ArrayList<>();
    private final List<String> arguments = new ArrayList<>();

    public CommandBuilder withCommand(String command) {
        this.command = command;
        return this;
    }

    /**
     * if the given key(s) is present in the map, create a long switch (--switch) with the same name
     * as the key
     *
     * @param keys
     * @param properties
     * @return
     */
    public CommandBuilder withLongSwitchIfPresent(Map<String, Object> properties, String... keys) {
        withLongSwitchIfPresent(properties, UnaryOperator.identity(), keys);
        return this;
    }

    /**
     * if the given key(s) is present in the map, create a long switch (--switch) with the given
     * transformation to create the switch name from the key
     *
     * @param properties
     * @param switchNameMapper
     * @param keys
     * @return
     */
    public CommandBuilder withLongSwitchIfPresent(Map<String, Object> properties,
            UnaryOperator<String> switchNameMapper, String... keys) {

        for (String key : keys) {
            withLongSwitchIfPresent(properties, key, switchNameMapper.apply(key));
        }
        return this;
    }

    /**
     * if the given key is present in the map, create a long switch (--switch) with the given switch
     * name
     *
     * @param properties
     * @param key
     * @param switchName
     * @return
     */
    public CommandBuilder withLongSwitchIfPresent(Map<String, Object> properties, String key,
            String switchName) {

        Object value = properties.get(key);
        if (value != null) {
            if (value.getClass().isArray()) {
                Object[] valueArray = (Object[]) value;
                for (Object valueArrayElement : valueArray) {
                    withLongSwitch(switchName, valueArrayElement);
                }

            } else {
                withLongSwitch(switchName, value);
            }
        }

        return this;
    }

    /**
     * add a long switch (--switch=value) with the given name and value
     *
     * @param switchName
     * @param value
     * @return
     */
    public CommandBuilder withLongSwitch(String switchName, Object value) {
        withLongSwitch(switchName, value, UnaryOperator.identity());

        return this;
    }

    /**
     * add a long switch (--switch=value) with the given name and value applying the unary operator
     * on the key
     *
     * @param switchName
     * @param value
     * @param switchNameMapper
     * @return
     */
    public CommandBuilder withLongSwitch(String switchName, Object value,
            UnaryOperator<String> switchNameMapper) {
        if (value != null) {
            switches.add(String.format("--%s='%s'", switchNameMapper.apply(switchName),
                    escapeQuotedSwitch(String.valueOf(value))));
        } else {
            switches.add("--" + switchNameMapper.apply(switchName));
        }

        return this;
    }

    /**
     * If the key(s) is mapped add an argument with the mapped value
     *
     * @param properties
     * @param keys
     * @return
     */
    public CommandBuilder withArgumentIfPresent(Map<String, Object> properties, String... keys) {
        for (String key : keys) {
            Object value = properties.get(key);

            if (value != null) {
                if (value.getClass().isArray()) {
                    withArguments(ArrayUtils.toStringArray(value));

                } else {
                    withArguments(value.toString());
                }
            }
        }
        return this;
    }

    /**
     * Add arguments
     *
     * @param arguments
     * @return
     */
    public CommandBuilder withArguments(String... arguments) {
        this.arguments.addAll(Arrays.asList(arguments));
        return this;
    }

    /**
     * Escape quotes in the value that can cause the shell problems (and prevent injection)
     *
     * This doesn't escape spaces as it is assumed that the value will be wrapped in quotes
     *
     * This escaping is bash specific - might not work with other shells
     *
     * The way to escape a single quote inside single quotes in bash is by gluing a double-quoted
     * single quote to the single quoted expression
     *
     * @param value
     * @return
     */
    public static String escapeQuotedSwitch(String value) {
        return value
                .replaceAll("'", "'\"'\"'");
    }

    /**
     * Get the command as a single string
     *
     * @return
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        List<String> fullCommand = new ArrayList<>();
        fullCommand.add(command);
        fullCommand.addAll(switches);
        fullCommand.addAll(arguments);

        sb.append(String.join(" ", fullCommand));

        return sb.toString();
    }
}
