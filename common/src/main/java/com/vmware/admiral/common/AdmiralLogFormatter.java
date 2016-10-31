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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.LogRecord;

import com.vmware.xenon.common.LogFormatter;

public class AdmiralLogFormatter extends LogFormatter {

    private static final String LOGGING_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    @Override
    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder();

        LogItem log = LogItem.create(record);
        sb.append("[").append(log.id).append("]");
        sb.append("[").append(log.l.substring(0, 1)).append("]");
        sb.append("[").append(getLocalizedDate(log.t)).append("]");
        sb.append("[").append(log.classOrUri).append("]");
        sb.append("[").append(log.method).append("]");
        if (log.m != null && !log.m.isEmpty()) {
            sb.append("[").append(log.m).append("]");
        }
        sb.append("\n");
        return sb.toString();
    }

    private Object getLocalizedDate(long t) {
        DateFormat dateTimeFormat = new SimpleDateFormat(LOGGING_FORMAT);
        Date date = new Date(t);
        return dateTimeFormat.format(date);
    }
}