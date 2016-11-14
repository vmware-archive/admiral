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

import TemplatesStore from 'stores/TemplatesStore';
import { TemplateActions } from 'actions/Actions';
import services from 'core/services';
import constants from 'core/constants';
import routes from 'core/routes';
import TemplatesMockData from 'unit/helpers/templates/TemplatesMockData';

describe("TemplatesStore test", function() {
  const BRIDGE_NETWORK_DESCRIPTION = {
    documentSelfLink: '/system-networks-link/bridge', name: 'bridge' 
  };

  function getItem(items, documentSelfLink) {
    for (var i = 0; i < items.length; i++) {
      if (items[i].documentSelfLink === documentSelfLink) {
        return items[i];
      }
    }
  };

  var totalNumberShownItems;
  var numberShownItemsCompositeDetails;

  var waitForData = function(conditionFn) {
    return testUtils.waitForListenable(TemplatesStore, conditionFn);
  };

  describe("Template with network", function() {

    beforeEach(function() {

      hasher.changed.active = false; //disable changed signal
      hasher.setHash(""); //set hash without dispatching changed signal
      hasher.changed.active = true; //re-enable signal
      routes.initialize();

      spyOn(services, 'loadContainerTemplate').and.callFake(function() {
        return new Promise(function(resolve, reject){
          setTimeout(function() {
            var template = JSON.parse(JSON.stringify(TemplatesMockData.TemplateWithNetworking));
            resolve(template);
          }, 0);
        });
      });

      spyOn(services, 'loadDocument').and.callFake(function(documentSelfLink) {
        return new Promise(function(resolve, reject){
          setTimeout(function() {
            if (TemplatesMockData.WordpressCD.documentSelfLink === documentSelfLink) {
              let cd = JSON.parse(JSON.stringify(TemplatesMockData.WordpressCD));
              resolve(cd);
            } else if (TemplatesMockData.MysqlCD.documentSelfLink === documentSelfLink) {
              let cd = JSON.parse(JSON.stringify(TemplatesMockData.MysqlCD));
              resolve(cd);
            } else if (TemplatesMockData.HaproxyCD.documentSelfLink === documentSelfLink) {
              let cd = JSON.parse(JSON.stringify(TemplatesMockData.HaproxyCD));
              resolve(cd);
            } else if (TemplatesMockData.Mynet.documentSelfLink === documentSelfLink) {
              let net = JSON.parse(JSON.stringify(TemplatesMockData.Mynet));
              resolve(net);
            } else {
              reject("Unknown documentSelfLink " + documentSelfLink);
            }
          }, 0);
        });
      });
      spyOn(services, 'patchDocument').and.callFake(function(documentSelfLink, patchObj) {
        return new Promise(function(resolve, reject){
          setTimeout(function() {
            let document;
            if (TemplatesMockData.WordpressCD.documentSelfLink === documentSelfLink) {
              document = JSON.parse(JSON.stringify(TemplatesMockData.WordpressCD));
            } else if (TemplatesMockData.MysqlCD.documentSelfLink === documentSelfLink) {
              document = JSON.parse(JSON.stringify(TemplatesMockData.MysqlCD));
            } else if (TemplatesMockData.HaproxyCD.documentSelfLink === documentSelfLink) {
              document = JSON.parse(JSON.stringify(TemplatesMockData.HaproxyCD));
            } else if (TemplatesMockData.Mynet.documentSelfLink === documentSelfLink) {
              document = JSON.parse(JSON.stringify(TemplatesMockData.Mynet));
            } else {
              reject("Unknown documentSelfLink " + documentSelfLink);
            }

            resolve($.extend({}, document, patchObj));
          }, 0);
        });
      });
    });

    it('should initialize network connections in model', function(done) {
      TemplateActions.openTemplateDetails(constants.TEMPLATES.TYPES.TEMPLATE, TemplatesMockData.TemplateWithNetworking.name);

      waitForData(function(data) {
        return data.selectedItemDetails.templateDetails.documentSelfLink ===
          TemplatesMockData.TemplateWithNetworking.documentSelfLink &&
          data.selectedItemDetails.templateDetails.listView.items;
      }).then(function(data) {
        var lv = data.selectedItemDetails.templateDetails.listView;
        expect(lv.networks).toEqual([
          TemplatesMockData.Mynet,
          BRIDGE_NETWORK_DESCRIPTION
        ]);

        expect(lv.networkLinks[TemplatesMockData.WordpressCD.documentSelfLink]).toEqual([
          BRIDGE_NETWORK_DESCRIPTION.documentSelfLink
        ]);

        expect(lv.networkLinks[TemplatesMockData.MysqlCD.documentSelfLink]).toEqual([
          TemplatesMockData.Mynet.documentSelfLink,
          BRIDGE_NETWORK_DESCRIPTION.documentSelfLink
        ]);

        expect(lv.networkLinks[TemplatesMockData.HaproxyCD.documentSelfLink]).toEqual([
          TemplatesMockData.Mynet.documentSelfLink
        ]);

        done();
      });
    });

    it('should update when attaching and detachin a network connection', function(done) {
      TemplateActions.openTemplateDetails(constants.TEMPLATES.TYPES.TEMPLATE, TemplatesMockData.TemplateWithNetworking.name);

      waitForData(function(data) {
        return data.selectedItemDetails.templateDetails.documentSelfLink ===
          TemplatesMockData.TemplateWithNetworking.documentSelfLink &&
          data.selectedItemDetails.templateDetails.listView.items;
      }).then(function(data) {
        TemplateActions.attachNetwork(TemplatesMockData.WordpressCD.documentSelfLink,
                                     TemplatesMockData.Mynet.documentSelfLink);

        TemplateActions.attachNetwork(TemplatesMockData.HaproxyCD.documentSelfLink,
                                     BRIDGE_NETWORK_DESCRIPTION.documentSelfLink);

        return waitForData(function(data) {
          var lv = data.selectedItemDetails.templateDetails.listView;
          var wordpress = getItem(lv.items, TemplatesMockData.WordpressCD.documentSelfLink);
          var mysql = getItem(lv.items, TemplatesMockData.MysqlCD.documentSelfLink);
          var haproxy = getItem(lv.items, TemplatesMockData.HaproxyCD.documentSelfLink);

          return wordpress.networkMode === 'bridge' && wordpress.networks['mynet'] &&
            mysql.networkMode === 'bridge' && mysql.networks['mynet'] &&
            haproxy.networkMode === 'bridge' && haproxy.networks['mynet'];
        });
      }).then(function(data) {
        var lv = data.selectedItemDetails.templateDetails.listView;
        var allNetworkLinks = [
          TemplatesMockData.Mynet.documentSelfLink,
          BRIDGE_NETWORK_DESCRIPTION.documentSelfLink
        ];

        expect(lv.networks).toEqual([
          TemplatesMockData.Mynet,
          BRIDGE_NETWORK_DESCRIPTION
        ]);

        expect(lv.networkLinks[TemplatesMockData.WordpressCD.documentSelfLink])
          .toEqual(allNetworkLinks);

        expect(lv.networkLinks[TemplatesMockData.MysqlCD.documentSelfLink])
          .toEqual(allNetworkLinks);

        expect(lv.networkLinks[TemplatesMockData.HaproxyCD.documentSelfLink])
          .toEqual(allNetworkLinks);
      }).then(function() {
        TemplateActions.detachNetwork(TemplatesMockData.WordpressCD.documentSelfLink,
                                     BRIDGE_NETWORK_DESCRIPTION.documentSelfLink);

        TemplateActions.detachNetwork(TemplatesMockData.HaproxyCD.documentSelfLink,
                                     TemplatesMockData.Mynet.documentSelfLink);

        TemplateActions.detachNetwork(TemplatesMockData.MysqlCD.documentSelfLink,
                                     BRIDGE_NETWORK_DESCRIPTION.documentSelfLink);

        TemplateActions.detachNetwork(TemplatesMockData.MysqlCD.documentSelfLink,
                                     TemplatesMockData.Mynet.documentSelfLink);

        return waitForData(function(data) {
          var lv = data.selectedItemDetails.templateDetails.listView;
          var wordpress = getItem(lv.items, TemplatesMockData.WordpressCD.documentSelfLink);
          var mysql = getItem(lv.items, TemplatesMockData.MysqlCD.documentSelfLink);
          var haproxy = getItem(lv.items, TemplatesMockData.HaproxyCD.documentSelfLink);

          return !wordpress.networkMode && wordpress.networks['mynet'] &&
            !mysql.networkMode && !mysql.networks['mynet'] &&
            haproxy.networkMode === 'bridge' && !haproxy.networks['mynet'];
        });
      }).then(function(data) {
        var lv = data.selectedItemDetails.templateDetails.listView;

        expect(lv.networkLinks[TemplatesMockData.WordpressCD.documentSelfLink])
          .toEqual([TemplatesMockData.Mynet.documentSelfLink]);

        expect(lv.networkLinks[TemplatesMockData.MysqlCD.documentSelfLink])
          .toEqual(undefined);

        expect(lv.networkLinks[TemplatesMockData.HaproxyCD.documentSelfLink])
          .toEqual([BRIDGE_NETWORK_DESCRIPTION.documentSelfLink]);

        done();
      });
    });
  });
});