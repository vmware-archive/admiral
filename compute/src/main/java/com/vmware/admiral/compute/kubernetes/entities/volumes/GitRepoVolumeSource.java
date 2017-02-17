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

package com.vmware.admiral.compute.kubernetes.entities.volumes;

/**
 * Represents a volume that is populated with the contents of a git repository.
 * Git repo volumes do not support ownership management. Git repo volumes support SELinux relabeling.
 */
public class GitRepoVolumeSource {

    /**
     * Repository URL.
     */
    public String repository;

    /**
     * Commit hash for the specified revision.
     */
    public String revision;

    /**
     * Target directory name. Must not contain or start with ... If . is supplied, the volume
     * directory will be the git repository. Otherwise, if specified, the volume will
     * contain the git repository in the subdirectory with the given name.
     */
    public String directory;
}
