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

export var AppActions = Reflux.createActions([
  'init', 'openHome', 'openView', 'openToolbarEventLogs'
]);

export var HostActions = Reflux.createActions([
  'openHosts', 'openHostsNext', 'openAddHost', 'verifyHost', 'addHost',
  'createHost', 'editHost', 'updateHost', 'removeHost',
  'hostRemovalCompleted', 'disableHost', 'enableHost',
  'acceptCertificateAndAddHost', 'acceptCertificateAndVerifyHost',
  'closeHosts', 'triggerDataCollection'
]);

export var MachineActions = Reflux.createActions([
  'openMachines', 'openMachineDetails'
]);

export var ComputeActions = Reflux.createActions([
  'openCompute', 'openComputeDetails'
]);

export var PlacementActions = Reflux.createActions([
  'openPlacements', 'editPlacement', 'cancelEditPlacement', 'createPlacement', 'updatePlacement',
  'deletePlacement'
]);

export var TemplateActions = Reflux.createActions([
  'openTemplates', 'openContainerRequest', 'openTemplateDetails', 'openAddNewContainerDefinition',
  'openEditContainerDefinition', 'resetContainerDefinitionEdit', 'cancelContainerDefinitionEdit',
  'searchImagesForContainerDefinition', 'selectImageForContainerDescription',
  'addContainerDefinition', 'removeContainerDefinition', 'saveContainerDefinition',
  'increaseClusterSize', 'decreaseClusterSize',
  'createContainer', 'createContainerWithDetails', 'createContainerTemplate',
  'removeTemplate', 'saveTemplateName', 'copyTemplate', 'publishTemplate', 'openImportTemplate',
  'importTemplate', 'openEditNetwork', 'cancelEditNetwork', 'attachNetwork', 'detachNetwork',
  'attachDetachNetwork', 'saveNetwork', 'removeNetwork'
]);

export var ContainerActions = Reflux.createActions([
  'openContainers', 'openContainersNext', 'openContainerDetails', 'openClusterDetails',
  'openCompositeContainerDetails', 'refreshContainer', 'refreshContainerStats',
  'refreshContainerLogs', 'changeLogsSinceDuration', 'startContainer', 'stopContainer',
  'removeContainer', 'startCompositeContainer', 'stopCompositeContainer',
  'removeCompositeContainer', 'startContainerDetails', 'stopContainerDetails',
  'removeContainerDetails', 'removeContainers', 'operationCompleted', 'operationFailed',
  'modifyClusterSize', 'scaleContainer', 'startCluster', 'stopCluster', 'removeCluster',
  'closeContainers', 'openShell', 'closeShell',
  'batchOpContainers', 'batchOpCompositeContainers', 'batchOpNetworks',
  'openCreateContainer', 'openCreateNetwork', 'createContainer', 'createNetwork'
]);

export var NetworkActions = Reflux.createActions([
  'removeNetwork', 'networkOperationCompleted', 'networkOperationFailed'
]);

export var EventLogActions = Reflux.createActions([
  'openEventLog', 'openEventLogNext', 'retrieveEventLogNotifications', 'selectEventLog',
  'closeEventLog', 'removeEventLog', 'clearEventLog'
]);

export var RegistryActions = Reflux.createActions([
  'openRegistries', 'editRegistry', 'cancelEditRegistry', 'createRegistry', 'checkInsecureRegistry',
  'acceptCertificateAndCreateRegistry', 'verifyRegistry', 'acceptCertificateAndVerifyRegistry',
  'updateRegistry', 'enableRegistry', 'disableRegistry', 'deleteRegistry'
]);

export var HostContextToolbarActions = Reflux.createActions([
  'openToolbarResourcePools', 'openToolbarCredentials', 'openToolbarCertificates',
  'openToolbarDeploymentPolicies', 'closeToolbar',
  'createResourcePool', 'manageResourcePools',
  'createCredential', 'manageCredentials', 'manageCertificates',
  'createDeploymentPolicy', 'manageDeploymentPolicies'
]);

