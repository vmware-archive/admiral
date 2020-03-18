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

package com.vmware.admiral.test.integration.client;

import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from an image search operation
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegistrySearchResponse {
    public int page;

    @XmlAttribute(name = "num_pages")
    @JsonProperty("num_pages")
    public int numPages;

    @XmlAttribute(name = "num_results")
    @JsonProperty("num_results")
    public int numResults;

    @XmlAttribute(name = "page_size")
    @JsonProperty("page_size")
    public int pageSize;

    public String query;

    public List<Result> results;

    @XmlAttribute(name = "is_partial_result")
    @JsonProperty("is_partial_result")
    public boolean isPartialResult;

    public static class Result {
        public String name;
        public String description;
        public String registry;

        @XmlAttribute(name = "is_automated")
        @JsonProperty("is_automated")
        public boolean automated;

        @XmlAttribute(name = "is_trusted")
        @JsonProperty("is_trusted")
        public boolean trusted;

        @XmlAttribute(name = "is_official")
        @JsonProperty("is_official")
        public boolean official;

        @XmlAttribute(name = "star_count")
        @JsonProperty("star_count")
        public int starCount;
    }

}
