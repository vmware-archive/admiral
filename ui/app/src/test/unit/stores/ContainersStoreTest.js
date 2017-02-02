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

import ContainersStore from 'stores/ContainersStore';
import { ContainerActions, NavigationActions } from 'actions/Actions';
import services from 'core/services';
import constants from 'core/constants';
import routes from 'core/routes';
import links from 'core/links';
import ContainersMockData from 'unit/helpers/containers/ContainersMockData'

describe("ContainerStore test", function() {
  var totalNumberShownItems;
  var numberShownItemsCompositeDetails;

  var waitForData = function(conditionFn) {
    return testUtils.waitForListenable(ContainersStore, conditionFn);
  };

  beforeEach(function() {
    // as per current mock data, because there are containers, eligible for clustering
    totalNumberShownItems = 7; // 1 cluster of 3 containers, 1 application, 1 single container
    numberShownItemsCompositeDetails = 2; // 1 cluster of 2 containers, 1 single container

    hasher.changed.active = false; //disable changed signal
    hasher.setHash(""); //set hash without dispatching changed signal
    hasher.changed.active = true; //re-enable signal
    routes.initialize();

    services.loadDocument = function(documentSelfLink) {
      return new Promise(function(resolve, reject){
        setTimeout(function() {
          resolve({
            documentSelfLink: documentSelfLink
          });
        }, 0);
      });
    };

    spyOn(services, 'loadContainers').and.callFake(function() {
      return new Promise(function(resolve, reject){
        setTimeout(function() {
          var documents = JSON.parse(JSON.stringify(ContainersMockData.containers));
          var resultToReturn = {
            documents,
            documentLinks: Object.keys(documents)
          };
          resolve(resultToReturn);
        }, 0);
      });
    });
    spyOn(services, 'loadContainerNetworks').and.callFake(function() {
      return new Promise(function(resolve, reject){
        setTimeout(function() {
          var documents = {};
          var resultToReturn = {
            documents,
            documentLinks: Object.keys(documents)
          };
          resolve(resultToReturn);
        }, 0);
      });
    });
    spyOn(services, 'loadContainer').and.callFake(function(containerId) {
      return new Promise(function(resolve, reject){
        setTimeout(function() {
          console.log("containerId " + containerId);
          var container = ContainersMockData.containers["/resources/containers/" + containerId];

          resolve(JSON.parse(JSON.stringify(container)));
        }, 0);
      });
    });
    spyOn(services, 'loadCompositeComponents').and.callFake(function() {
      return new Promise(function(resolve, reject){
        setTimeout(function() {
          var documents = JSON.parse(JSON.stringify(ContainersMockData.compositeComponents));
          var resultToReturn = {
            documents,
            documentLinks: Object.keys(documents)
          };
          resolve(resultToReturn);
        }, 0);
      });
    });
    spyOn(services, 'loadCompositeComponent').and.callFake(function(compositeComponentId) {
      return new Promise(function(resolve, reject){
        setTimeout(function() {
          var compositeComponent =
            ContainersMockData.compositeComponents["/resources/composite-components/" + compositeComponentId];
          resolve(JSON.parse(JSON.stringify(compositeComponent)));
        }, 0);
      });
    });
    spyOn(services, 'loadContainerLogs').and.callFake(function(containerId) {
      return new Promise(function(resolve, reject){
        setTimeout(function() {
          resolve("Logs for " + containerId);
        }, 0);
      });
    });
    spyOn(services, 'loadHostByLink').and.callFake(function(hostParentLink) {
      return new Promise(function(resolve, reject) {
        setTimeout(function() {
          let host = ContainersMockData.parentHosts[0];
          resolve(JSON.parse(JSON.stringify(host)));
        }, 0);
      });
    });
  });

  afterEach(function() {
    ContainersStore._clearData();
  });

  xdescribe("search for containers", function() {

    it("invoke ajax with filter from browser location", function(done) {
      window.location.hash = "#/containers?name=docker&name=dcp&parentId=localhost:2377";
      waitForData().then(function() {
        var url = "/resources/containers?";
        url += "$filter=names eq '[docker*]' and names eq '[dcp*]' and parentLink eq '/resources/compute/localhost*'";
        url += "&expand=true";

        var mostRecentDecodedUrl = decodeURIComponent(jasmine.Ajax.requests.mostRecent().url);
        expect(mostRecentDecodedUrl).toBe(url);
        done();
      });
    });

    it("invoke ajax with filter from browser location and ignore unknown properties", function(done) {
      window.location.hash = "#/containers?someFakeProperty=value";
      waitForData().then(function() {
        expect(jasmine.Ajax.requests.mostRecent().url).toBe("/resources/containers?expand=true");
        done();
      });
    });
  });

  describe("container details", function() {
    var containerStats = {
      "cpuUsage": 14.96,
      "memLimit": 30000000,
      "memUsage": 9126240,
      "networkIn": 104661,
      "networkOut": 1738101,
      "group": "docker-test",
      "documentSelfLink": "/resources/container-stats/" + ContainersMockData.containerIds[0]
    };

    beforeEach(function() {
      spyOn(services, 'loadContainerStats').and.callFake(function(containerId) {
        return new Promise(function(resolve, reject){
          setTimeout(function() {
            resolve(JSON.parse(JSON.stringify(containerStats)));
          }, 0);
        });
      });
    });

    it("load containers list and container instance details", function(done) {
      var containerId = ContainersMockData.containerIds[0];
      var containerDocumentLink = "/resources/containers/" + containerId;

      waitForData(function(data) {
        return data.listView && data.selectedItem && data.selectedItemDetails;
      }).then(function(data) {
        // First the list items will start loading
        expect(data.listView.itemsLoading).toBe(true);
        expect(data.selectedItem.documentId).toEqual(data.selectedItemDetails.documentId);
        expect(data.selectedItem.documentId).toBe(containerId);
        expect(data.selectedItem.type).toEqual(constants.CONTAINERS.TYPES.SINGLE);
        expect(data.selectedItemDetails.logsLoading).toBe(true);
        expect(data.selectedItemDetails.statsLoading).toBe(true);

        return waitForData(function(data) {
          return !data.listView.itemsLoading && data.selectedItemDetails.instance;
        });
      }).then(function(data) {
        expect(data.listView.items.length).toBe(totalNumberShownItems);

        var details = data.selectedItemDetails;

        expect(details.documentId).toBe(containerId);
        expect(details.instance.documentId).toBe(containerId);
        expect(details.instance.powerState).toBe("RUNNING");
        expect(details.instance.attributes.NetworkSettings.IPAddress).toBe("10.23.47.89");

        return waitForData(function(data) {
          return data.selectedItemDetails.descriptionLinkToConvertToTemplate;
        });

      }).then(function(data) {
        var details = data.selectedItemDetails;

        expect(details.descriptionLinkToConvertToTemplate).toBe(details.instance.descriptionLink);
        done();
      });

      NavigationActions.openContainerDetails(containerId);
    });

    it("load container instance details with link to create template", function(done) {
      var containerId = ContainersMockData.containerIds[0];

      waitForData(function(data) {
        return data.selectedItemDetails.descriptionLinkToConvertToTemplate;
      }).then(function(data) {
        var details = data.selectedItemDetails;

        expect(details.descriptionLinkToConvertToTemplate).toBe(details.instance.descriptionLink);
        done();
      });

      NavigationActions.openContainerDetails(containerId);
    });

    it("load container instance details with link to open template", function(done) {
      var containerId = ContainersMockData.containerIds[0];
      var container = ContainersMockData.containers[links.CONTAINERS + '/' + containerId];

      var templateComponent = {
        documentSelfLink: links.CONTAINER_DESCRIPTIONS + '/top-most-container-description'
      };

      var template = {
        documentSelfLink: links.COMPOSITE_DESCRIPTIONS + '/top-most-composite-description',
        descriptionLinks: [templateComponent.documentSelfLink],
      };

      var originalLoadDocument = services.loadDocument;
      services.loadDocument = function(documentSelfLink) {
        if (container.descriptionLink !== documentSelfLink) {
          return originalLoadDocument.call(null, documentSelfLink);
        }

        return new Promise(function(resolve, reject) {
          setTimeout(function() {
            resolve({
              documentSelfLink: documentSelfLink,
              parentDescriptionLink: templateComponent.documentSelfLink
            });
          }, 0);
        });
      };

      spyOn(services, 'loadTemplatesContainingComponentDescriptionLink').and.callFake(function() {
        return new Promise(function(resolve, reject) {
          resolve({
            0: template
          });
        });
      });

      waitForData(function(data) {
        return data.selectedItemDetails.templateLink;
      }).then(function(data) {
        var details = data.selectedItemDetails;
        expect(details.templateLink).toBe(template.documentSelfLink);
        done();
      });

      NavigationActions.openContainerDetails(containerId);
    });

    it("load containers list, container instance details, request logs and stats", function(done) {
      var containerId = ContainersMockData.containerIds[0];

      waitForData(function(data) {
        return !data.listView.itemsLoading && data.selectedItemDetails.instance;
      }).then(function(data) {
        expect(data.selectedItemDetails.logsLoading).toBe(true);
        expect(data.selectedItemDetails.statsLoading).toBe(true);

        ContainerActions.refreshContainerLogs();
        ContainerActions.refreshContainerStats();

        return waitForData(function(data) {
          var details = data.selectedItemDetails;
          return !details.logsLoading && !details.statsLoading;
        });
      }).then(function(data) {
        var details = data.selectedItemDetails;

        expect(data.selectedItemDetails.logs).toBe("Logs for " + containerId);
        expect(data.selectedItemDetails.stats).toEqual(containerStats);
        done();
      });

      NavigationActions.openContainerDetails(containerId);
    });

    describe("shell", function() {
      beforeEach(function() {
        spyOn(services, 'getContainerShellUri').and.callFake(function(containerId) {
          return new Promise(function(resolve, reject){
            setTimeout(function() {
              resolve('/uic/rp/remote-service-' + containerId);
            }, 0);
          });
        });
      });

      it("should open and close shell of container", function(done) {
        var containerId = ContainersMockData.containerIds[0];
        var containerDocumentLink = "/resources/containers/" + containerId;

        NavigationActions.openContainerDetails(containerId);

        waitForData(function(data) {
          return data.listView && data.selectedItem && data.selectedItemDetails;
        }).then(function(data) {
          ContainerActions.openShell(containerId);

          return waitForData(function(data) {
            return data.selectedItemDetails.shell;
          });
        }).then(function(data) {
          expect(data.selectedItemDetails.shell.shellUri).toBe('uic/rp/remote-service-' + containerId + '/');

          ContainerActions.closeShell();

          return waitForData(function(data) {
            return !data.selectedItemDetails.shell;
          });
        }).then(function(data) {
          done();
        });
      });
    })
  });

  describe("composite container details", function() {
    beforeEach(function() {
      spyOn(services, 'loadContainersForCompositeComponent').and.callFake(function(compositeContainerId) {
        return new Promise(function(resolve, reject){
          setTimeout(function() {
            var groupContainers = {};

            for (var i = 0; i < ContainersMockData.compositeComponentContainerIds.length; i++) {
              var id = ContainersMockData.compositeComponentContainerIds[i];
              var container = ContainersMockData.containers["/resources/containers/" + id];
              groupContainers[container.documentSelfLink] = container;
            }

            resolve(JSON.parse(JSON.stringify(groupContainers)));
          }, 0);
        });
      });
    });

    it("load containers list and composite container instance details", function(done) {
      var compositeId = ContainersMockData.compositeComponentIds[0];

      waitForData(function(data) {
        return data.listView.itemsLoading &&
          data.selectedItemDetails.listView.itemsLoading;
      }).then(function(data) {
        // First the list items will start loading
        expect(data.listView.itemsLoading).toBe(true);
        expect(data.selectedItem.documentId).toBe(compositeId);
        expect(data.selectedItemDetails.documentId).toBe(compositeId);
        expect(data.selectedItemDetails.listView.itemsLoading).toBe(true);
        expect(data.selectedItem.type).toEqual(constants.CONTAINERS.TYPES.COMPOSITE);

        return waitForData(function(data) {
          return !data.listView.itemsLoading &&
            !data.selectedItemDetails.listView.itemsLoading;
        });
      }).then(function(data) {
        expect(data.listView.items.length).toBe(totalNumberShownItems);
        expect(data.selectedItemDetails.listView.items.length)
          .toBe(numberShownItemsCompositeDetails);
        done();
      });

      NavigationActions.openCompositeContainerDetails(compositeId);
    });

    it("load containers list, composite container instance details and child container details", function(done) {
      var compositeId = ContainersMockData.compositeComponentIds[0];
      var childContainerId = ContainersMockData.compositeComponentContainerIds[0];

      waitForData(function(data) {
        return data.listView && data.selectedItem && data.selectedItemDetails;
      }).then(function(data) {
        // First the list items will start loading
        expect(data.listView.itemsLoading).toBe(true);
        expect(data.selectedItem).not.toEqual(data.selectedItemDetails);
        expect(data.selectedItem.documentId).toBe(compositeId);
        expect(data.selectedItem.type).toEqual(constants.CONTAINERS.TYPES.COMPOSITE);
        expect(data.selectedItemDetails.documentId).toBe(compositeId);
        expect(data.selectedItemDetails.type).toEqual(constants.CONTAINERS.TYPES.COMPOSITE);

        return waitForData(function(data) {
          var compositeComponent = data.selectedItemDetails;
          return !data.listView.itemsLoading &&
            compositeComponent.listView.items && compositeComponent.selectedItemDetails.instance;
        });
      }).then(function(data) {
        expect(data.listView.items.length).toBe(totalNumberShownItems);

        var compositeComponent = data.selectedItemDetails;
        expect(compositeComponent.listView.items.length).toBe(numberShownItemsCompositeDetails);
        expect(compositeComponent.selectedItemDetails.instance.documentId).toBe(childContainerId);
        expect(compositeComponent.selectedItemDetails.instance.powerState).toBe("RUNNING");
        done();
      });

      NavigationActions.openContainerDetails(childContainerId, null, compositeId);
    });
  });

  describe('Container Clustering Day2 Operations', function() {

    var singleContainerId = ContainersMockData.containerIds[0];
    var singleContainerLink = "/resources/containers/" + singleContainerId;
    var singleContainer = ContainersMockData.containers[singleContainerLink];

    var scaledContainersId = "/resources/container-descriptions/dcp-test:latest-id";

    var totalClusterSize = 2;

    var clusteringRequest = {
      "documentKind": "com:vmware:admiral:request:RequestBrokerService:RequestBrokerState",
      "documentOwner": "cbf96818-5afe-434e-8cf8-cc11fd01ea8b",
      "documentSelfLink": "/requests/2a170e39-ab95-4e30-864f-c51af43c3954",
      "customProperties": {},
      "documentUpdateAction": "POST",
      "documentUpdateTimeMicros": 1457529874418001,
      "documentVersion": 0,
      "operation": "CLUSTER_RESOURCE",
      "requestTrackerLink": "/request-status/2a170e39-ab95-4e30-864f-c51af43c3954",
      "resourceCount": totalClusterSize,
      "resourceDescriptionLink": "/resources/container-descriptions/dcp-test:latest-id2",
      "resourceType": "DOCKER_CONTAINER",
      "serviceTaskCallback": {
        "serviceSelfLink": "No callback set",
        "subStageComplete": "COMPLETED",
        "subStageFailed": "ERROR",
        "taskStageComplete": "FINISHED",
        "taskStageFailed": "FAILED"
      },
      "taskInfo": {
        "isDirect": false,
        "stage": "CREATED"
      },
      "taskSubStage": "CREATED"
    };

    beforeEach(function() {
      spyOn(services, 'modifyClusterSize').and.callFake(function(descriptionLink, contextId, totalClusterSize) {
        return new Promise(function(resolve, reject) {
          setTimeout(function() {
            clusteringRequest.resourceDescriptionLink = descriptionLink;
            clusteringRequest.resourceCount = totalClusterSize;

            resolve(JSON.parse(JSON.stringify(clusteringRequest)));
          }, 0);
        });
      });

      spyOn(services, 'loadClusterContainers').and.callFake(function(clusterId) {
        return new Promise(function(resolve, reject) {
          setTimeout(function() {
            var containers = {
              singleContainerLink : singleContainer
            };

            resolve(JSON.parse(JSON.stringify(containers)));
          }, 0);
        });
      });
    });

    it('load containers list and invoke cluster day2 operation on a single container', function(done) {
      waitForData(function(data) {
        return data.listView;
      }).then(function(data) {
        // start loading list items
        expect(data.listView.itemsLoading).toBe(true);

        return waitForData(function(data) {
          return !data.listView.itemsLoading;
        });
      }).then(function(data) {
        // the list items are loaded
        expect(data.listView.items.length).toBe(totalNumberShownItems);

        ContainerActions.scaleContainer(singleContainer.descriptionLink);

        return waitForData(function(data) {
          return data.contextView;
        });
      }).then(function(data) {
        // should open requests tab
        expect(data.contextView.expanded).toBe(true);

        done();
      });

      NavigationActions.openContainers();
    });


    it('load containers list and invoke cluster day2 operation on scaled containers', function(done) {
      waitForData(function(data) {
        return data.listView;
      }).then(function(data) {
        // start loading list items
        expect(data.listView.itemsLoading).toBe(true);

        return waitForData(function(data) {
          return !data.listView.itemsLoading;
        });
      }).then(function(data) {
        // the list items are loaded
        expect(data.listView.items.length).toBe(totalNumberShownItems);

        ContainerActions.modifyClusterSize(scaledContainersId, null, 3);

        return waitForData(function(data) {
          return data.contextView;
        });
      }).then(function(data) {
        // should open requests tab
        expect(data.contextView.expanded).toBe(true);

        done();
      });

      NavigationActions.openContainers();
    });
  });
});