export var RegistryContextToolbarActions = Reflux.createActions([
  'openToolbarCredentials', 'openToolbarCertificates', 'closeToolbar', 'createCredential',
  'manageCredentials', 'manageCertificates'
]);

export var HostsContextToolbarActions = Reflux.createActions([
  'openToolbarRequests', 'openToolbarEventLogs', 'closeToolbar'
]);

export var PlacementContextToolbarActions = Reflux.createActions([
  'openToolbarResourcePools', 'createResourcePool', 'manageResourcePools',
  'openToolbarDeploymentPolicies', 'createDeploymentPolicy', 'manageDeploymentPolicies',
  'openToolbarResourceGroups', 'createResourceGroup', 'manageResourceGroups',
  'closeToolbar'
]);

export var TemplatesContextToolbarActions = Reflux.createActions([
  'openToolbarRequests', 'openToolbarEventLogs', 'closeToolbar'
]);

export var ContainersContextToolbarActions = Reflux.createActions([
  'openToolbarRequests', 'openToolbarEventLogs', 'closeToolbar'
]);

export var ResourcePoolsContextToolbarActions = Reflux.createActions([
  'openToolbarEndpoints', 'closeToolbar', 'createEndpoint', 'manageEndpoints'
]);

export var ResourcePoolsActions = Reflux.createActions([
  'retrieveResourcePools', 'editResourcePool', 'cancelEditResourcePool', 'createResourcePool',
  'updateResourcePool', 'deleteResourcePool'
]);

export var CredentialsActions = Reflux.createActions([
  'retrieveCredentials', 'editCredential', 'cancelEditCredential', 'createCredential',
  'updateCredential', 'deleteCredential'
]);

export var ResourceGroupsActions = Reflux.createActions([
  'retrieveGroups', 'editGroup', 'cancelEditGroup', 'createGroup', 'updateGroup', 'deleteGroup'
]);

export var DeploymentPolicyActions = Reflux.createActions([
  'retrieveDeploymentPolicies', 'editDeploymentPolicy', 'cancelEditDeploymentPolicy',
  'createDeploymentPolicy', 'updateDeploymentPolicy', 'deleteDeploymentPolicy'
]);

export var CertificatesActions = Reflux.createActions([
  'retrieveCertificates', 'editCertificate', 'cancelEditCertificate', 'createCertificate',
  'updateCertificate', 'deleteCertificate', 'importCertificate'
]);

export var EnvironmentsActions = Reflux.createActions([
  'openEnvironments', 'editEnvironment', 'cancelEditEnvironment', 'createEnvironment',
  'updateEnvironment', 'deleteEnvironment', 'importEnvironment',
  'editEnvironmentProperty', 'cancelEditEnvironmentProperty', 'updateEnvironmentProperties'
]);

export var EndpointsActions = Reflux.createActions([
  'retrieveEndpoints', 'editEndpoint', 'cancelEditEndpoint', 'createEndpoint',
  'updateEndpoint', 'deleteEndpoint'
]);

export var RequestsActions = Reflux.createActions([
  'openRequests', 'openRequestsNext', 'selectRequests', 'refreshRequests',
  'requestCreated', 'removeRequest', 'closeRequests', 'clearRequests'
]);

export var NotificationsActions = Reflux.createActions([
  'retrieveNotifications'
]);

/*
  Used to trigger a navigation change.
  The one listening for these will eventually call the corresponding action on a App/component level
*/
export var NavigationActions = Reflux.createActions([
  'openHome', 'openHomeAddHost', 'openHosts', 'openHostsSilently', 'openAddHost', 'editHost',
  'openTemplates', 'openEventLog', 'openRegistries', 'openContainerRequest', 'openContainers',
  'openContainerDetails', 'openClusterDetails', 'openCompositeContainerDetails',
  'openTemplateDetails', 'showContainersPerPlacement', 'openPlacements', 'openEnvironments',
  'openMachines', 'openMachineDetails', 'openCompute', 'openComputeDetails'
]);
