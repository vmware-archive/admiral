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

import java.util.List;

/**
 *Adapts a ConfigMap into a volume.The contents of the target ConfigMap’s Data field will be
 * presented in a volume as files using the keys in the Data field as the file names,
 * unless the items element is populated with specific mappings of keys to paths.
 * ConfigMap volumes support ownership management and SELinux relabeling.
 */
public class ConfigMapVolumeSource {

    /**
     * Name of the referent.
     */
    public String name;

    /**
     * If unspecified, each key-value pair in the Data field of the referenced ConfigMap will be
     * projected into the volume as a file whose name is the key and content is the value.
     * If specified, the listed keys will be projected into the specified paths, and unlisted keys
     * will not be present. If a key is specified which is not present in the ConfigMap, the volume
     * setup will error. Paths must be relative and may not contain the .. path or start with ...
     */
    public List<KeyToPath> items;

    /**
     * Optional: mode bits to use on created files by default. Must be a value between 0 and 0777.
     * Defaults to 0644. Directories within the path are not affected by this setting.
     * This might be in conflict with other options that affect the file mode, like fsGroup,
     * and the result can be other mode bits set.
     */
    public Integer defaultMode;
}
