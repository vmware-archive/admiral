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

package com.vmware.admiral;

import java.net.URI;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.GuestUserService;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.UiContentService;

public class UiService extends UiContentService {
    public static final String SELF_LINK = ManagementUriParts.UI_SERVICE;
    public static final String HTML_RESOURCE_EXTENSION = ".html";
    public static final String LOGIN_PATH = "login" + HTML_RESOURCE_EXTENSION;

    @Override
    public void authorizeRequest(Operation op) {
        if (getHost().isAuthorizationEnabled()) {

            AuthorizationContext ctx = op.getAuthorizationContext();
            if (ctx == null) {
                // It should never happen. If no credentials are provided then Xenon falls back
                // on the guest user authorization context and claims.
                op.fail(new IllegalStateException("ctx == null"));
                return;
            }

            Claims claims = ctx.getClaims();
            if (claims == null) {
                // It should never happen. If no credentials are provided then Xenon falls back
                // on the guest user authorization context and claims.
                op.fail(new IllegalStateException("claims == null"));
                return;
            }

            String path = op.getUri().getPath();

            // Is the user trying to login?
            boolean isLoginPage = path.endsWith(LOGIN_PATH);

            // Is the user trying to access an html page? No need to redirect requests to js,
            // css, etc.
            boolean isHTMLResource = !path.contains(".") ||
                    path.endsWith(UiService.HTML_RESOURCE_EXTENSION);

            // Is the user already authenticated?
            boolean isValidUser = (claims.getSubject() != null)
                    && !GuestUserService.SELF_LINK.equals(claims.getSubject());

            if (!isLoginPage && isHTMLResource && !isValidUser) {
                // Redirect the browser to the login page
                String location = UiService.SELF_LINK + LOGIN_PATH;
                op.addResponseHeader(Operation.LOCATION_HEADER, location);
                op.setStatusCode(Operation.STATUS_CODE_MOVED_TEMP);
            } else if (isLoginPage && isValidUser) {
                // Redirect the browser to the home page
                String location = UiService.SELF_LINK + UriUtils.URI_PATH_CHAR;
                op.addResponseHeader(Operation.LOCATION_HEADER, location);
                op.setStatusCode(Operation.STATUS_CODE_MOVED_TEMP);
            }
        }

        op.complete();
    }

    @Override
    public void handleGet(Operation get) {
        URI uri = get.getUri();
        String selfLink = getSelfLink();
        String requestUri = uri.getPath();
        String uiResourcePath;

        ServiceDocumentDescription desc = getDocumentTemplate().documentDescription;
        if (desc != null && desc.userInterfaceResourcePath != null) {
            uiResourcePath = UriUtils.buildUriPath(ServiceUriPaths.UI_RESOURCES,
                    desc.userInterfaceResourcePath);
        } else {
            uiResourcePath = Utils.buildUiResourceUriPrefixPath(this);
        }

        if (requestUri.startsWith(uiResourcePath)) {
            Exception e = new ServiceNotFoundException(UriUtils.buildUri(uri.getScheme(), uri.getHost(),
                    uri.getPort(), uri.getPath().substring(uiResourcePath.length()), uri.getQuery()).toString());
            ServiceErrorResponse r = Utils.toServiceErrorResponse(e);
            r.statusCode = Operation.STATUS_CODE_NOT_FOUND;
            r.stackTrace = null;

            get.setStatusCode(Operation.STATUS_CODE_NOT_FOUND)
                    .setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON)
                    .fail(e, r);
            return;
        }

        if (selfLink.equals(requestUri) && !UriUtils.URI_PATH_CHAR.equals(requestUri)) {
            // no trailing /, redirect to a location with trailing /
            get.setStatusCode(Operation.STATUS_CODE_MOVED_TEMP);
            get.addResponseHeader(Operation.LOCATION_HEADER, selfLink + UriUtils.URI_PATH_CHAR);
            get.complete();
            return;
        } else {
            String relativeToSelfUri = UriUtils.URI_PATH_CHAR.equals(selfLink) ?
                    requestUri : requestUri.substring(selfLink.length());
            if (relativeToSelfUri.equals(UriUtils.URI_PATH_CHAR)) {
                // serve the index.html
                uiResourcePath += UriUtils.URI_PATH_CHAR + ServiceUriPaths.UI_RESOURCE_DEFAULT_FILE;
            } else {
                // serve whatever resource
                uiResourcePath += relativeToSelfUri;
            }
        }

        // Forward request to the /user-interface service
        Operation operation = get.clone();
        operation.setUri(UriUtils.buildUri(getHost(), uiResourcePath, uri.getQuery()))
                .setCompletion((o, e) -> {
                    get.setBody(o.getBodyRaw())
                            .setStatusCode(o.getStatusCode())
                            .setContentType(o.getContentType());
                    if (e != null) {
                        get.fail(e);
                    } else {
                        get.complete();
                    }
                });

        getHost().sendRequest(operation);
    }
}
