/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute;

import java.net.HttpURLConnection;
import java.util.logging.Logger;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.admiral.service.common.SslTrustImportService;
import com.vmware.admiral.service.common.SslTrustImportService.SslTrustImportRequest;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.Utils;

/**Helper utility class for host configurations and import of SSL trust */
public class EndpointCertificateUtil {
    public static final String REQUEST_PARAM_VALIDATE_OPERATION_NAME = "validate";

    private static final Logger logger = Logger.getLogger(EndpointCertificateUtil.class.getName());

    public static void validateSslTrust(Service sender, HostSpec hostSpec, Operation op,
            Runnable callbackFunction) {

        if (hostSpec.sslTrust != null) {
            callbackFunction.run();
            return;
        }

        if (DeploymentProfileConfig.getInstance().isTest()) {
            logger.warning("No ssl trust validation is performed in test mode...");
            callbackFunction.run();
            return;
        }

        if (!hostSpec.isSecureScheme()) {
            logger.info("Using non secure channel, skipping SSL validation for " + hostSpec.uri);
            callbackFunction.run();
            return;
        }

        SslTrustImportRequest sslTrustRequest = new SslTrustImportRequest();
        sslTrustRequest.hostUri = hostSpec.uri;
        sslTrustRequest.acceptCertificate = hostSpec.acceptCertificate;
        sslTrustRequest.tenantLinks = hostSpec.getHostTenantLinks();

        logger.fine("validateSslTrust: " + sslTrustRequest.hostUri);

        sender.sendRequest(Operation.createPut(sender, SslTrustImportService.SELF_LINK)
                .setBody(sslTrustRequest)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        String message = String.format("Error connecting to %s : %s",
                                hostSpec.uri.toString(), e.getMessage());
                        LocalizableValidationException ex = new LocalizableValidationException(
                                message, "compute.add.host.connection.error",
                                hostSpec.uri.toString(), e.getMessage());
                        ServiceErrorResponse rsp = Utils.toValidationErrorResponse(ex, op);
                        logger.severe(rsp.message);
                        op.setStatusCode(o.getStatusCode());
                        op.fail(e, rsp);
                        return;
                    }

                    // The SSL trust is not trusted self signed and must be accepted by the user
                    // return to the user the certificate for confirmation.
                    if (o.getStatusCode() == Operation.STATUS_CODE_OK) {
                        SslTrustCertificateState body = o.getBody(SslTrustCertificateState.class);
                        // return in origin field the uri for which this certificate is
                        body.origin = hostSpec.uri.toString();
                        op.setBody(body);
                        op.setStatusCode(Operation.STATUS_CODE_OK);
                        op.complete();
                        return;
                    }

                    // certificate is trusted
                    if (o.getStatusCode() == HttpURLConnection.HTTP_ACCEPTED) {
                        hostSpec.sslTrust = o.getBody(SslTrustCertificateState.class);
                        callbackFunction.run();
                        return;
                    }

                    // if location header is present it means a new certificate has been stored
                    String sslTrustLink = o.getResponseHeader(Operation.LOCATION_HEADER);
                    if (hostSpec.sslTrust == null && sslTrustLink != null) {
                        fetchSslTrust(sender, hostSpec, op, sslTrustLink, callbackFunction);
                    } else {
                        callbackFunction.run();
                    }
                }));
    }

    private static void fetchSslTrust(Service sender, HostSpec hostSpec, Operation op,
            String sslTrustLink,
            Runnable callbackFunction) {

        logger.fine("Fetching ssl trust: " + sslTrustLink);
        Operation fetchSslTrust = Operation.createGet(sender, sslTrustLink)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logger.severe(ex.getMessage());
                        failOperation(hostSpec, op, ex);
                        return;
                    }

                    hostSpec.sslTrust = o.getBody(SslTrustCertificateState.class);
                    callbackFunction.run();
                });

        sender.sendRequest(fetchSslTrust);
    }

    private static void failOperation(HostSpec hostSpec, Operation op, Throwable t) {
        String errMsg = String.format("Importing SSL Trust for endpoint %s failed: %s",
                hostSpec.uri, t.getMessage());
        op.fail(new Exception(errMsg, t));
    }

}
