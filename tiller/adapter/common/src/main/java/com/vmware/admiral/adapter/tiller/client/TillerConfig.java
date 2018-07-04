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

package com.vmware.admiral.adapter.tiller.client;

import java.util.Collections;
import java.util.Map;

public class TillerConfig {

    public static enum TillerConnectionType {
        PLAIN_TEXT, TLS, TLS_VERIFY
    }

    private static final TillerConnectionType DEFAULT_TILLER_CONNECTION_TYPE = TillerConnectionType.PLAIN_TEXT;

    private static final String EMPTY_PASSPHRASE = "";

    private String k8sApiUrl;

    private String k8sCertificateAuthority;

    private String k8sClientCertificate;

    private String k8sClientKey;

    private String k8sClientKeyPassphrase = EMPTY_PASSPHRASE;

    private boolean k8sTrustCertificateAuthority;

    private String tillerNamespace;

    private Integer tillerPort;

    private Map<String, String> tillerLabels;

    private String tillerCertificateAuthority;

    private String tillerClientCertificate;

    private String tillerClientKey;

    private String tillerClientKeyPassphrase = null;

    private TillerConnectionType tillerConnectionType = DEFAULT_TILLER_CONNECTION_TYPE;

    private TillerConfig() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getK8sApiUrl() {
        return k8sApiUrl;
    }


    public String getK8sCertificateAuthority() {
        return k8sCertificateAuthority;
    }


    public String getK8sClientCertificate() {
        return k8sClientCertificate;
    }


    public String getK8sClientKey() {
        return k8sClientKey;
    }

    public String getK8sClientKeyPassphrase() {
        return k8sClientKeyPassphrase;
    }

    public boolean getK8sTrustCertificateAuthority() {
        return k8sTrustCertificateAuthority;
    }

    public String getTillerNamespace() {
        return tillerNamespace;
    }


    public Integer getTillerPort() {
        return tillerPort;
    }


    public Map<String, String> getTillerLabels() {
        return tillerLabels;
    }


    public String getTillerCertificateAuthority() {
        return tillerCertificateAuthority;
    }


    public String getTillerClientCertificate() {
        return tillerClientCertificate;
    }


    public String getTillerClientKey() {
        return tillerClientKey;
    }

    public String getTillerClientKeyPassphrase() {
        return tillerClientKeyPassphrase;
    }

    public TillerConnectionType getTillerConnectionType() {
        return tillerConnectionType;
    }

    public static class Builder {

        static final String CONFIG_IS_ALREADY_BUILT_ERROR_MESSAGE = "Cannot modify configuration that has already been built.";

        private TillerConfig configuration;
        private boolean built;

        private Builder() {
            configuration = new TillerConfig();
            built = false;
        }

        public Builder setK8sApiUrl(String k8sApiUrl) {
            assertNotBuilt();
            configuration.k8sApiUrl = k8sApiUrl;
            return this;
        }

        public Builder setK8sCertificateAuthority(String k8sCertificateAuthority) {
            assertNotBuilt();
            configuration.k8sCertificateAuthority = k8sCertificateAuthority;
            return this;
        }

        public Builder setK8sClientCertificate(String k8sClientCertificate) {
            assertNotBuilt();
            configuration.k8sClientCertificate = k8sClientCertificate;
            return this;
        }

        public Builder setK8sClientKey(String k8sClientKey) {
            assertNotBuilt();
            configuration.k8sClientKey = k8sClientKey;
            return this;
        }

        public Builder setK8sClientKeyPassphrase(String k8sClientKeyPassphrase) {
            assertNotBuilt();
            // avoids problems with crappy libraries that expect passphrases to always exist and
            // skip null checks
            configuration.k8sClientKeyPassphrase = k8sClientKeyPassphrase != null
                    ? k8sClientKeyPassphrase : EMPTY_PASSPHRASE;
            return this;
        }

        public Builder setK8sTrustCertificateAuthority(boolean k8sTrustCertificateAuthority) {
            configuration.k8sTrustCertificateAuthority = k8sTrustCertificateAuthority;
            return this;
        }

        public Builder setTillerNamespace(String tillerNamespace) {
            assertNotBuilt();
            configuration.tillerNamespace = tillerNamespace;
            return this;
        }

        public Builder setTillerPort(Integer tillerPort) {
            assertNotBuilt();
            configuration.tillerPort = tillerPort;
            return this;
        }

        public Builder setTillerLabels(Map<String, String> tillerLabels) {
            assertNotBuilt();
            configuration.tillerLabels = tillerLabels == null ? null
                    : Collections.unmodifiableMap(tillerLabels);
            return this;
        }

        public Builder setTillerCertificateAuthority(String tillerCertificateAuthority) {
            assertNotBuilt();
            configuration.tillerCertificateAuthority = tillerCertificateAuthority;
            return this;
        }

        public Builder setTillerClientCertificate(String tillerClientCertificate) {
            assertNotBuilt();
            configuration.tillerClientCertificate = tillerClientCertificate;
            return this;
        }

        public Builder setTillerClientKey(String tillerClientKey) {
            assertNotBuilt();
            configuration.tillerClientKey = tillerClientKey;
            return this;
        }

        public Builder setTillerClientKeyPassphrase(String tillerClientKeyPassphrase) {
            assertNotBuilt();
            // avoids problems with crappy libraries that treat empty passphrases as real-world
            // encryption keys
            configuration.tillerClientKeyPassphrase = tillerClientKeyPassphrase != null
                    && !tillerClientKeyPassphrase.isEmpty() ? tillerClientKeyPassphrase : null;
            return this;
        }

        public Builder setTillerConnectionType(TillerConnectionType tillerConnectionType) {
            configuration.tillerConnectionType = tillerConnectionType == null
                    ? DEFAULT_TILLER_CONNECTION_TYPE : tillerConnectionType;
            return this;
        }

        public TillerConfig build() {
            assertNotBuilt();
            this.built = true;
            return configuration;
        }

        private void assertNotBuilt() {
            if (this.built) {
                throw new IllegalStateException(CONFIG_IS_ALREADY_BUILT_ERROR_MESSAGE);
            }
        }
    }

}
