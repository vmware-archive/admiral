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

import { TemplateActions } from 'actions/Actions';

import constants from 'core/constants';
import utils from 'core/utils';

var ResourceConnectionsDataMixin = {

  attached: function() {

    this.unwatchNetworks = this.$watch('networks', (networks, oldNetworks) => {
      if (networks !== oldNetworks) {
        this.networksChanged(networks);
      }
    });

    this.unwatchNetworkLinks = this.$watch('networkLinks', (networkLinks, oldNetworkLinks) => {
      if (networkLinks !== oldNetworkLinks) {
        Vue.nextTick(() => {
          this.applyContainerToResourcesLinks(networkLinks,
            constants.RESOURCE_CONNECTION_TYPE.NETWORK);
        });
      }
    });

    this.unwatchVolumes = this.$watch('volumes', (volumes, oldVolumes) => {
      if (volumes !== oldVolumes) {
        this.volumesChanged(volumes);
      }
    });

    this.unwatchVolumeLinks = this.$watch('volumeLinks', (volumeLinks, oldVolumeLinks) => {
      if (volumeLinks !== oldVolumeLinks) {
        Vue.nextTick(() => {
          this.applyContainerToResourcesLinks(volumeLinks,
            constants.RESOURCE_CONNECTION_TYPE.VOLUME);
        });
      }
    });
  },

  detached: function() {
    this.unwatchNetworks();
    this.unwatchNetworkLinks();
    this.unwatchVolumes();
    this.unwatchVolumeLinks();
  },

  methods: {
    networksChanged: function(networks) {
      var gridChildren = this.$refs.containerGrid.$children;

      gridChildren.forEach((child) => {
        if (child.$children && child.$children.length === 1) {
          var container = child.$children[0];

          if (container.model && container.model.documentSelfLink) {
            this.updateContainerEndpoints(networks, container.model.documentSelfLink,
              constants.RESOURCE_CONNECTION_TYPE.NETWORK);
          }
        }
      });

      this.onLayoutUpdate();
    },

    volumesChanged: function(volumes) {
      var gridChildren = this.$refs.containerGrid.$children;

      gridChildren.forEach((child) => {
        if (child.$children && child.$children.length === 1) {
          var containerVueComponent = child.$children[0];

          var containerData = containerVueComponent.model;
          var containerDocumentSelfLink = containerData && containerData.documentSelfLink;

          this.updateContainerVolumesEndpoints(containerDocumentSelfLink, containerData, volumes);
        }
      });

      this.onLayoutUpdate();
    },

    containerAttached: function(e) {
      var containerDocumentLink = e.model.documentSelfLink;

      let $containerEl = $(e.$el);
      let $containerAnchorEl = $containerEl.find('.container-resource-relations')[0];

      this.prepareContainerEndpoints($containerAnchorEl, containerDocumentLink);

      this.updateContainerEndpoints(this.networks, containerDocumentLink,
                                      constants.RESOURCE_CONNECTION_TYPE.NETWORK);
      this.updateContainerVolumesEndpoints(containerDocumentLink, e.model, this.volumes);
    },

    // Networks
    networkAttached: function(e) {
      var networkDescriptionLink = e.model.documentSelfLink;
      var resourceAnchor = $(e.$el).find('.resource-anchor')[0];

      this.addResourceEndpoint(resourceAnchor, networkDescriptionLink,
        constants.RESOURCE_CONNECTION_TYPE.NETWORK);
    },
    networkDetached: function(e) {
      var resourceAnchor = $(e.$el).find('.resource-anchor')[0];

      this.removeResourceEndpoint(resourceAnchor);
    },
    editNetwork: function(e) {
      TemplateActions.openEditNetwork(this.model.documentId, e.model);

      this.$dispatch('disableNetworkSaveButton', false);
    },
    removeNetwork: function(e) {
      TemplateActions.removeNetwork(this.model.documentId, e.model);
    },
    // Volumes
    volumeAttached: function(e) {
      var volumeDescriptionLink = e.model.documentSelfLink;
      var resourceAnchor = $(e.$el).find('.resource-anchor')[0];

      this.addResourceEndpoint(resourceAnchor, volumeDescriptionLink,
        constants.RESOURCE_CONNECTION_TYPE.VOLUME);
    },
    volumeDetached: function(e) {
      var resourceAnchor = $(e.$el).find('.resource-anchor')[0];

      this.removeResourceEndpoint(resourceAnchor);
    },
    editVolume: function(e) {
      TemplateActions.openEditVolume(this.model.documentId, e.model);

      this.$dispatch('disableVolumeSaveButton', false);
    },
    removeVolume: function(e) {
      TemplateActions.removeVolume(this.model.documentId, e.model);
    },

    updateContainerVolumesEndpoints: function(containerDocumentSelfLink, containerData, volumes) {
      if (!containerDocumentSelfLink) {
        return;
      }

      let containerVolumes = [];
      if (containerData.containers) { // cluster
        containerData.containers.forEach((container) => {
          if (container.volumes) {
            containerVolumes = containerVolumes.concat(container.volumes);
          }
        });
      } else {
        containerVolumes = containerData.volumes;
      }

      var containerVolumePaths = {};
      if (containerVolumes) {
        containerVolumes.forEach((containerVolumeString) => {

          let volume = utils.findVolume(containerVolumeString, volumes);

          if (volume) {
            let idxContainerVolNameEnd = containerVolumeString.indexOf(':');

            containerVolumePaths[volume.documentSelfLink] = (idxContainerVolNameEnd > -1)
            && containerVolumeString.substring(idxContainerVolNameEnd + 1);
          }
        });

        this.updateContainerEndpoints(volumes, containerDocumentSelfLink,
          constants.RESOURCE_CONNECTION_TYPE.VOLUME, containerVolumePaths);
      }
    }
  },
  filters: {
    networksOrderBy: function(items) {
      var priorityNetworks = [constants.NETWORK_MODES.HOST.toLowerCase(),
        constants.NETWORK_MODES.BRIDGE.toLowerCase()];

      if (items.asMutable) {
        items = items.asMutable();
      }

      return items.sort(function(a, b) {
        var aName = a.name.toLowerCase();
        var bName = b.name.toLowerCase();
        for (var i = 0; i < priorityNetworks.length; i++) {
          var net = priorityNetworks[i];
          if (net === aName) {
            return -1;
          }
          if (net === bName) {
            return 1;
          }
        }

        return aName.localeCompare(bName);
      });
    },
    volumesOrderBy: function(items) {
      if (items.asMutable) {
        items = items.asMutable();
      }

      return items.sort(function(a, b) {
        var aName = a.name.toLowerCase();
        var bName = b.name.toLowerCase();

        return aName.localeCompare(bName);
      });
    }
  }
};

export default ResourceConnectionsDataMixin;
