/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
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
  'openHosts', 'openHostsNext', 'openAddHost', 'autoConfigureHost',
  'verifyHost', 'addHost', 'createHost', 'editHost', 'updateHost', 'removeHost',
  'operationCompleted', 'disableHost', 'enableHost',
  'acceptCertificateAndAddHost', 'acceptCertificateAndVerifyHost',
  'closeHosts', 'triggerDataCollection'
]);

export var PlacementActions = Reflux.createActions([
  'openPlacements', 'editPlacement', 'cancelEditPlacement', 'createPlacement', 'updatePlacement',
  'deletePlacement'
]);

const containerDefinitionTemplateActions = ['openAddNewContainerDefinition',
  'openEditContainerDefinition', 'resetContainerDefinitionEdit', 'cancelContainerDefinitionEdit',
  'searchImagesForContainerDefinition', 'selectImageForContainerDescription',
  'addContainerDefinition', 'removeContainerDefinition', 'saveContainerDefinition'];

const networkTemplateActions = ['openEditNetwork', 'cancelEditNetwork', 'attachNetwork',
'detachNetwork', 'attachDetachNetwork', 'saveNetwork', 'removeNetwork'];

const closureTemplateActions = ['openAddClosure', 'removeClosure', 'saveClosure', 'runClosure',
'cancelAddClosure', 'createClosureTemplate', 'resetMonitoredClosure'];

const volumeTemplateActions = ['openEditVolume', 'cancelEditVolume', 'saveVolume', 'removeVolume',
'attachVolume', 'detachVolume', 'attachDetachVolume', 'editAttachedVolume'];

const kubernetesTemplateActions = ['openEditKubernetesDefinition', 'cancelEditKubernetesDefinition',
  'saveKubernetesDefinition', 'removeKubernetesDefinition'];

export var TemplateActions = Reflux.createActions([
  'openTemplates', 'openContainerRequest', 'openTemplateDetails',
  'increaseClusterSize', 'decreaseClusterSize',
  'createContainer', 'createContainerWithDetails', 'createContainerTemplate',
  'removeTemplate', 'saveTemplateName', 'copyTemplate', 'publishTemplate', 'openImportTemplate',
  'importTemplate', 'openCreateNewTemplate', 'createNewTemplate'
].concat(containerDefinitionTemplateActions, networkTemplateActions, closureTemplateActions,
  volumeTemplateActions, kubernetesTemplateActions));

export var ContainerActions = Reflux.createActions([
  'openContainers', 'openContainersNext', 'openContainerDetails', 'openClusterDetails',
  'openCompositeContainerDetails', 'openManageContainers', 'openManageComposite',
  'refreshContainer', 'refreshContainerStats',
  'refreshContainerLogs', 'changeLogsSinceDuration',
  'changeLogsTailLines', 'changeLogsFormat', 'changeLogsOption', 'startContainer',
  'stopContainer', 'createTemplateFromContainer',
  'removeContainer', 'startCompositeContainer', 'stopCompositeContainer',
  'removeCompositeContainer', 'startContainerDetails', 'stopContainerDetails',
  'removeContainerDetails', 'removeContainers', 'operationCompleted', 'operationFailed',
  'modifyClusterSize', 'scaleContainer', 'startCluster', 'stopCluster', 'removeCluster',
  'closeContainers', 'openShell', 'closeShell',
  'batchOpContainers', 'batchOpCompositeContainers', 'batchOpNetworks',
  'batchOpVolumes', 'batchOpClosures',
  'openCreateContainer', 'openCreateNetwork', 'createContainer', 'createNetwork',
  'removeClosureRun', 'openCreateClosure', 'saveClosure', 'runClosure', 'openClosureDetails',
  'openCompositeClosureDetails', 'resetMonitoredClosure',
  'rescanContainers', 'rescanApplicationContainers', 'rescanContainer',
  'stopRescanApplicationContainers', 'stopRescanContainer'
]);

export var NetworkActions = Reflux.createActions([
  'removeNetwork', 'networkOperationCompleted', 'networkOperationFailed',
  'openManageNetworks'
]);

export var VolumeActions = Reflux.createActions([
  'openManageVolumes', 'openCreateVolume', 'createVolume', 'removeVolume',
  'volumeOperationCompleted', 'volumeOperationFailed'
]);

export var KubernetesActions = Reflux.createActions([
  'openCreateKubernetesEntities', 'createKubernetesEntities'
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
  'openToolbarPlacementZones', 'openToolbarCredentials', 'openToolbarCertificates',
  'openToolbarDeploymentPolicies', 'closeToolbar',
  'createPlacementZone', 'managePlacementZones',
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
  'openToolbarPlacementZones', 'createPlacementZone', 'managePlacementZones',
  'openToolbarDeploymentPolicies', 'createDeploymentPolicy', 'manageDeploymentPolicies',
  'openToolbarResourceGroups', 'createResourceGroup', 'manageResourceGroups',
  'closeToolbar'
]);

export var TemplatesContextToolbarActions = Reflux.createActions([
  'openToolbarRequests', 'openToolbarEventLogs', 'closeToolbar', 'openToolbarClosureResults'
]);

export var ContainersContextToolbarActions = Reflux.createActions([
  'openToolbarRequests', 'openToolbarEventLogs', 'closeToolbar', 'openToolbarClosureResults'
]);

export var PlacementZonesActions = Reflux.createActions([
  'retrievePlacementZones', 'editPlacementZone', 'cancelEditPlacementZone', 'createPlacementZone',
  'updatePlacementZone', 'deletePlacementZone', 'openPlacementZones'
]);

export var CredentialsActions = Reflux.createActions([
  'retrieveCredentials', 'editCredential', 'cancelEditCredential', 'createCredential',
  'updateCredential', 'deleteCredential'
]);

export var ResourceGroupsActions = Reflux.createActions([
  'retrieveGroups', 'editGroup', 'cancelEditGroup', 'createGroup', 'updateGroup', 'deleteGroup',
  'projectOperationCompleted', 'projectOperationFailed',
  'openCreateOrEditProject'
]);

export var DeploymentPolicyActions = Reflux.createActions([
  'retrieveDeploymentPolicies', 'editDeploymentPolicy', 'cancelEditDeploymentPolicy',
  'createDeploymentPolicy', 'updateDeploymentPolicy', 'deleteDeploymentPolicy'
]);

export var CertificatesActions = Reflux.createActions([
  'retrieveCertificates', 'editCertificate', 'cancelEditCertificate', 'createCertificate',
  'updateCertificate', 'deleteCertificate', 'importCertificate'
]);

export var RequestsActions = Reflux.createActions([
  'openRequests', 'openRequestsNext', 'selectRequests', 'refreshRequests',
  'requestCreated', 'removeRequest', 'closeRequests', 'clearRequests'
]);

export var RequestGraphActions = Reflux.createActions([
  'openRequestGraph', 'refreshRequestGraph'
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
  'openNetworks', 'openContainerDetails', 'openClusterDetails', 'openCompositeContainerDetails',
  'openTemplateDetails', 'showContainersPerPlacement', 'showMachinesPerPlacement', 'openPlacements',
  'openClosures', 'openClosuresSilently', 'openAddClosure', 'openClosureDetails',
  'openCompositeClosureDetails', 'openVolumes', 'openCreateNewContainer',
  'openCreateNewApplication', 'openCreateNewNetwork', 'openCreateNewVolume', 'openCreateNewClosure',
  'openCreateNewProject', 'openCreateNewKubernetes'
]);
