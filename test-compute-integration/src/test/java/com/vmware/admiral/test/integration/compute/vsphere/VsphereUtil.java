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

package com.vmware.admiral.test.integration.compute.vsphere;

/**
 * Common constants and utilities for the vSphere-related tests.
 */
public class VsphereUtil {
    // vSphere-related test property names
    public static final String VC_USERNAME = "test.vsphere.username";
    public static final String VC_PASSWORD = "test.vsphere.password";
    public static final String VC_HOST = "test.vsphere.hostname";
    public static final String VC_RESOURCE_POOL_ID = "test.vsphere.resource.pool.path";
    public static final String VC_DATACENTER_ID = "test.vsphere.datacenter";
    public static final String VC_DATASTORE_ID = "test.vsphere.datastore.path";
    public static final String VC_NETWORK_ID = "test.vsphere.network.id";
    public static final String VC_TARGET_COMPUTE_NAME = "test.vsphere.target.compute.name";
    public static final String VC_TARGET_FOLDER_PATH = "test.vsphere.vm.folder";
    public static final String VC_VM_DISK_URI = "test.vsphere.disk.uri";
    public static final String VC_VM_DISK_URI_TEMPLATE = "test.vsphere.disk.%s.uri";
    public static final String OVF_URI = "test.vsphere.ovf.uri";
}
