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
 * Volume represents a named volume in a pod that may be accessed by any container in the pod.
 */
public class Volume {

    public String name;

    public HostPathVolumeSource hostPath;

    public EmptyDirVolumeSource emptyDir;

    public GCEPersistentDiskVolumeSource gcePersistentDisk;

    public AWSElasticBlockStoreVolumeSource awsElasticBlockStore;

    public GitRepoVolumeSource gitRepo;

    public SecretVolumeSource secret;

    public NFSVolumeSource nfs;

    public ISCSIVolumeSource iscsi;

    public GlusterfsVolumeSource glusterfs;

    public PersistentVolumeClaimVolumeSource persistentVolumeClaim;

    public RBDVolumeSource rbd;

    public FlexVolumeSource flexVolume;

    public CinderVolumeSource cinder;

    public CephFSVolumeSource cephfs;

    public DownwardAPIVolumeSource downwardAPI;

    public FCVolumeSource fc;

    public AzureFileVolumeSource azureFile;

    public ConfigMapVolumeSource configMap;

    public VsphereVirtualDiskVolumeSource vsphereVolume;

    public QuobyteVolumeSource quobyte;

    public AzureDiskVolumeSource azureDisk;

    public PhotonPersistentDiskVolumeSource photonPersistentDisk;
}
