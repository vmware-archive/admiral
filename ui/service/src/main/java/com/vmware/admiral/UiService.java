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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.FileUtils;
import com.vmware.xenon.common.FileUtils.ResourceEntry;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.AuthorizationContext;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.GuestUserService;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class UiService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.UI_SERVICE;
    public static final String HTML_RESOURCE_EXTENSION = ".html";
    public static final String LOGIN_PATH = "login" + HTML_RESOURCE_EXTENSION;
    public static final String INDEX_PATH = "index" + HTML_RESOURCE_EXTENSION;
    public static final String INDEX_EMBEDDED_PATH = "index-embedded" + HTML_RESOURCE_EXTENSION;

    @Override
    public void authorizeRequest(Operation op) {
        // No authorization required. In case the user is not authorized, when retrieving the / we will redirect to login.
        op.complete();
    }

    @Override
    public void handleStart(Operation startPost) {
        try {
            startUiFileContentServices();
            super.handleStart(startPost);
        } catch (Throwable e) {
            startPost.fail(e);
        }
    }

    @Override
    public void handleGet(Operation get) {
        if (redirectToLoginOrIndex(get)) {
            return;
        }

        URI uri = get.getUri();
        String selfLink = getSelfLink();
        String requestUri = uri.getPath();

        if (selfLink.equals(requestUri) && !UriUtils.URI_PATH_CHAR.equals(requestUri)) {
            // no trailing /, redirect to a location with trailing /
            get.setStatusCode(Operation.STATUS_CODE_MOVED_TEMP);
            get.addResponseHeader(Operation.LOCATION_HEADER, selfLink + UriUtils.URI_PATH_CHAR);
            get.complete();
            return;
        } else if (requestUri.equals(UriUtils.URI_PATH_CHAR)) {
            String indexFileName = ConfigurationUtil.isEmbedded()
                    ? INDEX_EMBEDDED_PATH
                    : ServiceUriPaths.UI_RESOURCE_DEFAULT_FILE;
            String uiResourcePath = ManagementUriParts.UI_SERVICE + UriUtils.URI_PATH_CHAR
                    + indexFileName;
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

    // As defined in ServiceHost
    private void startUiFileContentServices() throws Throwable {
        Map<Path, String> pathToURIPath = new HashMap<>();

        Path baseResourcePath = Utils.getServiceUiResourcePath(this);
        try {
            pathToURIPath = discoverUiResources(baseResourcePath, this);
        } catch (Throwable e) {
            log(Level.WARNING, "Error enumerating UI resources for %s: %s", this.getSelfLink(),
                    Utils.toString(e));
        }

        if (pathToURIPath.isEmpty()) {
            log(Level.WARNING, "No custom UI resources found for %s", this.getClass().getName());
            return;
        }

        for (Entry<Path, String> e : pathToURIPath.entrySet()) {
            String value = e.getValue();

            Operation post = Operation
                    .createPost(UriUtils.buildUri(getHost(), value));
            RestrictiveFileContentService fcs = new RestrictiveFileContentService(
                    e.getKey().toFile());
            getHost().startService(post, fcs);
        }
    }

    // Find UI resources for this service (e.g. html, css, js)
    private Map<Path, String> discoverUiResources(Path path, Service s)
            throws Throwable {
        Map<Path, String> pathToURIPath = new HashMap<>();
        Path baseUriPath = Paths.get(ManagementUriParts.UI_SERVICE);

        String prefix = path.toString().replace('\\', '/');

        if (getHost().getState().resourceSandboxFileReference != null) {
            discoverFileResources(s, pathToURIPath, baseUriPath, prefix);
        }

        if (pathToURIPath.isEmpty()) {
            discoverJarResources(path, s, pathToURIPath, baseUriPath, prefix);
        }
        return pathToURIPath;
    }

    private void discoverJarResources(Path path, Service s, Map<Path, String> pathToURIPath,
            Path baseUriPath, String prefix) throws URISyntaxException, IOException {
        for (ResourceEntry entry : FileUtils.findResources(s.getClass(), prefix)) {
            Path resourcePath = path.resolve(entry.suffix);
            Path uriPath = baseUriPath.resolve(entry.suffix);
            Path outputPath = getHost().copyResourceToSandbox(entry.url, resourcePath);
            if (outputPath == null) {
                // Failed to copy one resource, disable user interface for this service.
                s.toggleOption(ServiceOption.HTML_USER_INTERFACE, false);
            } else {
                pathToURIPath.put(outputPath, uriPath.toString().replace('\\', '/'));
            }
        }
    }

    private boolean redirectToLoginOrIndex(Operation op) {
        // in embedded mode we are already authenticated
        // no need to show login or home page upon successful login
        if (ConfigurationUtil.isEmbedded()) {
            return false;
        }

        if (getHost().isAuthorizationEnabled()) {
            AuthorizationContext ctx = op.getAuthorizationContext();
            if (ctx == null) {
                // It should never happen. If no credentials are provided then Xenon falls back
                // on the guest user authorization context and claims.
                op.fail(new IllegalStateException("ctx == null"));
                return true;
            }

            Claims claims = ctx.getClaims();
            if (claims == null) {
                // It should never happen. If no credentials are provided then Xenon falls back
                // on the guest user authorization context and claims.
                op.fail(new IllegalStateException("claims == null"));
                return true;
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

            boolean loginRequired = !isLoginPage && isHTMLResource && !isValidUser;
            boolean showHomePage = isLoginPage && isValidUser;

            if (loginRequired) {
                // Redirect the browser to the login page
                String location = UiService.SELF_LINK + LOGIN_PATH;
                op.addResponseHeader(Operation.LOCATION_HEADER, location);
                op.setStatusCode(Operation.STATUS_CODE_MOVED_TEMP);
                op.complete();

                return true;
            } else if (showHomePage) {
                // Redirect the browser to the home page
                String location = UiService.SELF_LINK + UriUtils.URI_PATH_CHAR;
                op.addResponseHeader(Operation.LOCATION_HEADER, location);
                op.setStatusCode(Operation.STATUS_CODE_MOVED_TEMP);
                op.complete();

                return true;
            }
        }
        return false;
    }

    private void discoverFileResources(Service s, Map<Path, String> pathToURIPath,
            Path baseUriPath,
            String prefix) {
        File rootDir = new File(new File(getHost().getState().resourceSandboxFileReference),
                prefix);
        if (!rootDir.exists()) {
            log(Level.INFO, "Resource directory not found: %s", rootDir.toString());
            return;
        }

        String basePath = baseUriPath.toString();
        String serviceName = s.getClass().getSimpleName();
        List<File> resources = FileUtils.findFiles(rootDir.toPath(),
                new HashSet<String>(), false);
        for (File f : resources) {
            String subPath = f.getAbsolutePath();
            subPath = subPath.substring(subPath.indexOf(serviceName));
            subPath = subPath.replace(serviceName, "");
            Path uriPath = Paths.get(basePath, subPath);
            pathToURIPath.put(f.toPath(), uriPath.toString().replace('\\', '/'));
        }

        if (pathToURIPath.isEmpty()) {
            log(Level.INFO, "No resources found in directory: %s", rootDir.toString());
        }
    }
}
