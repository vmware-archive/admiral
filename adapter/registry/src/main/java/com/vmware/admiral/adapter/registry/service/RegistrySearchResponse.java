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

package com.vmware.admiral.adapter.registry.service;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;

import com.vmware.admiral.common.util.AssertUtil;

/**
 * Response from a registry search query
 */
public class RegistrySearchResponse {
    public int page;

    @SerializedName("num_pages")
    public int numPages;

    @SerializedName("num_results")
    public int numResults;

    @SerializedName("page_size")
    public int pageSize;

    public String query;

    public List<Result> results;

    public static class Result {
        public String name;
        public String description;

        // this is not returned by docker registries, but added by the adapter so the source can be
        // identified in multi-registry search
        public String registry;

        @SerializedName("is_automated")
        public boolean automated;

        @SerializedName("is_trusted")
        public boolean trusted;

        @SerializedName("is_official")
        public boolean official;

        @SerializedName("star_count")
        public int starCount;
    }

    /**
     * Add results from another response into this one
     *
     * @param other
     */
    public void merge(RegistrySearchResponse other) {
        AssertUtil.assertNotNull(other, "other");

        // pagination won't make sense so don't deal with pageSize and numPages

        if (other.results != null) {
            numResults += other.numResults;

            if (results == null) {
                results = new ArrayList<Result>();
            }
            results.addAll(other.results);
        }
    }

    public void limit(int size) {
        size = Math.min(size, results.size());
        results = results.subList(0, size);
        numResults = results.size();
    }
}
