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

package com.vmware.admiral.adapter.docker.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Docker command utility
 */
public class CommandUtil {

    private static enum State {
        DEFAULT,
        DOUBLE,
        SINGLE
    }

    /**
     * Inspects the provided command array and splits entries with unquoted white space into separate items.
     *
     * @param command
     *              A container command array
     * @return An array that doesn't contain entries with unquoted white space
     */
    public static String[] spread(String[] command) {
        if (command == null) {
            return null;
        }
        List<String> result = new ArrayList<String>();
        State state = State.DEFAULT;
        boolean escape = false;
        for (String part : command) {
            StringBuilder sb = new StringBuilder();
            List<String> parts = new ArrayList<String>();
            int len = part.length();
            for (int i = 0; i < len; i++) {
                char c = part.charAt(i);
                if (escape) {
                    escape = false;
                    sb.append(c);
                } else {
                    switch (state) {
                    case SINGLE:
                        switch (c) {
                        case '\\':
                            sb.append(c);
                            escape = true;
                            break;
                        case '\'':
                            // skipping '' in the array string item
                            state = State.DEFAULT;
                            break;
                        default:
                            sb.append(c);
                        }
                        break;
                    case DOUBLE:
                        switch (c) {
                        case '\\':
                            sb.append(c);
                            escape = true;
                            break;
                        case '"':
                            // skipping "" in the array string item
                            state = State.DEFAULT;
                            break;
                        default:
                            sb.append(c);
                        }
                        break;
                    case DEFAULT:
                        switch (c) {
                        case '\\':
                            sb.append(c);
                            state = State.DEFAULT;
                            escape = true;
                            break;
                        case '\'':
                            // skipping '' in the array string item
                            state = State.SINGLE;
                            break;
                        case '"':
                            // skipping "" in the array string item
                            state = State.DOUBLE;
                            break;
                        default:
                            if (!Character.isWhitespace(c)) {
                                sb.append(c);
                            } else if (sb.length() != 0) {
                                parts.add(sb.toString());
                                sb = new StringBuilder();
                            }
                        }
                        break;
                    default:
                        throw new IllegalStateException("Invalid state");
                    }
                }
            }
            parts.add(sb.toString());
            result.addAll(parts);
        }
        return result.toArray(new String[] {});
    }
}
