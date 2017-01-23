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
import constants from 'core/constants';
import utils from 'core/utils';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';

const OPERATION = {
  LIST: 'LIST'
};

const CHECK_INTERVAL_MS = 2000;

var findRequestStatus = function(requestStatuses, documentSelfLink) {
  let requestStatusMatches = requestStatuses.filter((requestStatus) => {
    return requestStatus.documentSelfLink === documentSelfLink;
  });

  return requestStatusMatches.length > 0 && requestStatusMatches[0];
};

var mergeItems = function(items1, items2) {
  return items1.concat(items2).filter((item, index, self) =>
      self.findIndex((c) => c.documentSelfLink === item.documentSelfLink) === index);
};

let activeRequests = [];

let RequestsStore = Reflux.createStore({
  mixins: [CrudStoreMixin],
  listenables: [actions.RequestsActions],

  onOpenRequests: function() {
    activeRequests = [];
    this.setInData(['items'], []);
    this.setInData(['itemsLoading'], true);
    this.setInData(['itemsType'], 'all');
    this.emitChange();

    this.onRefreshRequests();
  },

  onRefreshRequests: function() {
    var operation = this.requestCancellableOperation(OPERATION.LIST);

    if (operation) {
      operation.forPromise(services.loadRequestStatuses(this.data.itemsType)).then((result) => {
        var items = result.documentLinks.map((documentLink) => result.documents[documentLink]);

        this.processItems(items, result.nextPageLink);
      });
    }
    this.emitChange();
  },

  onOpenRequestsNext: function(nextPageLink) {
    var operation = this.requestCancellableOperation(OPERATION.LIST);

    if (operation) {
      this.setInData(['itemsLoading'], true);
      this.emitChange();

      operation.forPromise(services.loadNextPage(nextPageLink)).then((result) => {
        var items = mergeItems(this.data.items.asMutable(),
            result.documentLinks.map((documentLink) => result.documents[documentLink]));

        this.processItems(items, result.nextPageLink);
      });
    }
    this.emitChange();
  },

  onClearRequests: function() {
    services.clearRequests().then(() => {
      this.setInData(['items'], []);
      this.emitChange();
    }).catch((error) => {
      console.log('Could not clear requests: ' + error);
    });
  },

  onCloseRequests: function() {
    this.setInData(['items'], []);
    this.setInData(['itemsLoading'], false);
    this.setInData(['nextPageLink'], null);
  },

  onSelectRequests(itemsType) {
    this.setInData(['items'], []);
    this.setInData(['itemsLoading'], true);
    this.setInData(['itemsType'], itemsType);

    this.onRefreshRequests();
  },

  onRequestCreated: function(request) {
    this.setInData(['itemsLoading'], true);
    this.emitChange();

    services.loadRequestStatuses(this.data.itemsType).then((result) => {
      var newItems = result.documentLinks.map((documentLink) => result.documents[documentLink]);

      // in embedded mode the result is not an object, but array
      var newReq = findRequestStatus(newItems, request.requestTrackerLink);
      if (newReq) {
        this.setInData(['newItem'], newReq);
        activeRequests.push(newReq.documentSelfLink);

        if (!this.requestCheckInterval) {
          this.requestCheckInterval = setInterval(this.onRefreshRequests, CHECK_INTERVAL_MS);
        }
      }

      this.processItems(newItems, result.nextPageLink);

      this.setInData(['itemsLoading'], false);
      this.emitChange();
      this.setInData(['newItem'], null);
    });
  },

  onRemoveRequest: function(requestStatusSelfLink) {

    services.removeRequestStatus(requestStatusSelfLink).then(() => {
      var requests = this.data.items.asMutable();

      for (var i = 0; i < requests.length; i++) {
        if (requests[i].documentSelfLink === requestStatusSelfLink) {
          requests.splice(i, 1);
        }
      }

      this.setInData(['items'], requests);
      this.emitChange();
    }).catch((error) => {
      console.log('Could not delete request: ' + requestStatusSelfLink
                    + '. Failed with: ' + error);
    });
  },

  countRunning: function() {
    var result = 0;
    if (this.data.items && this.data.items !== constants.LOADING) {
      for (var request in this.data.items) {
        if (utils.isRequestRunning(this.data.items[request])) {
          result++;
        }
      }
    }

    return result;
  },

  processItems: function(requestStatuses, nextPageLink) {
    // Retrieve errors for failed requests
    let failedRequestStatuses = requestStatuses.filter((requestStatus) => {
      return utils.isRequestFailed(requestStatus);
    });

    let failedRequestsCalls = [];
    failedRequestStatuses.forEach((failedRequestStatus) => {
      failedRequestsCalls.push(
        services.loadRequestByStatusLink(failedRequestStatus.documentSelfLink));
    });

    if (failedRequestsCalls.length > 0) {
      Promise.all(failedRequestsCalls).then((result) => {
        let requests = utils.resultToArray(result);

        requests.map((request) => {
          let requestDocs = request;
          if (request.documents) {
            requestDocs = utils.resultToArray(request.documents);
          } else if (request.content) {
            requestDocs = request.content;
          }

          if (requestDocs && requestDocs.length > 0) {
            let requestStatus = failedRequestStatuses.filter((requestStatus) => {
              return requestDocs[0].requestTrackerLink === requestStatus.documentSelfLink;
            });

            if (requestStatus.length > 0) {
              let errorRequestDocs = requestDocs.filter((requestDoc) => {
                return requestDoc.taskInfo.failure && requestDoc.taskInfo.failure.message;
              });
              requestStatus[0].errorMessage = (errorRequestDocs.length > 0)
                                            ? errorRequestDocs[0].taskInfo.failure.message : null;
            }
          }
        });

        this.updateItems(requestStatuses, nextPageLink);
      });
    } else {

      this.updateItems(requestStatuses, nextPageLink);
    }
  },

  updateItems: function(requestStatuses, nextPageLink) {
    this.setInData(['items'], requestStatuses);
    this.setInData(['itemsLoading'], false);
    this.setInData(['nextPageLink'], nextPageLink);
    this.emitChange();

    this.checkCompletedRequests(requestStatuses);

    if (this.requestCheckInterval && activeRequests.length === 0) {
      clearInterval(this.requestCheckInterval);

      this.requestCheckInterval = null;
    }
  },

  checkCompletedRequests: function(requests) {
    var reqId;
    var req;
    for (var idxReq = 0; idxReq < activeRequests.length; idxReq++) {
      reqId = activeRequests[idxReq];
      req = findRequestStatus(requests, reqId);

      let resourceLinks = req && req.resourceLinks ? req.resourceLinks : null;

      if (req && utils.isRequestFinished(req)) {
        // remove from active
        activeRequests.splice(idxReq, 1);

        if (req.requestProgressByComponent.hasOwnProperty('Compute Provision')) {
          // Host Created
          return this.hostOperationSuccess();
        } else if (req.requestProgressByComponent.hasOwnProperty('Host Removal')) {
          // Host Removed
          return this.hostOperationSuccess();
        } else if (req.requestProgressByComponent.hasOwnProperty('Configure Host')) {
          // Host auto-configured
          return this.hostOperationSuccess();
        } else if (req.requestProgressByComponent.hasOwnProperty('Container Allocation')) {
          // Container created
          this.containerOperationSuccess(constants.CONTAINERS.OPERATION.CREATE);

        } else if (req.requestProgressByComponent.hasOwnProperty('Container Removal')) {
          // Container Removed
          this.containerOperationSuccess(constants.CONTAINERS.OPERATION.REMOVE, resourceLinks);

        } else if (req.requestProgressByComponent.hasOwnProperty('Container Network Removal')) {
          // Network Removed
          this.networkOperationSuccess(constants.RESOURCES.NETWORKS.OPERATION.REMOVE);

        } else if (req.requestProgressByComponent.hasOwnProperty('Container Volume Removal')) {
          // Volume Removed
          this.volumeOperationSuccess(constants.RESOURCES.VOLUMES.OPERATION.REMOVE);

        } else if (req.requestProgressByComponent.hasOwnProperty('Container Operation')) {

          if (req.name === 'Start') {
            // Container Started
            this.containerOperationSuccess(constants.CONTAINERS.OPERATION.START, resourceLinks);

          } else if (req.name === 'Stop') {
            // Container Stopped
            this.containerOperationSuccess(constants.CONTAINERS.OPERATION.STOP, resourceLinks);
          } else {
            // Default container operation
            this.containerOperationSuccess(constants.CONTAINERS.OPERATION.DEFAULT, resourceLinks);
          }
        } else if (req.requestProgressByComponent.hasOwnProperty('Resource Clustering')) {
          // Container clustering
          this.containerOperationSuccess(constants.CONTAINERS.OPERATION.CLUSTERING, resourceLinks);

        } else if (req.requestProgressByComponent.hasOwnProperty('Container Network Allocation')) {
          // Network created
          this.containerOperationSuccess(constants.CONTAINERS.OPERATION.NETWORKCREATE,
                                          resourceLinks);
        } else if (req.requestProgressByComponent.hasOwnProperty('Container Volume Allocation')) {
          // Volume created
          this.containerOperationSuccess(constants.CONTAINERS.OPERATION.CREATE_VOLUME,
                                          resourceLinks);
        }
      } else if (req && utils.isRequestFailed(req)) {

        activeRequests.splice(idxReq, 1);

        if (req.requestProgressByComponent.hasOwnProperty('Container Removal')) {
          // Container Removed
          this.containerOperationFail(constants.CONTAINERS.OPERATION.REMOVE, resourceLinks);

        } else if (req.requestProgressByComponent.hasOwnProperty('Container Network Removal')) {
          // Network Removal
          this.networkOperationFail(constants.RESOURCES.NETWORKS.OPERATION.REMOVE);
        } else if (req.requestProgressByComponent.hasOwnProperty('Container Volume Removal')) {
          // Volume Removal
          this.volumeOperationFail(constants.RESOURCES.VOLUMES.OPERATION.REMOVE);

        } else if (req.requestProgressByComponent.hasOwnProperty('Container Operation')) {

          if (req.name === 'Start') {
            // Container Started
            this.containerOperationFail(constants.CONTAINERS.OPERATION.START, resourceLinks);

          } else if (req.name === 'Stop') {
            // Container Stopped
            this.containerOperationFail(constants.CONTAINERS.OPERATION.STOP, resourceLinks);
          }
        }
      }
    }
  },

  hostOperationSuccess: function() {
    actions.HostActions.operationCompleted();
  },

  containerOperationSuccess: function(operationType, resourceLinks) {

    actions.ContainerActions.operationCompleted(operationType, this.getResourceIds(resourceLinks));
  },

  networkOperationSuccess: function(operationType) {

    actions.NetworkActions.networkOperationCompleted(operationType);
  },

  volumeOperationSuccess: function(operationType) {

    actions.VolumeActions.volumeOperationCompleted(operationType);
  },

  containerOperationFail: function(operationType, resourceLinks) {

    actions.ContainerActions.operationFailed(operationType, this.getResourceIds(resourceLinks));
  },

  networkOperationFail: function(operationType) {

    actions.NetworkActions.networkOperationFailed(operationType);
  },

  volumeOperationFail: function(operationType) {

    actions.VolumeActions.volumeOperationFailed(operationType);
  },

  getResourceIds: function(resourceLinks) {
    let resourceIds = [];

    if (resourceLinks) {
      for (let iResLink = 0; iResLink < resourceLinks.length; iResLink++) {
        resourceIds.push(utils.getDocumentId(resourceLinks[iResLink]));
      }
    }

    return resourceIds;
  }
});

export default RequestsStore;
