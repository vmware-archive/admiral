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

package com.vmware.admiral.compute.pks;

import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_ENDPOINT_PROP_NAME;
import static com.vmware.photon.controller.model.query.QueryUtils.DEFAULT_MAX_RESULT_LIMIT;
import static com.vmware.xenon.common.UriUtils.URI_PARAM_ODATA_FILTER;
import static com.vmware.xenon.common.UriUtils.URI_PARAM_ODATA_LIMIT;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.CertificateCleanupUtil;
import com.vmware.admiral.common.util.CertificateUtilExtended;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.TenantLinksUtil;
import com.vmware.admiral.compute.cluster.ClusterService;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class PKSEndpointService extends StatefulService {

    public static class Endpoint extends ResourceState {
        public static final String FIELD_NAME_UAA_ENDPOINT = "uaaEndpoint";
        public static final String FIELD_NAME_API_ENDPOINT = "apiEndpoint";

        @Documentation(description = "UAA endpoint address")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String uaaEndpoint;

        @Documentation(description = "PKS API endpoint address")
        @UsageOption(option = PropertyUsageOption.REQUIRED)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public String apiEndpoint;

        @Documentation(description = "Link to associated authentication credentials")
        public String authCredentialsLink;

        @Documentation(description = "Maps a project to a set of available plans")
        @UsageOption(option = PropertyUsageOption.OPTIONAL)
        @UsageOption(option = PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL)
        public Map<String, PlanSet> planAssignments;

        // this composition is needed to make it possible for jaxb
        // to serialize plan assignments in the cafe code
        public static class PlanSet {
            public Set<String> plans;
        }
    }

    public PKSEndpointService() {
        super(Endpoint.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleCreate(Operation op) {
        if (!checkForBody(op)) {
            return;
        }

        try {
            Endpoint endpoint = op.getBody(Endpoint.class);
            validate(endpoint);
            op.complete();
        } catch (Throwable e) {
            logSevere("Error creating PKS endpoint: %s. Error: %s", e.getMessage(),
                    Utils.toString(e));
            op.fail(e);
        }
    }

    @Override
    public void handlePatch(Operation op) {
        if (!checkForBody(op)) {
            return;
        }

        Endpoint currentState = getState(op);
        Endpoint patchBody = op.getBody(Endpoint.class);

        ServiceDocumentDescription docDesc = getDocumentTemplate().documentDescription;
        String currentSignature = Utils.computeSignature(currentState, docDesc);

        List<String> mergedTenantLinks = mergeTenantLinks(currentState.tenantLinks,
                patchBody.tenantLinks);
        PropertyUtils.mergeServiceDocuments(currentState, patchBody);
        currentState.tenantLinks = mergedTenantLinks;

        validate(currentState);

        String newSignature = Utils.computeSignature(currentState, docDesc);

        // if the signature hasn't change we shouldn't modify the state
        if (currentSignature.equals(newSignature)) {
            currentState = null;
            op.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
        }

        op.setBody(currentState).complete();
    }

    /**
     * Takes all non-project links from the old tenant links and all project links from the patched
     * tenant links, puts them together and returns the result.
     */
    List<String> mergeTenantLinks(List<String> oldTenantLinks, List<String> patchTenantLinks) {
        // no pathed tenant links = keep original tenant links
        if (patchTenantLinks == null) {
            return oldTenantLinks;
        }

        // we are allowed to patch only the project links (i.e. to make group assignments).
        // Clear all other links from the patch
        List<String> projectLinks = patchTenantLinks.stream()
                .filter(link -> {
                    return TenantLinksUtil.isProjectLink(link)
                            || TenantLinksUtil.isGroupLink(link);
                }).collect(Collectors.toList());

        if (oldTenantLinks == null) {
            // no old tenant links = use patch links
            return projectLinks;
        }

        // merge old non-project links with patch project links and return the result
        List<String> otherLinks = oldTenantLinks.stream()
                .filter(link -> {
                    return TenantLinksUtil.isNotProjectLink(link)
                            && TenantLinksUtil.isNotGroupLink(link);
                }).collect(Collectors.toList());

        otherLinks.addAll(projectLinks);
        return otherLinks;
    }

    @Override
    public void handleDelete(Operation delete) {
        Endpoint currentState = getState(delete);
        DeferredResult.completed(null)
                // first delete the clusters for this endpoint
                .thenCompose(ignore -> deleteClustersForEndpoint())
                // then delete all certificates that will be no longer used by any endpoint
                .thenCompose(ignore -> cleanupUnusedEndpointCertificates(currentState))
                .whenComplete((ignore, ex) -> {
                    if (ex != null) {
                        logSevere("Failed to delete endpoint [%s]: %s",
                                getSelfLink(), Utils.toString(ex));
                        delete.fail(ex);
                    } else {
                        delete.complete();
                    }
                });
    }

    private DeferredResult<Void> cleanupUnusedEndpointCertificates(Endpoint removedEndpoint) {
        final String failedMessageFormat = "Failed to cleanup unused certificates when deleting endpoint [%s]: %s";

        if (removedEndpoint == null || removedEndpoint.customProperties == null) {
            logWarning(failedMessageFormat, getSelfLink(), "endpoint or custom properties is null");
            return DeferredResult.completed(null);
        }

        String pksUaaCertLink = removedEndpoint.customProperties
                .get(CertificateUtilExtended.CUSTOM_PROPERTY_PKS_UAA_TRUST_CERT_LINK);
        String pksApiCertLink = removedEndpoint.customProperties
                .get(CertificateUtilExtended.CUSTOM_PROPERTY_PKS_API_TRUST_CERT_LINK);

        if (StringUtils.isEmpty(pksUaaCertLink) && StringUtils.isEmpty(pksApiCertLink)) {
            logWarning(failedMessageFormat, getSelfLink(), "certificate links are not set");
            return DeferredResult.completed(null);
        }

        return CertificateCleanupUtil.removeTrustCertsIfUnused(getHost(),
                Arrays.asList(pksUaaCertLink, pksApiCertLink),
                Collections.singleton(getSelfLink()))
                .exceptionally(ex -> {
                    logWarning(failedMessageFormat, getSelfLink(), Utils.toString(ex));
                    // stop the propagation of the error so the endpoint can be
                    // successfully deleted
                    return null;
                });
    }

    /**
     * Deletes from xenon all clusters for that endpoint. Uses OData query to find the clusters,
     * because that information (the endpoint) is not directly exposed in the cluster DTO.
     */
    private DeferredResult<Void> deleteClustersForEndpoint() {
        String endpointLink = getSelfLink();
        logInfo("deleting clusters for PKS endpoint %s", endpointLink);
        String query = String.format("%s=%s.%s eq '%s'&%s=%d", URI_PARAM_ODATA_FILTER,
                ClusterService.ClusterDto.FIELD_NAME_CUSTOM_PROPERTIES, PKS_ENDPOINT_PROP_NAME,
                endpointLink, URI_PARAM_ODATA_LIMIT, DEFAULT_MAX_RESULT_LIMIT);
        URI uri = UriUtils.buildUri(this.getHost(), ClusterService.SELF_LINK, query);

        Operation getClusters = Operation.createGet(uri).setReferer(getSelfLink());
        return getHost()
                // get all PKS clusters for this Endpoint
                .sendWithDeferredResult(getClusters, ServiceDocumentQueryResult.class)
                .exceptionally(ex -> {
                    logSevere("Error getting PKS clusters for endpoint %s, reason: %s",
                            getSelfLink(), Utils.toString(ex));
                    throw ex instanceof CompletionException
                            ? (CompletionException) ex
                            : new CompletionException(ex);
                })
                // then delete all found clusters. Wait for the deletion operations to complete
                // or fail. Ignore failures
                .thenCompose(foundClusters -> {
                    if (foundClusters == null
                            || foundClusters.documentLinks == null
                            || foundClusters.documentLinks.isEmpty()) {
                        return DeferredResult.completed(null);
                    }

                    // Create and send a delete operation for each found cluster
                    List<DeferredResult<Operation>> deleteOps = foundClusters.documentLinks.stream()
                            .map(clusterLink -> {
                                logInfo("Removing PKS cluster %s for %s", clusterLink, endpointLink);
                                Operation deleteCluser = Operation.createDelete(this, clusterLink)
                                        .setReferer(getSelfLink());
                                return getHost().sendWithDeferredResult(deleteCluser)
                                        // ignore failures
                                        .exceptionally(ex -> {
                                            logWarning(
                                                    "Failed to cleanup cluster [%s] while deleting PKS endpoint [%s]: %s",
                                                    clusterLink, getSelfLink(), Utils.toString(ex));
                                            // Stop propagation of the error so the endpoint can be
                                            // successfully deleted
                                            return null;
                                        });
                            }).collect(Collectors.toList());

                    return DeferredResult.allOf(deleteOps);
                }).thenAccept(ignore -> {
                    // convert to DeferredResult<Void>
                });
    }

    private void validate(Endpoint endpoint) {
        AssertUtil.assertNotNull(endpoint, "endpoint");
        AssertUtil.assertNotNullOrEmpty(endpoint.uaaEndpoint, "UAA endpoint");
        AssertUtil.assertNotNullOrEmpty(endpoint.apiEndpoint, "API endpoint");

        logInfo("Parsing uaa endpoint: %s", endpoint.uaaEndpoint);
        URI uri = URI.create(endpoint.uaaEndpoint);
        validateUri(uri);

        logInfo("Parsing api endpoint: %s", endpoint.apiEndpoint);
        uri = URI.create(endpoint.apiEndpoint);
        validateUri(uri);
    }

    private void validateUri(URI uri) {
        if (!UriUtils.HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme())
                && !UriUtils.HTTP_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            throw new LocalizableValidationException("Unsupported scheme: " + uri.getScheme(),
                    "common.unsupported.scheme", uri.getScheme());
        }
    }

}
