/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.service.common;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * Endpoint adapter proxy service to forward requests to 3rd party services.
 */
public class UrlEncodedReverseProxyService extends StatelessService {
    private static final Logger LOGGER = Logger
            .getLogger(UrlEncodedReverseProxyService.class.getName());

    private static final Collection<String> URI_PROTOCOLS = Arrays.asList(
            UriUtils.HTTP_SCHEME,
            UriUtils.HTTPS_SCHEME,
            UriUtils.FILE_SCHEME);
    //Do not perform authorization for static files with these extensions (web page related)
    private static final Set<String> NO_AUTHZ_EXTENSIONS = new HashSet<>(
            Arrays.asList("html", "htm", "css", "js", "png"));

    public static final String SELF_LINK = "/uerp";
    /**
     * key for placeholder to replace in backend URI.
     * <p>
     * example in html: &lt;link rel="stylesheet" href="{host.uri}/styles/main.css"&gt;
     */
    public static final String KEY_HOST_URI = "host.uri";

    public UrlEncodedReverseProxyService() {
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    public void authorizeRequest(Operation op) {
        if (Action.GET == op.getAction()) {
            URI uri = op.getUri();
            String path = uri.getPath();
            if (path != null) {
                int index = path.lastIndexOf('.');
                if (index != -1) {
                    String extension = path.substring(index + 1);
                    if (NO_AUTHZ_EXTENSIONS.contains(extension.toLowerCase())) {
                        op.complete();
                        return;
                    }
                }
            }
        }
        super.authorizeRequest(op);
    }

    @Override
    public void handleGet(Operation get) {
        forwardRequest(get, Operation::createGet);
    }

    @Override
    public void handlePost(Operation post) {
        forwardRequest(post, Operation::createPost);
    }

    @Override
    public void handlePatch(Operation patch) {
        forwardRequest(patch, Operation::createPatch);
    }

    @Override
    public void handlePut(Operation put) {
        forwardRequest(put, Operation::createPut);
    }

    @Override
    public void handleDelete(Operation delete) {
        forwardRequest(delete, Operation::createDelete);
    }

    @Override
    public void handleOptions(Operation options) {
        forwardRequest(options, Operation::createOptions);
    }

    /**
     * Create reverse proxy location for the given {@code backendLocation}.
     * @param backendLocation
     * @return the location in form of https://host1:1234/no-auth/https%3Ahost2%3A4321/step1/step2
     * /step3?p1=v1#frg
     */
    public static String createReverseProxyLocation(String backendLocation) {
        URI uri = URI.create(backendLocation);
        String scheme = uri.getScheme();
        String schemeSpecificPart = uri.getSchemeSpecificPart();
        String authority = uri.getAuthority();
        String path = uri.getPath();
        String query = uri.getQuery();
        String fragment = uri.getFragment();

        StringBuilder partToEncode = new StringBuilder();
        if (scheme != null) {
            partToEncode.append(scheme).append(":");
            if (authority == null && path == null && query == null && fragment == null &&
                    schemeSpecificPart != null) {
                partToEncode.append(schemeSpecificPart);
            }
        }
        if (authority != null) {
            partToEncode.append(authority);
        }
        StringBuilder builder = new StringBuilder();
        if (partToEncode.length() > 0) {
            String encoded;
            try {
                encoded = URLEncoder.encode(partToEncode.toString(), "UTF-8");
            } catch (UnsupportedEncodingException uec) {
                throw new IllegalStateException(uec);
            }
            builder.append(encoded);
        }
        if (path != null) {
            builder.append(path);
        }
        if (query != null) {
            builder.append("?").append(query);
        }
        if (fragment != null) {
            builder.append("#").append(fragment);
        }
        return UriUtils.buildUriPath(SELF_LINK, builder.toString());
    }

    private static String normalizeHost(String uri) {
        for (String protocol : URI_PROTOCOLS) {
            if (uri.startsWith(protocol + ":")) {
                return protocol + "://" + uri.substring(protocol.length() + 1);
            }
        }
        return uri;
    }

    /**
     * extract the backend uri, from the given (frontend) {@code uri}, to use for the reverse proxy.
     * <p>
     * The scheme, host and port part of the backend uri shall be encoded in
     * application/x-www-form-urlencoded format in UTF-8.
     * @param uri
     *         frontend uri to analyze e.g. https://host1:1234/no-auth/https%3Ahost2%3A4321/step1/step2
     *         /step3?p1=v1#frg
     * @return the backend uri to use
     * @see URLEncoder#encode(String, String)
     */
    public static URI extractBackendURI(URI uri, Function<String, URI> hostResolver) {
        String uriPath = uri.getPath();

        int rpIndex = uriPath.indexOf(SELF_LINK);
        if (rpIndex == -1) {
            // no additional data provided!
            return null;
        }
        String opPath = uriPath.substring(rpIndex + SELF_LINK.length());

        if (opPath.startsWith(UriUtils.URI_PATH_CHAR)) {
            opPath = opPath.substring(1);
        } else {
            // no additional data provided!
            return null;
        }
        String hostKey = null;
        opPath = normalizeHost(opPath);
        int crIndex = opPath.indexOf("/{");
        if (crIndex != -1) {
            int end = opPath.indexOf("}", crIndex);
            if (end != -1) {
                hostKey = opPath.substring(crIndex + 2, end);
                LOGGER.fine("hostKey: " + hostKey + " for " + uriPath);
                opPath = opPath.substring(end + 1);
            }
        }
        String path = opPath;
        String query = uri.getRawQuery();
        if (query != null) {
            path = path + "?" + query;
        }
        String fragment = uri.getFragment();
        if (fragment != null) {
            path = path + "#" + fragment;
        }

        URI opURI = URI.create(path);
        if (hostResolver != null && opURI.getAuthority() == null) {
            if (hostKey == null) {
                hostKey = KEY_HOST_URI;
            }
            URI host = hostResolver.apply(hostKey);
            if (host != null) {
                if (!path.startsWith(UriUtils.URI_PATH_CHAR)) {
                    path = UriUtils.URI_PATH_CHAR + path;
                }
                opURI = host.resolve(path);
            } else {
                LOGGER.warning(String.format("Cannot resolve %s for uri %s ",
                        hostKey, uriPath));
            }
        }

        return opURI;
    }

    private void forwardRequest(final Operation op, final Function<URI, Operation> createOp) {

        URI opURI = op.getUri();
        URI backendURI;
        try {
            backendURI = extractBackendURI(op.getUri(), p -> {
                if (KEY_HOST_URI.equals(p)) {
                    return getHost().getUri();
                } else {
                    return null;
                }
            });
        } catch (Exception iae) {
            op.fail(new IllegalArgumentException("Invalid URI: " + opURI.toASCIIString(), iae));
            return;
        }

        sendRequest(createForwardOperation(op, backendURI, createOp));
    }

    /**
     * Convenient method to create forward request for provided {@code operation} to given {@code
     * forwardURI)
     * @param operation
     *         the original operation received the request
     * @param forwardURI
     *         the URI to forward the request
     * @param createOp
     *         the create operation function. see {@link Operation} create methods family
     * @return
     */
    public static Operation createForwardOperation(
            Operation operation,
            URI forwardURI,
            Function<URI, Operation> createOp) {
        return createOp.apply(forwardURI)
                .transferRequestHeadersFrom(operation)
                .setContentType(operation.getContentType())
                .setBody(operation.getBodyRaw())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        operation.fail(e);
                        return;
                    }
                    operation.transferResponseHeadersFrom(o);
                    operation.getResponseHeaders()
                            .put(Operation.CONTENT_TYPE_HEADER, o.getContentType());
                    operation.setBody(o.getBodyRaw());
                    operation.setStatusCode(o.getStatusCode());

                    // handle HTTP 301/302 responses to redirect through the reverse proxy also
                    if (o.getStatusCode() == Operation.STATUS_CODE_MOVED_PERM ||
                            o.getStatusCode() == Operation.STATUS_CODE_MOVED_TEMP) {
                        String location = o.getResponseHeader(Operation.LOCATION_HEADER);
                        operation.getResponseHeaders().put(Operation.LOCATION_HEADER, location);
                    }

                    operation.complete();
                });
    }

}
