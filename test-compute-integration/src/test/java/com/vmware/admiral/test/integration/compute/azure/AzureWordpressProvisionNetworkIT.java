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

package com.vmware.admiral.test.integration.compute.azure;

import static org.junit.Assert.fail;

import static com.vmware.admiral.test.integration.compute.azure.AzureComputeProvisionIT.ACCESS_KEY_PROP;
import static com.vmware.admiral.test.integration.compute.azure.AzureComputeProvisionIT.ACCESS_SECRET_PROP;
import static com.vmware.admiral.test.integration.compute.azure.AzureComputeProvisionIT.REGION_ID_PROP;
import static com.vmware.admiral.test.integration.compute.azure.AzureComputeProvisionIT.SUBSCRIPTION_PROP;
import static com.vmware.admiral.test.integration.compute.azure.AzureComputeProvisionIT.TENANT_ID_PROP;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_TENANT_ID;
import static com.vmware.photon.controller.model.resources.SubnetService.SubnetState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

import com.google.common.collect.Sets;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.management.network.AddressSpace;
import com.microsoft.azure.management.network.implementation.SubnetInner;
import com.microsoft.azure.management.network.implementation.VirtualNetworkInner;
import com.microsoft.azure.management.resources.implementation.ResourceGroupInner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.vmware.admiral.compute.profile.StorageProfileService.StorageProfile;
import com.vmware.admiral.test.integration.SimpleHttpsClient;
import com.vmware.admiral.test.integration.compute.BaseWordpressComputeProvisionIT;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureSdkClients;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

@RunWith(Parameterized.class)
public class AzureWordpressProvisionNetworkIT extends BaseWordpressComputeProvisionIT {

    private static final int CIDR_PREFIX = 28;

    private final String templateFilename;

    private static AzureSdkClients azureSdkClients;

    private static ResourceGroupInner resourceGroup;
    private static List<String> rgTDelete = new ArrayList<>();

    private static VirtualNetworkInner virtualNetwork;
    private static VirtualNetworkInner virtualNetworkIsolated;

