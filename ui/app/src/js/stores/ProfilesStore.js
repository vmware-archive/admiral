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

import { EndpointsActions, ProfileActions, NavigationActions,
  SubnetworksActions, AzureStorageAccountsActions, VsphereDatastoreActions,
  VsphereStoragePolicyActions} from 'actions/Actions';
import ContextPanelStoreMixin from 'stores/mixins/ContextPanelStoreMixin';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';
import EndpointsStore from 'stores/EndpointsStore';
import SubnetworksStore from 'stores/SubnetworksStore';
import AzureStorageAccountsStore from 'stores/AzureStorageAccountsStore';
import VsphereDatastoreStore from 'stores/VsphereDatastoreStore';
import VsphereStoragePolicyStore from 'stores/VsphereStoragePolicyStore';
import constants from 'core/constants';
import services from 'core/services';
import utils from 'core/utils';

const OPERATION = {
  LIST: 'list'
};

function isContextPanelActive(name) {
  var activeItem = this.data.editingItemData.contextView &&
      this.data.editingItemData.contextView.activeItem;
  return activeItem && activeItem.name === name;
}

function onOpenToolbarItem(name, data, shouldSelectAndComplete) {
  var contextViewData = {
    expanded: true,
    activeItem: {
      name: name,
      data: data
    },
    shouldSelectAndComplete: shouldSelectAndComplete
  };

  this.setInData(['editingItemData', 'contextView'], contextViewData);
  this.emitChange();
}

