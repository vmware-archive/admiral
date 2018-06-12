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

package com.vmware.photon.controller.model.security.service;

import static com.vmware.photon.controller.model.UriPaths.CONFIG;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.vmware.photon.controller.model.ServiceUtils;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.StatefulService;

/**
 * Trust Store service for storing and managing SSL Trust Certificates. The certificates could be
 * server certificates or Certificate Authorities (CA) in .PEM format.
 * <p>
 * The Trust Certificates are used in the context of setting up SSL connection between client and
 * server. The client certificate keys are handled by AuthCredentialsService.
 */
public class SslTrustCertificateService extends StatefulService {
    public static final String FACTORY_LINK = CONFIG + "/trusted-certificates";

    public static class SslTrustCertificateState extends ServiceDocument {
        // MultiTenantDocument?

        public static final String FIELD_NAME_TENANT_LINKS = "tenantLinks";
        public static final String FIELD_NAME_CERTIFICATE = "certificate";
        public static final String FIELD_NAME_RESOURCE_LINK = "resourceLink";
        public static final String FIELD_NAME_SUBSCRIPTION_LINK = "subscriptionLink";

        /**
         * A list of tenant links which can access this service.
         */
        @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND })
        public List<String> tenantLinks;

        /**
         * (Required) The SSL trust certificate encoded into .PEM format.
         */
        @Documentation(description = "The SSL trust certificate encoded into .PEM format.")
        @PropertyOptions(indexing = { PropertyIndexingOption.STORE_ONLY }, usage = {
                PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String certificate;

        /**
         * (Read Only) The common name of the certificate.
         */
        @Documentation(description = "The common name of the certificate.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String commonName;

        /**
         * (Read Only) The issuer name of the certificate.
         */
        @Documentation(description = "The issuer name of the certificate.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String issuerName;

        /**
         * (Read Only) The serial of the certificate.
         */
        @Documentation(description = "The serial of the certificate.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String serial;

        /**
         * (Read Only) The fingerprint of the certificate in SHA-1 form.
         */
        @Documentation(description = "The fingerprint of the certificate in SHA-1 form. ")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String fingerprint;

        /**
         * (Read Only) The date since the certificate is valid.
         */
        @Documentation(description = "The date since the certificate is valid.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.SINGLE_ASSIGNMENT })
        public long validSince;

        /**
         * (Read Only) The date until the certificate is valid.
         */
        @Documentation(description = "The date until the certificate is valid.")
        @PropertyOptions(usage = { PropertyUsageOption.OPTIONAL,
                PropertyUsageOption.SINGLE_ASSIGNMENT })
        public long validTo;

        /* (Internal) Set by the service when subscription for resource deletion is created */
        @Documentation(description = "Set by the service when subscription for resource deletion is created")
        @PropertyOptions(indexing = { PropertyIndexingOption.STORE_ONLY }, usage = {
                PropertyUsageOption.SERVICE_USE, PropertyUsageOption.LINK,
                PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String subscriptionLink;

        public String getAlias() {
            if (this.documentSelfLink != null) {
                return Service.getId(this.documentSelfLink);
            } else {
                return CertificateUtil.generatePureFingerPrint(
                        CertificateUtil.createCertificateChain(this.certificate));
            }

        }

        public static void populateCertificateProperties(SslTrustCertificateState state,
                X509Certificate cert) {
            state.documentExpirationTimeMicros = TimeUnit.MILLISECONDS
                    .toMicros(cert.getNotAfter().getTime());

            state.commonName = CertificateUtil.getCommonName(cert.getSubjectDN());
            state.issuerName = CertificateUtil.getCommonName(cert.getIssuerDN());
            state.serial = cert.getSerialNumber() == null ? null
                    : cert.getSerialNumber()
                            .toString();
            state.fingerprint = CertificateUtil.computeCertificateThumbprint(cert);
            state.validSince = cert.getNotBefore().getTime();
            state.validTo = cert.getNotAfter().getTime();
        }
    }

    public SslTrustCertificateService() {
        super(SslTrustCertificateState.class);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    public void handleCreate(Operation post) {
        if (!checkForBody(post)) {
            return;
        }

        SslTrustCertificateState state = post.getBody(SslTrustCertificateState.class);
        if (state.documentVersion > 0) {
            post.complete();
            return;
        }

        boolean validated = validate(post, () -> validateStateOnStart(state));
        if (!validated) {
            return;
        }
        post.complete();
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!checkForBody(patch)) {
            return;
        }

        SslTrustCertificateState body = patch.getBody(SslTrustCertificateState.class);
        SslTrustCertificateState state = getState(patch);
        state.certificate = body.certificate != null ? body.certificate : state.certificate;

        boolean validated = validate(patch, () -> validateStateOnStart(state));
        if (!validated) {
            return;
        }

        patch.setBody(state);

        patch.complete();
    }

    @Override
    public void handlePut(Operation put) {
        if (!checkForBody(put)) {
            return;
        }

        SslTrustCertificateState body = put.getBody(SslTrustCertificateState.class);
        SslTrustCertificateState state = getState(put);
        // these properties can't be modified once set:
        body.subscriptionLink = state.subscriptionLink;

        boolean validated = validate(put, () -> validateStateOnStart(body));
        if (!validated) {
            return;
        }

        this.setState(put, body);
        put.setBody(body);
        put.complete();
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        ServiceUtils.setRetentionLimit(template);
        return template;
    }

    private void validateStateOnStart(SslTrustCertificateState state) throws Exception {
        AssertUtil.assertNotEmpty(state.certificate, "'certificate' cannot be empty");
        state.certificate = state.certificate.trim();

        X509Certificate[] certificates = CertificateUtil.createCertificateChain(state.certificate);

        CertificateUtil.validateCertificateChain(certificates);

        // Populate the certificate properties based on the first (end server) certificate
        X509Certificate endCertificate = certificates[0];
        SslTrustCertificateState.populateCertificateProperties(state, endCertificate);

    }

    private boolean isModified(String stateCert, String bodyCert) {
        if (stateCert != null && stateCert.equals(bodyCert)) {
            return false;
        }
        return true;
    }

    private static boolean validate(Operation op, ValidateOperationHandler validateHandler) {
        try {
            validateHandler.validate();
            return true;
        } catch (Exception e) {
            handleValidationException(op, e);
            return false;
        }
    }

    private static void handleValidationException(Operation op, Throwable e) {
        Throwable ex = e;
        if (!(e instanceof IllegalArgumentException)
                && !(e instanceof LocalizableValidationException)) {
            ex = new IllegalArgumentException(e.getMessage());
        }
        op.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
        // with body = null fail(ex) will populate it with the proper ServiceErrorResponse content
        op.setBody(null);
        op.fail(ex);
    }

    @FunctionalInterface
    public interface ValidateOperationHandler {
        void validate() throws Exception;
    }
}
