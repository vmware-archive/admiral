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

import * as actions from 'actions/Actions';
import services from 'core/services';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';

let RequestsGraphStore = Reflux.createStore({
  mixins: [CrudStoreMixin],
  listenables: [actions.RequestGraphActions],

  onOpenRequestGraph: function(requestId, host) {
    services.loadRequestGraph(requestId, host).then((graph) => {
      this.setInData(['graph'], graph);
      this.emitChange();

      this.processGeneralInfo(graph);
      this.processRequestInfos(graph);
    });
  },

  processGeneralInfo: function(graph) {
    var request = graph.request;
    if (!request) {
      return;
    }

    var promises = [];

    if (request.resourceLinks) {
      request.resources = [];
      request.resourceLinks.forEach((resourceLink) => {
        promises.push(services.loadDocument(resourceLink).then((resource) => {
          request.resources.push(resource);
        }));
      });
    }

    if (request.tenantLinks) {
      request.tenants = [];
      request.tenantLinks.forEach((tenantLink) => {
        promises.push(services.loadDocument(tenantLink).then((tenant) => {
          request.tenants.push(tenant);
        }));
      });
    }

    Promise.all(promises).then(() => {
      this.setInData(['request'], request);
      this.emitChange();
    });
  },

  processRequestInfos: function(graph) {
    let componentInfos = graph.componentInfos;
    if (!componentInfos || componentInfos.length < 1) {
      // no request info available
      return;
    }

    Promise.all(componentInfos.map(ri => this.processRequestInfo(ri))).then((infos) => {
      this.setInData(['infos'], infos);
      this.emitChange();
    });
  },

  processRequestInfo: function(info) {
    var result = {
      type: info.type
    };

    var promises = [];
    if (info.groupResourcePlacementLink) {
      promises.push(services.loadPlacement(info.groupResourcePlacementLink).then((placement) => {
        result.placement = placement;
        if (placement.resourcePoolLink) {
          return services.loadResourcePool(placement.resourcePoolLink).then((placementZone) => {
            result.placementZone = placementZone.resourcePoolState;
          });
        }
      }));
    }

    if (info.resourceDescriptionLink) {
      promises.push(services.loadDocument(info.resourceDescriptionLink)
      .then((resourceDescription) => {
        result.resourceDescription = resourceDescription;
      }));
    }

    if (info.hostSelections) {
      var hostLinks = info.hostSelections.map(hs => hs.hostLink);

      result.hosts = [];

      hostLinks.forEach((hostLink) => {
        promises.push(services.loadHostByLink(hostLink).then((host) => {
          result.hosts.push(host);
        }));
      });
    }

    return Promise.all(promises).then(() => {
      return result;
    });
  }
});

export default RequestsGraphStore;
