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
import utils from 'core/utils';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';

let RequestsGraphStore = Reflux.createStore({
  mixins: [CrudStoreMixin],
  listenables: [actions.RequestGraphActions],

  onOpenRequestGraph: function(requestId, host) {

    services.loadRequests().then((requests) => {
      let reqArr = utils.resultToArray(requests);
      let theRequest = reqArr.find((req) => {
        return req.documentSelfLink.endsWith(requestId);
      });

      this.setInData(['request'], theRequest);
      this.emitChange();

      if (theRequest && theRequest.resourceType === 'COMPOSITE_COMPONENT'
            && theRequest.resourceLinks && theRequest.resourceLinks.length > 0) {

        services.loadCompositeComponent(utils.getDocumentId(theRequest.resourceLinks[0]))
          .then((compositeComponent) => {

            this.setInData(['application'], compositeComponent);
            this.emitChange();
          }).catch((e) => {
            console.log('Error', e);
          });
      }
    });

    services.loadRequestGraph(requestId, host).then((graph) => {
      this.setInData(['graph'], graph);
      this.emitChange();

      this.processRequestInfos(graph);
    });
  },

  processRequestInfos: function(graph) {
    let requestInfos = graph.requestInfos;
    if (!requestInfos || requestInfos.length < 1) {
      // no request info available
      return;
    }

    let placementLinks = new Set();
    let containerDescriptionLinks = new Set();
    let hostLinks = new Set();
    requestInfos.forEach((requestInfo) => {
      // Container requests
      if (requestInfo.type === 'App.Container') {
        placementLinks.add(requestInfo.groupResourcePlacementLink);
        containerDescriptionLinks.add(requestInfo.resourceDescriptionLink);
        if (requestInfo.hostSelections) {
          requestInfo.hostSelections.forEach((hostSelection) => {
            hostLinks.add(hostSelection.hostLink);
          });
        }
      }
    });

    let placementCalls = [...placementLinks].map(services.loadPlacement);
    let containerDescriptionCalls = [...containerDescriptionLinks].map((cdl) =>
      services.loadContainerDescription(cdl));
    let hostCalls = [...hostLinks].map((hl) => services.loadHostByLink(hl));

    Promise.all(placementCalls).then((placementResults) => {
      let placements = placementResults;
      this.setInData(['placements'], placements);
      this.emitChange();

      let placementZoneLinks = new Set();
      placementResults.forEach((placementResult) => {
        if (placementResult.resourcePoolLink) {
          placementZoneLinks.add(placementResult.resourcePoolLink);
        }
      });
      let placementZoneCalls = [...placementZoneLinks].map(services.loadResourcePool);
      Promise.all(placementZoneCalls).then((placementZoneResults) => {
        this.setInData(['placementZones'], placementZoneResults.map(pz => pz.resourcePoolState));
        this.emitChange();
      });
    });

    Promise.all(containerDescriptionCalls).then((containerDescriptionResults) => {
      this.setInData(['resourceDescriptions'], containerDescriptionResults);
      this.emitChange();
    });

    Promise.all(hostCalls).then((hostResults) => {
      this.setInData(['hosts'], hostResults);
      this.emitChange();
    });
  }
});

export default RequestsGraphStore;
