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

package com.vmware.admiral.test.integration.client;

import java.io.Serializable;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.vmware.admiral.test.integration.client.dcp.DcpDocumentKind;

/**
 * EventLogState contains the information about events that resulted from asynchronous operations in
 * back-end.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
@DcpDocumentKind("com:vmware:admiral:log:EventLogService:EventLogState")
public class EventLogState extends TenantedServiceDocument implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum EventLogType {
        /**
         * Events that pass non-critical information to the administrator.
         */
        INFO,
        /**
         * Events that provide implication of potential problems; a warning indicates that the
         * system is not in an ideal state and that some further actions could result in an error.
         */
        WARNING,
        /**
         * Events that indicate problems that are not system-critical and do not require immediate
         * attention.
         */
        ERROR
    }

    /**
     * The operation this event originates from.
     */
    public String resourceType;
    /**
     * Severity level
     */
    public EventLogType eventLogType;
    /**
     * User-friendly description of the event
     */
    public String description;
    /**
     * Additional data like operation request/response body, Request IP, etc.
     */
    public Map<String, String> customProperties;
}
