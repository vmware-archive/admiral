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

package com.vmware.admiral.service.common;

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.common.util.ServiceUtils.addServiceRequestRoute;

import java.net.HttpURLConnection;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.SslCertificateResolver;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Helper service to import and transform a Server certificate from URI and store it to
 * {@link SslTrustCertificateService} if valid and accepted.
 */
public class SslTrustImportService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.SSL_TRUST_CERTS_IMPORT;

    public static final int THREAD_POOL_QUEUE_SIZE = 10000;

    public static final int THREAD_POOL_KEEPALIVE_SECONDS = 60;

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(0,
            Utils.DEFAULT_THREAD_COUNT,
            THREAD_POOL_KEEPALIVE_SECONDS,
            TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(THREAD_POOL_QUEUE_SIZE),
            new ThreadPoolExecutor.AbortPolicy());

    public static class SslTrustImportRequest {

        /** (Required) The URI of the host where a SSL Trust certificate will be imported. */
        public URI hostUri;

        /**
         * (Optional) Boolean flag indicating whether the certificate should be accepted or will
         * need confirmation from the user. Typically, when self-signed certificates, the service
         * will reject the certificate in order for the user to confirm. Default value is false.
         */
        public boolean acceptCertificate;

        /**
         * (Optional) Tenant links for the certificate
         */
        public List<String> tenantLinks;

    }

    @Override
    public void handleGet(Operation op) {
        Operation.failActionNotSupported(op);
    }

    @Override
    public void handleStop(Operation op) {
        logInfo("Terminating executor");
        if (!executor.isShutdown() && !executor.isTerminated()) {
            executor.shutdown();
        }
        op.complete();
    }

    /**
     * Handles PUT operation. Import certificate from an URI. Any errors during connection,
     * encoding/decoding and validation of the certificate results in 400 BAD REQUEST status
     * response.
     *
     * When a certificate is downloaded and validated successfully, an empty response with
     * status 204 is returned.
     *
     * When a certificate is imported but not validated successfully (ex: self-signed certificates)
     * and <code>acceptCertificate=false</code>, then a {@link SslTrustCertificateState} is returned
     * as part of the body for user confirmation with response status code of 200.
     *
     * When a certificate is imported and not validated successfully but the
     * <code>acceptCertificate</code> flag is set to true, then a {@link SslTrustCertificateState}
     * is stored in {@link SslTrustCertificateService}. The selfDocumentLink of the new stored state
     * {@link SslTrustCertificateState} is returned as part of the <code>Location</code> response
     * header.
     *
     */
    @Override
    public void handlePut(Operation op) {
        assertNotNull(op.getBodyRaw(), "body is required");
        SslTrustImportRequest request = op.getBody(SslTrustImportRequest.class);
        assertNotNull(request.hostUri, "hostUri");

        final OperationContext parentContext = OperationContext.getOperationContext();

        executor.execute(() -> {
            final OperationContext childContext = OperationContext.getOperationContext();
            try {
                // set the operation context of the parent thread in the current thread
                OperationContext.restoreOperationContext(parentContext);

                SslCertificateResolver resolver = SslCertificateResolver.connect(request.hostUri);

                X509Certificate[] certificateChain = resolver.getCertificateChain();
                try {
                    CertificateUtil.validateCertificateChain(certificateChain);
                } catch (Exception e) {
                    op.fail(e);
                    return;
                }

                SslTrustCertificateState sslTrustState = createSslTrustCertificateState(
                        request, certificateChain);
                if (resolver.isCertsTrusted()) {
                    // No need to store the certificate since it is signed by a known CA.
                    op.setStatusCode(HttpURLConnection.HTTP_NO_CONTENT);
                    op.setBody(null);
                    op.complete();
                } else if (request.acceptCertificate) {
                    // store the accepted certificated to be used by the trust store.
                    storeTrustCertificate(sslTrustState, op);
                } else {
                    // self-signed certificate - return to the user for confirmation if not
                    // already accepted
                    String fullSslTrustDocumentSelfLink = UriUtils.buildUriPath(
                            SslTrustCertificateService.FACTORY_LINK,
                            sslTrustState.documentSelfLink);
                    // Check if such trust cert state already exists:
                    new ServiceDocumentQuery<SslTrustCertificateState>(getHost(),
                            SslTrustCertificateState.class)
                            .queryDocument(
                                    fullSslTrustDocumentSelfLink,
                                    (r) -> {
                                        if (r.hasException()) {
                                            logWarning("Exception during ssl trust certificate check: "
                                                    + Utils.toString(r.getException()));
                                            // return the certificate to the user for
                                            // confirmation.
                                            op.setBody(sslTrustState);
                                            op.complete();
                                        } else if (r.hasResult()) {
                                            // Such trust state already exists, return it's
                                            // location
                                            op.addResponseHeader(Operation.LOCATION_HEADER,
                                                    fullSslTrustDocumentSelfLink);
                                            op.setBody(null);
                                            op.setStatusCode(HttpURLConnection.HTTP_NO_CONTENT);
                                            op.complete();
                                        } else {
                                            // return the certificate to the user for
                                            // confirmation.
                                            op.setBody(sslTrustState);
                                            op.complete();
                                        }
                                    });
                }

            } catch (Throwable t) {
                if (!(t instanceof IllegalArgumentException) && !(t instanceof LocalizableValidationException)) {
                    t = new IllegalArgumentException(t);
                }
                op.setStatusCode(Operation.STATUS_CODE_BAD_REQUEST);
                op.fail(t);
            } finally {
                // restore the operation context of the child thread
                OperationContext.restoreOperationContext(childContext);
            }
        });
    }

    private void storeTrustCertificate(SslTrustCertificateState sslTrustState, Operation op) {
        sendRequest(OperationUtil
                .createForcedPost(this, SslTrustCertificateService.FACTORY_LINK)
                .setBody(sslTrustState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Error creating trust certificate state: %s. Error: %s",
                                o.getUri(), Utils.toString(e));
                        op.setStatusCode(o.getStatusCode());
                        op.fail(e);
                        return;
                    }

                    SslTrustCertificateState body = o.getBody(SslTrustCertificateState.class);
                    String selfLink = body.documentSelfLink;
                    if (!body.documentSelfLink.startsWith(SslTrustCertificateService.FACTORY_LINK)) {
                        selfLink = SslTrustCertificateService.FACTORY_LINK + body.documentSelfLink;
                    }
                    op.addResponseHeader(Operation.LOCATION_HEADER, selfLink);
                    op.setBody(null);
                    op.setStatusCode(HttpURLConnection.HTTP_NO_CONTENT);
                    op.complete();
                }));
    }

    private static SslTrustCertificateState createSslTrustCertificateState(
            SslTrustImportRequest request, X509Certificate[] certChain) {
        String sslTrust = CertificateUtil.toPEMformat(certChain);

        SslTrustCertificateState sslTrustState = new SslTrustCertificateState();
        sslTrustState.certificate = sslTrust;
        SslTrustCertificateState.populateCertificateProperties(sslTrustState, certChain[0]);
        sslTrustState.tenantLinks = request.tenantLinks;
        sslTrustState.documentSelfLink = SslTrustCertificateFactoryService
                .generateSelfLink(sslTrustState);

        return sslTrustState;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument d = super.getDocumentTemplate();
        addServiceRequestRoute(d, Action.PUT,
                "Import certificate from an URI. When the certificate is downloaded and "
                        + "validated successfully the response is empty with status code 204. If "
                        + "the certificate is not validated, the response body contains the "
                        + "certificate with status code 200.",
                SslTrustCertificateState.class);
        return d;
    }

}