let ProfilesStore = Reflux.createStore({
  mixins: [ContextPanelStoreMixin, CrudStoreMixin],
  init() {
    EndpointsStore.listen((endpointsData) => {

      let endpoints = (endpointsData.items || []).map((item) =>
        $.extend({
          iconSrc: utils.getAdapter(item.endpointType).iconSrc
        }, item));
      this.setInData(['endpoints'], endpoints);

      if (!this.data.editingItemData) {
        return;
      } else {
        this.setInData(['editingItemData', 'endpoints'], endpoints);
      }

      if (isContextPanelActive.call(this, constants.CONTEXT_PANEL.ENDPOINTS)) {
        this.setInData(['editingItemData', 'contextView', 'activeItem', 'data'], endpointsData);

        let itemToSelect = endpointsData.newItem || endpointsData.updatedItem;
        if (itemToSelect && this.data.editingItemData.contextView.shouldSelectAndComplete) {
          itemToSelect = endpoints.find((item) =>
              item.documentSelfLink === itemToSelect.documentSelfLink);
          clearTimeout(this.itemSelectTimeout);
          this.itemSelectTimeout = setTimeout(() => {
            this.setInData(['editingItemData', 'item', 'endpoint'], itemToSelect);
            this.onCloseToolbar();
          }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
        }
      }

      this.emitChange();
    });

    SubnetworksStore.listen((subnetworksData) => {

      let subnetworks = subnetworksData.items || [];
      this.setInData(['subnetworks'], subnetworks);

      if (!this.data.editingItemData) {
        return;
      } else {
        this.setInData(['editingItemData', 'subnetworks'], subnetworks);
      }

      if (isContextPanelActive.call(this, constants.CONTEXT_PANEL.SUBNETWORKS)) {
        this.setInData(['editingItemData', 'contextView', 'activeItem', 'data'], subnetworksData);

        let itemToSelect = subnetworksData.newItem || subnetworksData.updatedItem;
        if (itemToSelect && this.data.editingItemData.contextView.shouldSelectAndComplete) {
          itemToSelect = subnetworks.find((item) =>
              item.documentSelfLink === itemToSelect.documentSelfLink);
          clearTimeout(this.itemSelectTimeout);
          this.itemSelectTimeout = setTimeout(() => {
            this.setInData(['editingItemData', 'item', 'networkProfile', 'subnetwork'],
                itemToSelect);
            this.onCloseToolbar();
          }, constants.VISUALS.ITEM_HIGHLIGHT_ACTIVE_TIMEOUT);
        }
      }

      this.emitChange();
    });

    AzureStorageAccountsStore.listen((storageAccountsData) => {

      let storageAccounts = storageAccountsData.items || [];
      this.setInData(['storageAccounts'], storageAccounts);
      if (!this.data.editingItemData) {
        return;
      } else {
        this.setInData(['editingItemData', 'storageAccounts'], storageAccounts);
      }

      if (isContextPanelActive.call(this, constants.CONTEXT_PANEL.STORAGE_AZURE)) {
        this.setInData(['editingItemData', 'contextView', 'activeItem', 'data'],
          storageAccountsData);
      }

      this.emitChange();
    });

    VsphereDatastoreStore.listen((datastoresData) => {
      let datastores = datastoresData.items || [];
      this.setInData(['datastores'], datastores);
      if (!this.data.editingItemData) {
        return;
      } else {
        this.setInData(['editingItemData', 'datastores'], datastores);
      }

      if (isContextPanelActive.call(this, constants.CONTEXT_PANEL.VSPHERE_DATASTORES)) {
        this.setInData(['editingItemData', 'contextView', 'activeItem', 'data'],
          datastoresData);
      }

      this.emitChange();
    });

    VsphereStoragePolicyStore.listen((storagePoliciesData) => {
      let storagePolicies = storagePoliciesData.items || [];
      this.setInData(['storagePolicies'], storagePolicies);
      if (!this.data.editingItemData) {
        return;
      } else {
        this.setInData(['editingItemData', 'storagePolicies'], storagePolicies);
      }

      if (isContextPanelActive.call(this, constants.CONTEXT_PANEL.VSPHERE_STORAGE_POLICIES)) {
        this.setInData(['editingItemData', 'contextView', 'activeItem', 'data'],
          storagePoliciesData);
      }

      this.emitChange();
    });

  },

  listenables: [ProfileActions],

  onOpenProfiles(queryOptions) {
    this.setInData(['listView', 'queryOptions'], queryOptions);
    this.setInData(['editingItemData'], null);
    this.setInData(['validationErrors'], null);

    var operation = this.requestCancellableOperation(OPERATION.LIST);
    if (operation) {
      this.setInData(['listView', 'itemsLoading'], true);
      this.emitChange();

      operation.forPromise(services.loadProfiles(queryOptions)).then((result) => {
        let nextPageLink = result.nextPageLink;
        let itemsCount = result.totalCount;

        Promise.all(result.documentLinks.map((documentLink) =>
          services.loadProfile(utils.getDocumentId(documentLink))
        )).then((profiles) => {
          this.setInData(['listView', 'items'], profiles);
          this.setInData(['listView', 'itemsLoading'], false);
          this.setInData(['listView', 'nextPageLink'], nextPageLink);
          if (itemsCount !== undefined && itemsCount !== null) {
            this.setInData(['listView', 'itemsCount'], itemsCount);
          }
          this.emitChange();
        });
      });
    }
  },

  onOpenAddProfile() {
    this.setInData(['editingItemData', 'item'], {});
    this.setInData(['editingItemData', 'endpoints'], this.data.endpoints);
    this.setInData(['editingItemData', 'subnetworks'], this.data.subnetworks);
    this.emitChange();
  },

  onOpenAddInstanceType() {
    this.setInData(['editingItemData', 'item'], {});
    this.setInData(['editingItemData', 'endpoints'], this.data.endpoints);
    this.emitChange();
  },


  onEditProfile(profileId) {
    services.loadProfile(profileId).then((document) => {
      var promises = [];

      if (document.endpointLink) {
        promises.push(
            services.loadEndpoint(document.endpointLink).catch(() => Promise.resolve()));
      } else {
        promises.push(Promise.resolve());
      }

      if (document.tagLinks && document.tagLinks.length) {
        promises.push(
            services.loadTags(document.tagLinks).catch(() => Promise.resolve()));
      } else {
        promises.push(Promise.resolve());
      }

      if (document.computeProfile && document.computeProfile.imageMapping &&
          Object.keys(document.computeProfile.imageMapping).length) {
        let values = Object.values(document.computeProfile.imageMapping);
        promises.push(Promise.all(values.map((value) => {
          if (value.imageLink) {
            return services.loadImage(value.imageLink).then((image) => image.name);
          } else {
            return Promise.resolve(value.image);
          }
        })));
      } else {
        promises.push(Promise.resolve());
      }

      if (document.networkProfile && document.networkProfile.subnetLinks &&
          document.networkProfile.subnetLinks.length) {
        promises.push(
            services.loadSubnetworks(document.endpointLink,
                document.networkProfile.subnetLinks).catch(() => Promise.resolve()));
      } else {
        promises.push(Promise.resolve());
      }

      if (document.networkProfile && document.networkProfile.isolationNetworkLink) {
        promises.push(
            services.loadNetwork(
                document.networkProfile.isolationNetworkLink).catch(() => Promise.resolve()));
      } else {
        promises.push(Promise.resolve());
      }

      if (document.networkProfile && document.networkProfile.isolationExternalSubnetLink) {
        promises.push(
            services.loadSubnet(
                document.networkProfile.isolationExternalSubnetLink).catch(() =>
                    Promise.resolve()));
      } else {
        promises.push(Promise.resolve());
      }

      Promise.all(promises).then(([endpoint, tags, images, subnetworks, isolationNetwork,
          isolationExternalSubnet]) => {
        if (document.endpointLink && endpoint) {
          document.endpoint = endpoint;
        }
        document.tags = tags ? Object.values(tags) : [];
        document.computeProfile.images = images || [];
        if (document.networkProfile.isolationNetworkLink && isolationNetwork) {
          document.networkProfile.isolationNetwork = isolationNetwork;
        }
        if (document.networkProfile.isolationExternalSubnetLink && isolationExternalSubnet) {
          document.networkProfile.isolationExternalSubnet = isolationExternalSubnet;
        }

        new Promise((resolve, reject) => {
          if (subnetworks) {
            let values = Object.values(subnetworks);
            let networkLinks = [...new Set(values.map((subnetwork) =>
                subnetwork.networkLink))];
            services.loadNetworks(document.endpointLink, networkLinks).then((networks) => {
              values.forEach((subnetwork) => {
                subnetwork.networkName = networks[subnetwork.networkLink].name;
              });
              resolve(values);
            }, reject);
          } else {
            resolve([]);
          }
        }).then((subnetworks) => {
          document.networkProfile.subnetworks = subnetworks;

          this.setInData(['editingItemData', 'item'], Immutable(document));
          this.setInData(['editingItemData', 'endpoints'], this.data.endpoints);
          this.emitChange();
        });
      });

    }).catch(this.onGenericEditError);

    this.emitChange();
  },

  onEditInstanceType(instanceTypeId) {
    services.loadInstanceType(instanceTypeId).then((document) => {
      this.setInData(['editingItemData', 'item'], Immutable(document));
      this.setInData(['editingItemData', 'endpoints'], this.data.endpoints);
      this.emitChange();
    }).catch(this.onGenericEditError);

    this.emitChange();
  },

  onCancelEditProfile() {
    this.setInData(['editingItemData'], null);
    this.emitChange();
  },

  onClearProfile() {
    this.setInData(['editingItemData', 'item'], {});
    this.emitChange();
  },

  onCreateProfile(model, tagRequest) {
    Promise.all(services.updateStorageTags(model.storageProfile.storageItems || []))
      .then((storageItemTagAssignmentResponses) => {
        let storageItems = [];
        for (let i = 0; i < storageItemTagAssignmentResponses.length; i++) {
          let storageItem = model.storageProfile.storageItems[i];
          storageItem.tagLinks = storageItemTagAssignmentResponses[i].tagLinks;
          storageItems.push(storageItem);
        }
        this.setInData(['editingItemData', 'item',
          'storageProfile', 'storageItems'], storageItems);
        return services.loadImageResources(model.endpointLink,
            Object.values(model.computeProfile.imageMapping).map((value) => value.image));
      }).then((images) => {
        let imageMapping = Object.keys(model.computeProfile.imageMapping).reduce((prev, curr) => {
          let name = model.computeProfile.imageMapping[curr].image;
          let image = images.find((image) => image.name === name);
          if (image) {
            prev[curr] = {
              imageLink: image.documentSelfLink
            };
          } else {
            prev[curr] = {
              image: name
            };
          }
          return prev;
        }, {});
        Promise.all([
          services.createComputeProfile($.extend({}, model.computeProfile, {
            imageMapping
          })),
          services.createNetworkProfile(model.networkProfile),
          services.createStorageProfile(model.storageProfile)
        ]).then(([computeProfile, networkProfile, storageProfile]) => {
          let data = $.extend(model, {
            computeProfileLink: computeProfile.documentSelfLink,
            networkProfileLink: networkProfile.documentSelfLink,
            storageProfileLink: storageProfile.documentSelfLink
          });
          return services.createProfile(data);
        }).then((createdProfile) => {
          if (tagRequest) {
            tagRequest.resourceLink = createdProfile.documentSelfLink;
            return services.updateTagAssignment(tagRequest);
          }
          return Promise.resolve();
        }).then(() => {
          NavigationActions.openProfiles();
          this.setInData(['editingItemData'], null);
          this.emitChange();
        }).catch(this.onGenericEditError);
      }).catch(this.onGenericEditError);
    this.setInData(['editingItemData', 'item'], model);
    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();
  },

  onCreateInstanceType(model, tagRequest) {
    this.onPersistInstanceType(model, tagRequest, services.createInstanceType);
  },

  onUpdateInstanceType(model, tagRequest) {
    this.onPersistInstanceType(model, tagRequest, services.updateInstanceType);
  },

  onPersistInstanceType(model, tagRequest, persistFunction) {
    persistFunction(model).then((profile) => {
      if (tagRequest) {
        tagRequest.resourceLink = profile.documentSelfLink;
        return services.updateTagAssignment(tagRequest);
      }
      return Promise.resolve();
    }).then(() => {
      NavigationActions.openInstanceTypes();
      // update the model after a slight timeout so the view can change context
      setTimeout(() => {
        this.setInData(['editingItemData', 'item'], {});
        this.setInData(['editingItemData', 'saving'], false);
        this.emitChange();
      }, 500);
    }).catch(this.onGenericEditError);

    this.setInData(['editingItemData', 'item'], model);
    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();
  },

  onUpdateProfile(model, tagRequest) {
    Promise.all(services.updateStorageTags(model.storageProfile.storageItems || []))
      .then((storageItemTagAssignmentResponses) => {
        let storageItems = [];
        for (let i = 0; i < storageItemTagAssignmentResponses.length; i++) {
          let storageItem = model.storageProfile.storageItems[i];
          storageItem.tagLinks = storageItemTagAssignmentResponses[i].tagLinks;
          storageItems.push(storageItem);
        }
        this.setInData(['editingItemData', 'item',
          'storageProfile', 'storageItems'], storageItems);
        return services.loadImageResources(model.endpointLink,
            Object.values(model.computeProfile.imageMapping).map((value) => value.image));
      }).then((images) => {
        let imageMapping = Object.keys(model.computeProfile.imageMapping).reduce((prev, curr) => {
          let name = model.computeProfile.imageMapping[curr].image;
          let image = images.find((image) => image.name === name);
          if (image) {
            prev[curr] = {
              imageLink: image.documentSelfLink
            };
          } else {
            prev[curr] = {
              image: name
            };
          }
          return prev;
        }, {});
        Promise.all([
          services.updateComputeProfile($.extend({}, model.computeProfile, {
            imageMapping
          })),
          services.updateNetworkProfile(model.networkProfile),
          services.updateStorageProfile(model.storageProfile),
          services.updateProfile(model),
          services.updateTagAssignment(tagRequest)
        ]).then(() => {
          NavigationActions.openProfiles();
          this.setInData(['editingItemData'], null);
          this.emitChange();
        }).catch(this.onGenericEditError);
      }).catch(this.onGenericEditError);

    this.setInData(['editingItemData', 'item'], model);
    this.setInData(['editingItemData', 'validationErrors'], null);
    this.setInData(['editingItemData', 'saving'], true);
    this.emitChange();
  },

  onDeleteProfile(profile) {
    services.deleteProfile(profile).then(() => {
      var profiles = this.data.listView.items.asMutable().filter((item) =>
          item.documentSelfLink !== profile.documentSelfLink);

      this.setInData(['listView', 'items'], Immutable(profiles));
      this.setInData(['listView', 'itemsCount'], this.data.listView.itemsCount - 1);
      this.emitChange();
    });
  },

  onGenericEditError(e) {
    var validationErrors = utils.getValidationErrors(e);
    this.setInData(['editingItemData', 'validationErrors'], validationErrors);
    this.setInData(['editingItemData', 'saving'], false);
    console.error(e);
    this.emitChange();
  },

  onOpenToolbarEndpoints() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.ENDPOINTS,
      EndpointsStore.getData(), false);
  },

  onCloseToolbar() {
    if (!this.data.editingItemData) {
      this.closeToolbar();
    } else {
      var contextViewData = {
        expanded: false,
        activeItem: null
      };
      this.setInData(['editingItemData', 'contextView'], contextViewData);
      this.emitChange();
    }
  },

  onSelectView(view, endpoint) {
    switch (view) {
      case 'basic':
        EndpointsActions.retrieveEndpoints();
        break;
      case 'network':
        SubnetworksActions.retrieveSubnetworks(endpoint && endpoint.documentSelfLink,
            endpoint && endpoint.endpointType);
        break;
      case 'storage':
        if (endpoint) {
          if (endpoint.endpointType === 'azure') {
            AzureStorageAccountsActions.retrieveAccounts();
          } else if (endpoint.endpointType === 'vsphere') {
            VsphereDatastoreActions.retrieveDatastores(endpoint.documentSelfLink);
            VsphereStoragePolicyActions.retrieveStoragePolicies(endpoint.documentSelfLink);
          }
        }
        break;
    }
  },

  onCreateEndpoint() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.ENDPOINTS,
        EndpointsStore.getData(), true);
    EndpointsActions.editEndpoint({});
  },

  onManageEndpoints() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.ENDPOINTS,
        EndpointsStore.getData(), true);
  },

  onCreateSubnetwork() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.SUBNETWORKS,
        SubnetworksStore.getData(), true);
    SubnetworksActions.editSubnetwork({});
  },

  onManageSubnetworks() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.SUBNETWORKS,
        SubnetworksStore.getData(), true);
  },

  onLoadStorageTags(tagLinks) {
    let tags = {};
    services.loadTags(tagLinks).then((tagsResponse) => {
      tags = Object.values(tagsResponse);
    });
    return tags;
  },

  onManageAzureStorageAccounts() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.STORAGE_AZURE,
      AzureStorageAccountsStore.getData(), true);
  },

  onManageVsphereStoragePolicies() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.VSPHERE_STORAGE_POLICIES,
      VsphereStoragePolicyStore.getData(), true);
  },

  onManageVsphereDatastores() {
    onOpenToolbarItem.call(this, constants.CONTEXT_PANEL.VSPHERE_DATASTORES,
      VsphereDatastoreStore.getData(), true);
  }
});

export default ProfilesStore;
