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

package com.vmware.admiral.common.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

public class CertificateCleanupUtil {

    private static final List<String> TRUST_CERT_CUSTOM_PROPERTY_NAMES = Collections
            .unmodifiableList(
                    Arrays.asList(
                            CertificateUtilExtended.CUSTOM_PROPERTY_TRUST_CERT_LINK,
                            CertificateUtilExtended.CUSTOM_PROPERTY_PKS_UAA_TRUST_CERT_LINK,
                            CertificateUtilExtended.CUSTOM_PROPERTY_PKS_API_TRUST_CERT_LINK));

    /**
     * Finds which of the passed certificates are currently not used by any state and deletes them.
     *
     * @param serviceHost
     *            a host to carry out operations
     * @param trustCertLinks
     *            the links to the certificates that are suspected to be unused
     * @param ignoredUsageLinks
     *            links to documents which usage of the certificate should be ignored
     */
    public static DeferredResult<Void> removeTrustCertsIfUnused(ServiceHost serviceHost,
            Collection<String> trustCertLinks, Collection<String> ignoredUsageLinks) {

        trustCertLinks = sanitizeCollection(trustCertLinks);
        if (trustCertLinks.isEmpty()) {
            return DeferredResult.completed(null);
        }
        ignoredUsageLinks = sanitizeCollection(ignoredUsageLinks);

        // Start with a set of all certificate links
        Set<String> unusedCertLinks = ConcurrentHashMap.newKeySet();
        unusedCertLinks.addAll(trustCertLinks);

        // Build a query that selects all entities that use the specified certs
        Query usagesQuery = buildCertUsageSelectionQuery(trustCertLinks, ignoredUsageLinks);
        // ResourceState is used instead of ServiceDocument so we can
        // check the custom properties as part of the query
        QueryByPages<ResourceState> queryByPages = new QueryByPages<>(serviceHost,
                usagesQuery, ResourceState.class, null);

        return queryByPages
                // remove all used certificates from the deletion list
                .queryDocuments(usage -> {
                    TRUST_CERT_CUSTOM_PROPERTY_NAMES.forEach(propName -> {
                        String propValue = usage.customProperties.get(propName);
                        if (propValue != null && !propName.isEmpty()) {
                            unusedCertLinks.remove(propValue);
                        }
                    });
                })
                // delete the remaining certificates
                .thenCompose(ignore -> deleteCertificatesByLink(serviceHost, unusedCertLinks));
    }

    /**
     * Converts <code>null</code> collections to empty collections, drops <code>null</code> elements
     * in the passed collection, drops duplicated elements.
     */
    private static Set<String> sanitizeCollection(Collection<String> c) {
        if (c == null) {
            return Collections.emptySet();
        }

        return c.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * Builds a query that will select all (not ignored) documents that use any of the specified
     * certificates.
     */
    private static Query buildCertUsageSelectionQuery(Collection<String> trustCertLinks,
            Collection<String> ignoredUsageLinks) {
        Builder queryBuilder = Query.Builder.create();

        addClauseToQuery(queryBuilder, buildCertLinkClause(trustCertLinks));
        addClauseToQuery(queryBuilder, buildSelfLinkExclusionClause(ignoredUsageLinks));

        return queryBuilder.build();
    }

    private static void addClauseToQuery(Builder queryBuilder, Query clause) {
        if (clause != null) {
            queryBuilder.addClause(clause);
        }
    }

    /**
     * Builds a query that will select all documents that use any of the specified certificates.
     */
    private static Query buildCertLinkClause(Collection<String> trustCertLinks) {
        if (trustCertLinks == null || trustCertLinks.isEmpty()) {
            return null;
        }

        Builder builder = Query.Builder.create(Occurance.MUST_OCCUR);

        TRUST_CERT_CUSTOM_PROPERTY_NAMES.forEach(propName -> {
            builder.addInClause(
                    QuerySpecification.buildCompositeFieldName(
                            ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                            propName),
                    trustCertLinks, Occurance.SHOULD_OCCUR);
        });

        return builder.build();
    }

    /**
     * Builds a query that will select all documents that are not present in the ignored list
     */
    private static Query buildSelfLinkExclusionClause(Collection<String> ignoredUsageLinks) {
        if (ignoredUsageLinks == null || ignoredUsageLinks.isEmpty()) {
            return null;
        }

        return Query.Builder.create(Occurance.MUST_NOT_OCCUR)
                .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, ignoredUsageLinks)
                .build();
    }

    /**
     * Deletes the specified certificates.
     */
    private static DeferredResult<Void> deleteCertificatesByLink(ServiceHost serviceHost,
            Collection<String> certLinks) {

        if (certLinks == null || certLinks.isEmpty()) {
            return DeferredResult.completed(null);
        }

        List<DeferredResult<Void>> deleteOps = certLinks.stream()
                .map(certLink -> {
                    Operation delete = Operation.createDelete(serviceHost, certLink)
                            .setReferer("/certificate-cleanup");
                    return serviceHost.sendWithDeferredResult(delete)
                            .thenAccept(ignore -> {
                                serviceHost.log(Level.INFO,
                                        "Deleted unused trust cert [%s]",
                                        certLink);
                            })
                            .exceptionally(ex -> {
                                serviceHost.log(Level.WARNING,
                                        "Failed to delete trust cert [%s]: %s",
                                        certLink, Utils.toString(ex));
                                throw ex instanceof CompletionException
                                        ? (CompletionException) ex
                                        : new CompletionException(ex);
                            });
                }).collect(Collectors.toList());

        return DeferredResult.allOf(deleteOps)
                .thenAccept(ignore -> {
                    // convert return type to DeferredResult<Void>
                });
    }

}
