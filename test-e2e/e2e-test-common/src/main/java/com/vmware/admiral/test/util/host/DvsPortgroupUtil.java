/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.util.host;

import java.net.URL;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Logger;

import com.vmware.vim25.DVPortgroupConfigSpec;
import com.vmware.vim25.DuplicateName;
import com.vmware.vim25.DvsFault;
import com.vmware.vim25.InvalidName;
import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VMwareDVSPortSetting;
import com.vmware.vim25.VmwareDistributedVirtualSwitchVlanIdSpec;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.DistributedVirtualPortgroup;
import com.vmware.vim25.mo.DistributedVirtualSwitch;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.util.MorUtil;

public class DvsPortgroupUtil {

    private final Logger LOG = Logger.getLogger(getClass().getName());

    private final ServiceInstance VCENTER;

    public DvsPortgroupUtil(String vcenterIp, String username, String password) {
        Objects.requireNonNull(vcenterIp, "Parameter vcenterIp cannot be null");
        Objects.requireNonNull(username, "Parameter username cannot be null");
        Objects.requireNonNull(password, "Parameter password cannot be null");
        try {
            VCENTER = new ServiceInstance(new URL("https://" + vcenterIp + "/sdk"), username,
                    password, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void createDvsPortgroup(String datacenterName, String dvsName, String portgroupName,
            int portsCount,
            int vlanId) {
        LOG.info("Creating a dvs portgroup with name: " + portgroupName);
        try {
            Datacenter datacenter = findDatacenter(datacenterName);
            DistributedVirtualSwitch dvs = findDvs(datacenter, dvsName);
            doCreateDvsPortgroup(dvs, portgroupName, portsCount, vlanId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteDvsPortgroup(String datacenterName, String portgroupName) {
        LOG.info("Deleting dvs portgroup with name: " + portgroupName);
        try {
            Datacenter datacenter = findDatacenter(datacenterName);
            doDeletePortgroup(datacenter, portgroupName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Datacenter findDatacenter(String datacenterName)
            throws InvalidProperty, RuntimeFault, IllegalArgumentException, RemoteException {
        return (Datacenter) Arrays
                .asList(VCENTER.getRootFolder().getChildEntity())
                .stream()
                .filter(dc -> dc.getMOR().getType().equals("Datacenter")
                        && dc.getName().equals(datacenterName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Could not find datacenter with name: " + datacenterName));
    }

    private DistributedVirtualSwitch findDvs(Datacenter datacenter, String dvsName)
            throws InvalidProperty, RuntimeFault, IllegalArgumentException, RemoteException {
        return (DistributedVirtualSwitch) Arrays
                .asList(datacenter.getNetworkFolder().getChildEntity())
                .stream()
                .filter(d -> d.getMOR().getType().equals("VmwareDistributedVirtualSwitch")
                        && d.getName().equals(dvsName))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Could not find DVS with name: " + dvsName));
    }

    private DistributedVirtualPortgroup doCreateDvsPortgroup(DistributedVirtualSwitch dvs,
            String portgroupName, int portsCount, int vlanId)
            throws DvsFault, DuplicateName, InvalidName, RuntimeFault, RemoteException,
            InterruptedException {
        DVPortgroupConfigSpec[] dvpgs = new DVPortgroupConfigSpec[1];
        dvpgs[0] = new DVPortgroupConfigSpec();
        dvpgs[0].setName(portgroupName);
        dvpgs[0].setNumPorts(128);
        dvpgs[0].setType("earlyBinding");
        VMwareDVSPortSetting vport = new VMwareDVSPortSetting();
        dvpgs[0].setDefaultPortConfig(vport);

        VmwareDistributedVirtualSwitchVlanIdSpec vlan = new VmwareDistributedVirtualSwitchVlanIdSpec();
        vport.setVlan(vlan);
        vlan.setInherited(false);
        vlan.setVlanId(vlanId);
        Task task_pg = dvs.addDVPortgroup_Task(dvpgs);

        TaskInfo taskInfo = waitFor(task_pg);

        if (taskInfo.getState() == TaskInfoState.error) {
            throw new RuntimeException(
                    "Could not create DVS portgroup, error message: "
                            + taskInfo.getError().getLocalizedMessage());
        }
        LOG.info("Successfully created dvs portgroup with name: " + portgroupName);
        ManagedObjectReference pgMor = (ManagedObjectReference) taskInfo.getResult();
        DistributedVirtualPortgroup pg = (DistributedVirtualPortgroup) MorUtil
                .createExactManagedEntity(dvs.getServerConnection(), pgMor);
        return pg;
    }

    public void doDeletePortgroup(Datacenter datacenter, String portgroupName)
            throws InvalidProperty, RuntimeFault, RemoteException, InterruptedException {
        DistributedVirtualPortgroup pg = (DistributedVirtualPortgroup) Arrays
                .asList(datacenter.getNetworkFolder().getChildEntity()).stream()
                .filter(p -> p.getMOR().getType().equals("DistributedVirtualPortgroup")
                        && p.getName().equals(portgroupName))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Could not find dvs portgroup with name: " + portgroupName));
        Task task_pg = pg.destroy_Task();

        TaskInfo taskInfo = waitFor(task_pg);

        if (taskInfo.getState() == TaskInfoState.error) {
            throw new RuntimeException(
                    "Could not delete DVS portgroup, error message: "
                            + taskInfo.getError().getLocalizedMessage());
        }
        LOG.info("Successfully deleted dvs portgroup with name:" + portgroupName);

    }

    private static TaskInfo waitFor(Task task) throws RemoteException, InterruptedException {
        while (true) {
            TaskInfo ti = task.getTaskInfo();
            TaskInfoState state = ti.getState();
            if (state == TaskInfoState.success || state == TaskInfoState.error) {
                return ti;
            }
            Thread.sleep(1000);
        }
    }
}