    private static SubnetInner defaultSubnet;
    private static SubnetInner secondarySubnet;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "WordPress_with_MySQL_compute_network.yaml", null },
                { "WordPress_with_MySQL_compute_public_network.yaml", null },
                { "WordPress_with_MySQL_compute_isolated_network.yaml",
                        (BiConsumer<Set<ServiceDocument>, String>) BaseWordpressComputeProvisionIT
                                ::validateIsolatedNic },
                { "WordPress_with_MySQL_compute_isolated_sg_network.yaml",
                        (BiConsumer<Set<ServiceDocument>, String>) BaseWordpressComputeProvisionIT
                                ::validateIsolatedNic },
                { "WordPress_with_MySQL_compute_isolated_outbound_network.yaml",
                        (BiConsumer<Set<ServiceDocument>, String>) BaseWordpressComputeProvisionIT
                                ::validateOutboundAccess }
        });
    }

    private final BiConsumer<Set<ServiceDocument>, String> validator;

    public AzureWordpressProvisionNetworkIT(String templateFilename,
            BiConsumer<Set<ServiceDocument>, String> validator) {

        this.templateFilename = templateFilename;
        this.validator = validator;
    }

    @Override
    protected void doSetUp() throws Throwable {

        // raise the support public ip address flag on the default subnet
        String defaultSubnetState = getDefaultSubnetStateLink();

        SubnetState patch = new SubnetState();
        patch.supportPublicIpAddress = true;
        patch.documentSelfLink = defaultSubnetState;

        patchDocument(patch);

        createProfile(loadComputeProfile(getEndpointType()),
                createNetworkProfile(secondarySubnet.name(), null, null),
                new StorageProfile());

        createProfile(loadComputeProfile(getEndpointType()),
                createNetworkProfile(
                        defaultSubnet.name(), null,
                        Sets.newHashSet(createTag("location", "dmz"), createTag("type", "public"))),
                new StorageProfile());

        createProfile(loadComputeProfile(getEndpointType()),
                createIsolatedSubnetNetworkProfile(virtualNetworkIsolated.name(),
                        CIDR_PREFIX),
                new StorageProfile());

        createProfile(loadComputeProfile(getEndpointType()), createIsolatedSecurityGroupNetworkProfile(
                defaultSubnet.name(), Sets.newHashSet(createTag("type", "sg"))),
                new StorageProfile());
    }

    @AfterClass
    public static void removeResourceGroup() {
        rgTDelete.forEach(rgName -> {

            try {
                azureSdkClients.getResourceManagementClientImpl().resourceGroups()
                        .beginDelete(resourceGroup.name());
            } catch (Exception e) {
                //retry
                try {
                    azureSdkClients.getResourceManagementClientImpl().resourceGroups()
                            .beginDelete(resourceGroup.name());
                } catch (Exception e1) {
                    System.out.println(String.format(
                            "Could not delete resource group with name %s . Reason: %s . Please, delete it manually",
                            resourceGroup.name(), e1.getMessage()));
                }
            }
        });

    }

    @Override
    protected String getEndpointType() {
        return EndpointType.azure.name();
    }

    @Override
    protected void extendEndpoint(EndpointState endpoint) {
        new AzureComputeProvisionIT().extendEndpoint(endpoint);
    }

    @Override
    protected String getResourceDescriptionLink() throws Exception {
        return importTemplate(templateFilename);
    }

    @Override
    protected void extendComputeDescription(ComputeDescription computeDescription)
            throws Exception {

        if (computeDescription.customProperties == null) {
            computeDescription.customProperties = new HashMap<>();
        }
        String suffix = String.valueOf((int) (new Random().nextDouble() * 1000));
        String vmResourceGroupName = resourceGroup.name() + "-" + suffix;
        computeDescription.customProperties.put(ComputeProperties.RESOURCE_GROUP_NAME,
                vmResourceGroupName);
        rgTDelete.add(vmResourceGroupName);
    }

    @Override
    protected void doWithResources(Set<String> resourceLinks) throws Throwable {

        Set<ServiceDocument> resources = getResources(resourceLinks);
        if (validator != null) {
            validator.accept(resources, virtualNetworkIsolated.name());
        } else {
            super.doWithResources(resourceLinks);
            resources.stream()
                    .filter(c -> c instanceof ComputeState)
                    .forEach(c -> {
                        try {
                            validateComputeNic((ComputeState) c, defaultSubnet.name());
                        } catch (Exception e) {
                            fail();
                        }
                    });
        }

    }

    @BeforeClass
    public static void createVNet() throws Exception {
        AuthCredentialsServiceState c = new AuthCredentialsServiceState();

        c.privateKey = getTestRequiredProp(ACCESS_SECRET_PROP);
        c.privateKeyId = getTestRequiredProp(ACCESS_KEY_PROP);
        c.userLink = getTestRequiredProp(SUBSCRIPTION_PROP);
        c.customProperties = new HashMap<>();
        c.customProperties.put(AZURE_TENANT_ID, getTestRequiredProp(TENANT_ID_PROP));

        azureSdkClients = new AzureSdkClients(Executors.newSingleThreadExecutor(), c);

        String suffix = String.valueOf((int) (new Random().nextDouble() * 1000));
        String resourceGroupName = AzureWordpressProvisionNetworkIT.class.getSimpleName()
                + "-" + suffix;

        createResourceGroup(resourceGroupName);

        String vNetName = "it-test-vnet-" + suffix;
        virtualNetwork = createVirtualNetwork(vNetName);

        String vNetNameIsolated = "it-test-isolated-vnet-" + suffix;
        virtualNetworkIsolated = createVirtualNetwork(vNetNameIsolated);

        defaultSubnet = createSubnet("default", "10.10.10.0/28");
        secondarySubnet = createSubnet("secondary", "10.10.90.0/28");

    }

    private static SubnetInner createSubnet(String name, String cidr)
            throws CloudException, IOException, InterruptedException {
        SubnetInner subnetRequest = new SubnetInner()
                .withAddressPrefix(cidr)
                .withName(name);

        return azureSdkClients
                .getNetworkManagementClientImpl()
                .subnets()
                .createOrUpdate(resourceGroup.name(), virtualNetwork.name(), name, subnetRequest);
    }

    private static VirtualNetworkInner createVirtualNetwork(String vNetName)
            throws CloudException, IOException, InterruptedException {
        VirtualNetworkInner virtualNetworkRequest = new VirtualNetworkInner();
        virtualNetworkRequest.withLocation(getTestProp(REGION_ID_PROP, "westus"));

        AddressSpace addressSpace = new AddressSpace();
        ArrayList<String> prefixes = new ArrayList<>();
        prefixes.add("10.10.0.0/16");
        addressSpace.withAddressPrefixes(prefixes);
        virtualNetworkRequest.withAddressSpace(addressSpace);

        return azureSdkClients
                .getNetworkManagementClientImpl()
                .virtualNetworks()
                .createOrUpdate(resourceGroup.name(), vNetName, virtualNetworkRequest);
    }

    private static void createResourceGroup(String resourceGroupName)
            throws CloudException, IOException {
        ResourceGroupInner resourceGroupToCreate = new ResourceGroupInner()
                .withName(resourceGroupName)
                .withLocation(getTestProp(REGION_ID_PROP, "westus"));

        resourceGroup = azureSdkClients.getResourceManagementClientImpl()
                .resourceGroups()
                .createOrUpdate(resourceGroupName, resourceGroupToCreate);

        rgTDelete.add(resourceGroup.name());
    }

    private String getDefaultSubnetStateLink() throws Exception {
        QueryTask.Query query = QueryTask.Query.Builder.create()
                .addFieldClause(SubnetState.FIELD_NAME_ID, defaultSubnet.id())
                .build();
        QueryTask qt = QueryTask.Builder.createDirectTask().setQuery(query).build();
        String responseJson = sendRequest(SimpleHttpsClient.HttpMethod.POST,
                ServiceUriPaths.CORE_QUERY_TASKS,
                Utils.toJson(qt));
        QueryTask result = Utils.fromJson(responseJson, QueryTask.class);

        return result.results.documentLinks.get(0);
    }
}
