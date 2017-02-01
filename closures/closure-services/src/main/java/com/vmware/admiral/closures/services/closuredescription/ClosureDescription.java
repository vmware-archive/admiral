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

package com.vmware.admiral.closures.services.closuredescription;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.gson.JsonElement;

import com.vmware.admiral.closures.services.closure.ClosureInputsDeserializer;
import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.Utils;

/**
 *
 * Closure description represents the code snippet and its environment.
 *
 */
@JsonFilter(YamlMapper.SERVICE_DOCUMENT_FILTER)
@JsonIgnoreProperties(value = { "customProperties" }, ignoreUnknown = true)
public class ClosureDescription extends ResourceState {

    public static final String FIELD_NAME_RUNTIME = "runtime";
    public static final String FIELD_NAME_DESCRIPTION = "description";

    /**
     * Runtime environment used for code execution.
     */
    @Documentation(description = "Runtime environment used for code execution")
    @PropertyOptions(usage = PropertyUsageOption.SINGLE_ASSIGNMENT)
    public String runtime;

    /**
     * Actual source of the code snippet.
     */
    @Documentation(description = "Actual source of the code snippet")
    @PropertyOptions(indexing = PropertyIndexingOption.TEXT, usage = PropertyUsageOption.SINGLE_ASSIGNMENT)
    public String source;

    /**
     * Entry point to start execution from: in the format: <name of the file>.<name of handler function> Optional parameter.
     *
     */
    @Documentation(description = "Entry point to start execution from: in the format: <name of the file>.<name of handler function>")
    @UsageOption(option = PropertyUsageOption.OPTIONAL)
    public String entrypoint;

    /**
     * Description of the closure
     * This is an optional parameter.
     */
    @Documentation(description = "Description of the closure")
    @UsageOption(option = PropertyUsageOption.OPTIONAL)
    public String description;

    /**
     * URL to fetch the source snippet code from. If set will be used with priority against the source field. Optional parameter.
     *
     */
    @Documentation(description = "URL to fetch the source snippet code from. If set will be used with priority against the source field.")
    @UsageOption(option = PropertyUsageOption.OPTIONAL)
    public String sourceURL;

    /**
     * Names of the input parameters.
     *
     */
    @Documentation(description = "Names of the input parameters.")
    @UsageOption(option = PropertyUsageOption.OPTIONAL)
    @JsonDeserialize(using = ClosureInputsDeserializer.class)
    public Map<String, JsonElement> inputs;

    /**
     * Names of the output parameters.
     *
     */
    @Documentation(description = "Names of the output parameters.")
    @UsageOption(option = PropertyUsageOption.OPTIONAL)
    public List<String> outputNames;

    /**
     * Log configuration of execution. Optional parameter.
     *
     */
    @Documentation(description = "Log configuration of execution.")
    @UsageOption(option = PropertyUsageOption.OPTIONAL)
    @JsonDeserialize(using = ClosureLogConfigDeserializer.class)
    public JsonElement logConfiguration;

    /**
     * Resource constraints to use when executing code snippet. Could be CPU, memory or execution timeout. Optional parameter.
     *
     */
    @Documentation(description = "Resource constraints to use when executing code snippet. Could be CPU, memory or execution timeout.")
    @UsageOption(option = PropertyUsageOption.OPTIONAL)
    public ResourceConstraints resources;

    /**
     * Additional dependencies needed for code snippet execution. Optional parameter.
     *
     */
    @Documentation(description = "Additional dependencies needed for code snippet execution. The format depends on "
            + "the runtime.")
    @UsageOption(option = PropertyUsageOption.OPTIONAL)
    public String dependencies;

    /**
     * Link of the placement to use. Optional parameter.
     *
     */
    @Documentation(description = "Link to a placement to use.")
    @UsageOption(option = PropertyUsageOption.OPTIONAL)
    public String placementLink;

    /**
     * Notification URL that can be used as Webhook. Optional parameter.
     */
    @Documentation(description = "Notification URL that can be used as Webhook.")
    @UsageOption(option = PropertyUsageOption.OPTIONAL)
    public String notifyUrl;

    @Override
    public String toString() {
        return Utils.toJson(this);
    }

}
