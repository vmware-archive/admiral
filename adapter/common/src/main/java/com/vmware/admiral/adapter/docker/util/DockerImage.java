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

import java.util.regex.Pattern;

import com.vmware.admiral.common.util.AssertUtil;

/**
 * Docker image name parsing utility
 */
public class DockerImage {
    public static final String SECTION_SEPARATOR = "/";
    public static final String TAG_SEPARATOR = ":";
    public static final String DEFAULT_TAG = "latest";
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9_]+");
    private static final String[] OFFICIAL_REGISTRY_ADDRESS_LIST = {
        "registry.hub.docker.com/library/",
        "registry.hub.docker.com/",
        "docker.io/library/",
        "docker.io/"
    };

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

    /**
     * @return the tag
     */
    public String getTag() {
        return tag;
    }

    /**
     * parse a full image name (myhost:300/namespace/repo:tag) into its components
     *
     * @param imageName
     * @return
     */
    public static DockerImage fromImageName(String imageName) {
        imageName = prepare(imageName);

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
     * If no namespace provided, then the default will be used
     */
    @Override
    public String toString() {
        StringBuilder imageName = new StringBuilder();
        if (host != null) {
            imageName.append(host);
            imageName.append(SECTION_SEPARATOR);
        }

        // If namespace is null, do not set the default value 'library' as not all
        // V2 registry implementations support this convention
        if (namespace != null) {
            imageName.append(namespace);
            imageName.append(SECTION_SEPARATOR);
        }

        imageName.append(repository);

        if (tag != null) {
            imageName.append(TAG_SEPARATOR);
            imageName.append(tag);
        }

        return imageName.toString();
    }

    /**
     * Cut host and default namespace part from image name for official registries.
     * Docker will use its own default registry.
     *
     * @param imageName image name
     * @return E.g.:<p/>
     *   registry.hub.docker.com/library/alpine -> alpine<p/>
     *   registry.hub.docker.com/mongons/mongo -> mongons/mongo<p/>
     *   registry.local.corp/proj/image -> registry.local.corp/proj/image
     */
    private static String prepare(String imageName) {
        AssertUtil.assertNotNull(imageName, "imageName");

        for (String registryPath : OFFICIAL_REGISTRY_ADDRESS_LIST) {
            if (imageName.startsWith(registryPath)) {
                return imageName.substring(registryPath.length());
            }
        }
        return imageName;
    }

}
