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

package com.vmware.admiral.closures.services.closure;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.gson.JsonElement;

import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;

/**
 * Represents an instance/execution of a closure definition.
 *
 */
@JsonFilter(YamlMapper.SERVICE_DOCUMENT_FILTER)
@ServiceDocument.IndexingParameters(serializedStateSize = 112640)
public class Closure extends ResourceState {

    /**
     * Business state of the closure.
     */
    @Documentation(description = "Business state of the closure")
    public TaskStage state = TaskStage.CREATED;

    /**
     * Link to the closure definition.
     */
    @Documentation(description = "Defines the description of the closure.")
    @PropertyOptions(usage = { PropertyUsageOption.LINK, PropertyUsageOption.SINGLE_ASSIGNMENT })
    @UsageOption(option = PropertyUsageOption.SERVICE_USE)
    public String descriptionLink;

    @Documentation(description = "Link to CompositeComponent when a closure is part of "
            + "App/Composition request.")
    @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL, PropertyUsageOption.LINK })
    @UsageOption(option = PropertyUsageOption.SERVICE_USE)
    public String compositeComponentLink;

    /** Callback link and response from the service initiated this task. */
    @Documentation(description = "Callback link and response from the service initiated this task.")
    @UsageOption(option = PropertyUsageOption.SERVICE_USE)
    public ServiceTaskCallback serviceTaskCallback;

    /**
     * Input parameters of the closure.
     * (Optional parameter)
     */
    @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY)
    @UsageOption(option = PropertyUsageOption.OPTIONAL)
    @JsonDeserialize(using = ClosureInputsDeserializer.class)
    public Map<String, JsonElement> inputs;

    /*
     * Output parameters of the task that are populated on task execution.
     */
    @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY)
    @JsonSerialize
    public Map<String, JsonElement> outputs;

    /**
     * Error message in case closure execution completed with error.
     */
    @Documentation(description = "Error message in case closure execution fails.")
    public String errorMsg;

    /** Closure log data */
    @Documentation(description = "Closure log data.")
    @PropertyOptions(indexing = {
            PropertyIndexingOption.STORE_ONLY,
            PropertyIndexingOption.EXCLUDE_FROM_SIGNATURE })
    public byte[] logs;

    /**
     * Hold links to allocated execution resources.
     * (Used internally)
     */
    @Documentation(description = "Links to used resource.")
    public Set<String> resourceLinks;

    /**
     * Time when the closure has been leased for execution.
     * (Used internally)
     */
    @Documentation(description = "Time when the closure has been leased for execution.")
    @UsageOption(option = PropertyUsageOption.SERVICE_USE)
    public Long lastLeasedTimeMillis;

    /**
     * Used to control access to closure execution.
     * (Used internally)
     */
    @Documentation(description = "Used to control access to closure execution.")
    @UsageOption(option = PropertyUsageOption.SERVICE_USE)
    public String closureSemaphore;

    @Override
    public String toString() {
        return Utils.toJson(this);
    }

}
