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

package com.vmware.admiral.common;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.logging.LogRecord;

import com.vmware.xenon.common.LogFormatter;
import com.vmware.xenon.common.Utils;

public class AdmiralLogFormatter extends LogFormatter {

    private static final String LOGGING_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    private static final DateTimeFormatter FORMATTER;

    static {
        DateTimeFormatter f = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        try {
            f = DateTimeFormatter.ofPattern(LOGGING_FORMAT);
        } catch (Exception e) {
            Utils.logWarning("Cannot use pattern '%s' for date time formatting, "
                            + "fallback to ISO_LOCAL_DATE_TIME (ISO-8601). "
                            + "Error: %s",
                    LOGGING_FORMAT, e.getMessage());
        }
        FORMATTER = f;
    }

    @Override
    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder();

        LogItem log = LogItem.create(record);
        sb.append('[').append(log.id).append(']');
        sb.append('[').append(log.l.substring(0, 1)).append(']');
        sb.append('[').append(getFormattedDate(log.t)).append(']');
        sb.append('[').append(log.classOrUri).append(']');
        sb.append('[').append(log.method).append(']');
        if (log.m != null && !log.m.isEmpty()) {
            sb.append('[').append(log.m).append(']');
        }
        sb.append('\n');
        return sb.toString();
    }

    /**
     * Formats milliseconds to human readable date time string
     *
     * @param t milliseconds, returned by <code>System.currentTimeMillis()</code>
     * @return formatted date time string
     */
    private String getFormattedDate(long t) {
        long seconds = t / 1000;
        int nanos = (int) (t % 1000) * 1_000_000;

        return FORMATTER.format(LocalDateTime.ofEpochSecond(seconds, nanos, ZoneOffset.UTC));
    }
}