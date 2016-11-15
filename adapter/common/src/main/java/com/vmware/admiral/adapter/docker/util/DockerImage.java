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

package com.vmware.admiral.adapter.docker.util;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Docker image name parsing utility
 */
public class DockerImage {
    public static final String SECTION_SEPARATOR = "/";
    public static final String TAG_SEPARATOR = ":";
    public static final String DEFAULT_NAMESPACE = "library";
    public static final String DEFAULT_TAG = "latest";
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9_]+");
    private static final List<String> OFFICIAL_REGISTRY_LIST = Arrays.asList(
            "registry.hub.docker.com",
            "docker.io");

    private String host;
    private String namespace;
    private String repository;
    private String tag;

    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @return the namespace
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * @return the repository
     */
    public String getRepository() {
        return repository;
    }

    public String getNamespaceAndRepo() {
        if (namespace != null) {
            return namespace + SECTION_SEPARATOR + repository;
        } else if (isDockerHubImage()) {
            return DEFAULT_NAMESPACE + SECTION_SEPARATOR + repository;
        }

        return repository;
    }

    /**
     * @return the tag
     */
    public String getTag() {
        return tag;
    }

    public boolean isDockerHubImage() {
        return host == null || OFFICIAL_REGISTRY_LIST.contains(host);
    }

    /**
     * parse a full image name (myhost:300/namespace/repo:tag) into its components
     *
     * @param imageName
     * @return
     */
    public static DockerImage fromImageName(String imageName) {
        String[] parts = imageName.split(SECTION_SEPARATOR);
        switch (parts.length) {
        case 1:
            // only one section - it is the repository name with optional tag
            return fromParts(null, null, parts[0]);

        case 2:
            // since there are two sections the second one can be either a host or a namespace
            if (isValidNamespace(parts[0])) {
                return fromParts(null, parts[0], parts[1]);
            } else {
                return fromParts(parts[0], null, parts[1]);
            }

        case 3:
            // all sections present
            return fromParts(parts[0], parts[1], parts[2]);

        default:
            throw new IllegalArgumentException("Invalid image format: " + imageName);
        }
    }

    public static DockerImage fromParts(String hostPart, String namespacePart, String repoAndTagPart) {
        String[] repoParts = repoAndTagPart.split(TAG_SEPARATOR);
        switch (repoParts.length) {
        case 1:
            // no tag
            return fromParts(hostPart, namespacePart, repoParts[0], DEFAULT_TAG);

        case 2:
            // with tag
            return fromParts(hostPart, namespacePart, repoParts[0], repoParts[1]);

        default:
            throw new IllegalArgumentException("Invalid repository and tag format: "
                    + repoAndTagPart);
        }
    }

    public static DockerImage fromParts(String hostPart, String namespacePart, String repo,
            String tag) {

        DockerImage dockerImage = new DockerImage();
        dockerImage.host = hostPart;
        dockerImage.namespace = namespacePart;
        dockerImage.repository = repo;
        dockerImage.tag = tag;

        return dockerImage;
    }

    /**
     * When a image name part can be ambiguously either host or namespace, check which one it is
     * based on a regex of valid characters for the namespace part
     *
     * @param namespaceCandidate
     * @return
     */
    public static boolean isValidNamespace(String namespaceCandidate) {
        return NAMESPACE_PATTERN.matcher(namespaceCandidate).matches();
    }

    /**
     * Convert to a canonical single string representation
     *
     * @return E.g.:<p/>
     *   registry.hub.docker.com/library/alpine -> alpine<p/>
     *   registry.hub.docker.com/mongons/mongo -> mongons/mongo<p/>
     *   registry.local.corp/proj/image -> registry.local.corp/proj/image
     */
    @Override
    public String toString() {
        StringBuilder imageName = new StringBuilder();

        if (!isDockerHubImage()) {
            imageName.append(host);
            imageName.append(SECTION_SEPARATOR);
        }

        // If namespace is null, do not set the default value 'library' as not all
        // V2 registry implementations support this convention
        imageName.append(getNamespaceAndRepo());

        if (tag != null) {
            imageName.append(TAG_SEPARATOR);
            imageName.append(tag);
        }

        return imageName.toString();
    }
}
