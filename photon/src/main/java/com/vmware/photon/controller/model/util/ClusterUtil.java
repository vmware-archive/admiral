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

package com.vmware.photon.controller.model.util;

import java.net.URI;
import java.net.URISyntaxException;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.xenon.common.ServiceHost;

/**
 * Utility related to handling cluster information.
 *
 */
public class ClusterUtil {
    /**
     * Metrics URI set as a system property. Eg: -Dphoton-model.metrics.uri=http://localhost/
     */
    public static final String METRICS_URI = System
            .getProperty(UriPaths.PROPERTY_PREFIX + "metrics.uri");

    /**
     * Discovery URI set as a system property. Eg: -Dphoton-model.discovery.uri=http://localhost/
     */
    public static final String DISCOVERY_URI = System
            .getProperty(UriPaths.PROPERTY_PREFIX + "discovery.uri");

    /**
     * Enum mapping Clusters with their URIs.
     *
     */
    public enum ServiceTypeCluster {

        METRIC_SERVICE(METRICS_URI), DISCOVERY_SERVICE(DISCOVERY_URI);

        private String uri;

        ServiceTypeCluster(String uri) {
            this.uri = uri;
        }

        public String getUri() {
            return this.uri;
        }

        void setUri(String uri) {
            this.uri = uri;
        }
    }

    /**
     * Returns the cluster URI, if set. If not set, returns the host URI.
     *
     * @param host
     *            The Service Host.
     * @param cluster
     *            The Cluster, the URI is requested for.
     * @return URI of the cluster or the host.
     */
    public static URI getClusterUri(ServiceHost host, ServiceTypeCluster cluster) {
        // If cluster is null, return the host URI.
        if (cluster == null) {
            return host.getUri();
        }

        String uriString = cluster.getUri();
        if (uriString == null || uriString.isEmpty()) {
            // If the clusterUri is not passed as a parameter, return host URI.
            return host.getUri();
        }

        URI clusterUri = null;
        try {
            clusterUri = new URI(uriString);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e.getLocalizedMessage());
        }
        return clusterUri;
    }
}
