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
        // TODO for composite components retrieve also the child requests
      });

      this.setInData(['request'], theRequest);
      this.emitChange();

      if (theRequest) {

        if (theRequest.resourceDescriptionLink) {
          if (theRequest.resourceType === 'DOCKER_CONTAINER') {
            services.loadContainerDescription(theRequest.resourceDescriptionLink)
              .then((containerDesc) => {

                this.setInData(['resourceDescription'], containerDesc);
                this.emitChange();
              }).catch((e) => {
                console.log('Error', e);
              });
          } else if (theRequest.resourceType === 'COMPOSITE_COMPONENT') {
            services.loadContainerTemplate(utils.getDocumentId(theRequest.resourceDescriptionLink))
              .then((compositeDesc) => {

                if (compositeDesc && compositeDesc.descriptionLinks) {
                  let containerDescriptionLinks = compositeDesc.descriptionLinks
                    .filter((descLink) => {
                      return descLink.indexOf('container-descriptions') > -1;
                    });
                  let networkDescriptionLinks = compositeDesc.descriptionLinks
                    .filter((descLink) => {
                      return descLink.indexOf('container-network-descriptions') > -1;
                    });
                  console.log('containerDescriptionLinks', containerDescriptionLinks);
                  console.log('networkDescriptionLinks', networkDescriptionLinks);

                  let containerDescCalls = containerDescriptionLinks.map((cdl => {
                    return services.loadContainerDescription(cdl);
                  }));
                  let netDescCalls = networkDescriptionLinks.map((ndl => {
                    return services.loadNetworkDescription(ndl);
                  }));

                  Promise.all(containerDescCalls).then((containerDescResults) => {
                    let containerDescriptions = [];
                    for (let i = 0; i < containerDescResults.length; i++) {
                      containerDescriptions.push(containerDescResults[i]);
                    }

                    this.setInData(['childContainerDescriptions'], containerDescriptions);
                    this.emitChange();
                  }).catch((e) => {
                    console.log('Error', e);
                  });

                  Promise.all(netDescCalls).then((netDescResults) => {
                    let networkDescriptions = [];
                    for (let i = 0; i < netDescResults.length; i++) {
                      networkDescriptions.push(netDescResults[i]);
                    }

                    this.setInData(['childNetworkDescriptions'], networkDescriptions);
                    this.emitChange();
                  }).catch((e) => {
                    console.log('Error', e);
                  });
                }

                this.setInData(['resourceDescription'], compositeDesc);
                this.emitChange();
              });
          }
        }

        if (theRequest.groupResourcePlacementLink) {
          services.loadPlacement(theRequest.groupResourcePlacementLink).then((placement) => {
            this.setInData(['placement'], placement);
            this.emitChange();

          if (placement && placement.resourcePoolLink) {
            services.loadResourcePool(placement.resourcePoolLink).then((placementZone) => {
              this.setInData(['placementZone'], placementZone && placementZone.resourcePoolState);
              this.emitChange();
            });
          }
          });
        }

        if (theRequest.resourceLinks) {
          // /resources/containers/hellooz-mcm111-29092357826
          // TODO add affinity, anti-affinity info
          // cluster info??
          let resourceCalls = [];
          //let isHosts = false;
          let isContainers = false;
          theRequest.resourceLinks.forEach((resourceLink) => {
            if (resourceLink.indexOf('containers') > -1) {
              resourceCalls.push(services.loadContainer(utils.getDocumentId(resourceLink)));
              isContainers = true;
            } else if (resourceLink.indexOf('compute') > -1) {
              resourceCalls.push(services.loadHostByLink(resourceLink));
              //isHosts = true;
            }
          });

          Promise.all(resourceCalls).then((resourcesResults) => {
            let resources = [];
            for (let i = 0; i < resourcesResults.length; i++) {
              resources.push(resourcesResults[i]);
            }

            this.setInData(['resources'], resources);
            this.emitChange();

            if (isContainers && (resources.length > 0)) {

              let parentLinks = [...new Set(resources.map((resource) => resource.parentLink))];
              if (parentLinks.length > 0) {
              let hostCalls = [];
              parentLinks.forEach((parentLink) => {
                if (parentLink && parentLink.indexOf('compute') > -1) {
                  hostCalls.push(services.loadHostByLink(parentLink));
                }
              });

              Promise.all(hostCalls).then((hostResults) => {
                let hosts = [];
                for (let i = 0; i < hostResults.length; i++) {
                  hosts.push(hostResults[i]);
                }

                this.setInData(['hosts'], hosts);
                this.emitChange();
              });
            }
          }
        }).catch((e) => {
          console.log('Error', e);
        });
      }
      }
    });

    services.loadRequestGraph(requestId, host).then((graph) => {
      this.setInData(['graph'], graph);
      this.emitChange();
    });
  }
});

export default RequestsGraphStore;
