/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model.query;

import java.util.function.Consumer;
import java.util.stream.Collector;

import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.ServiceDocument;

/**
 * Represents a query strategy, such as query-by-pages and query-top-results.
 */
public interface QueryStrategy<T extends ServiceDocument> {

    /**
     * Query for all documents which satisfy passed query.
     *
     * @param documentConsumer
     *            The callback interface of documents consumer.
     */
    DeferredResult<Void> queryDocuments(Consumer<T> documentConsumer);

    /**
     * Query for all document links which satisfy passed query.
     *
     * @param linkConsumer
     *            The callback interface of document links consumer.
     */
    DeferredResult<Void> queryLinks(Consumer<String> linkConsumer);

    /**
     * Performs a mutable reduction operation on the elements of this query using a
     * {@code Collector}. The method is inspired by {@code Stream#collect(Collector)}.
     *
     * @see #queryDocuments(Consumer)
     */
    <R, A> DeferredResult<R> collectDocuments(Collector<T, A, R> collector);

    /**
     * Performs a mutable reduction operation on the elements of this query using a
     * {@code Collector}. The method is inspired by {@code Stream#collect(Collector)}.
     *
     * @see #queryLinks(Consumer)
     */
    <R, A> DeferredResult<R> collectLinks(Collector<String, A, R> collector);

}