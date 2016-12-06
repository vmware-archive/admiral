/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the 'License').
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import services from 'core/services';
import InlineEditableListFactory from 'components/common/InlineEditableListFactory';
import PlacementZonesStore from 'stores/PlacementZonesStore';
import { PlacementZonesActions } from 'actions/Actions';

describe('Resource pools integration test', function() {
  var $container;

  var unspubscribeDataListener;
  var lastPlacementZonesData;
  var rpView;

  var createdConfigs = [];

  beforeEach(function() {
    $container = $('<div>');
    $('body').append($container);

    var realCreate = services.createPlacementZone;
    // intercepts the creation method
    spyOn(services, 'createPlacementZone').and.callFake(function(params) {
      return new Promise(function(resolve, reject) {
        realCreate.call(null, params)
          .then(function(createdConfig) {
            createdConfigs.push(createdConfig);
            resolve(createdConfig);
          }).catch(reject);
      });
    });

    // Initialize and show the view
    rpView = InlineEditableListFactory.createPlacementZonesList($container);

    unspubscribeDataListener = PlacementZonesStore.listen(function(placementZonesData) {
      lastPlacementZonesData = placementZonesData;
      rpView.setData(placementZonesData);
    });
  });

  afterEach(function(done) {
    $container.remove();

    unspubscribeDataListener();
    lastPlacementZonesData = null;

    var deletionPromises = [];
    createdConfigs.forEach(function(config) {
      deletionPromises.push(services.deletePlacementZone(config));
    });

    Promise.all(deletionPromises).then(done);
  });

  it('it should create a resource pool', function(done) {
    PlacementZonesActions.retrievePlacementZones();
    // Reset the data and wait for new data to be set
    lastPlacementZonesData = null;

    testUtils.waitFor(function() {
      return lastPlacementZonesData && lastPlacementZonesData.items &&
          lastPlacementZonesData.items.length >= 0;
    }).then(function() {

      // Open up the editor
      $container.find('.new-item').trigger('click');

      lastPlacementZonesData = null;
      return testUtils.waitFor(function() {
        return lastPlacementZonesData && lastPlacementZonesData.editingItemData;
      });
    }).then(function() {
      fillName($container, 'RP-test');

      // Trigger creation
      $container.find('.placementZoneEdit .placementZoneEdit-save').trigger('click');

      // Reset the data and wait for new data to be set
      lastPlacementZonesData = null;

      testUtils.waitFor(function() {
        return lastPlacementZonesData && lastPlacementZonesData.newItem;
      }).then(function() {
        expect(lastPlacementZonesData.newItem.resourcePoolState.name).toEqual('RP-test');
        expect(lastPlacementZonesData.items).toContain(lastPlacementZonesData.newItem);

        done();
      });
    });
  });

  // Disabled as update uses HTTP PATCH, which seems PhantomJS 1.x has
  // issues with, does not send body.
  // See issue here https://github.com/ariya/phantomjs/issues/11384
  // Chrome passes well, but on the CI we rely on Phantom.
  xit('it should edit a resource pool', function(done) {

    var testConfig = {
      resourcePoolState: {
        id: 'rp-edit-test',
        name: 'rp-edit-test',
        minCpuCount: 1,
        minMemoryBytes: 4
      }
    };

    services.createPlacementZone(testConfig).then(function() {
      PlacementZonesActions.retrievePlacementZones();
      // Reset the data and wait for new data to be set
      lastPlacementZonesData = null;

      return testUtils.waitFor(function() {
        return lastPlacementZonesData && lastPlacementZonesData.items &&
            lastPlacementZonesData.items.length >= 0;
      });
    }).then(function() {
      var $itemChild = $container.find('.item td[title="rp-edit-test"]');
      var $item = $itemChild.closest('.item');
      $item.find('.item-edit').trigger('click');

      lastPlacementZonesData = null;
      return testUtils.waitFor(function() {
        return lastPlacementZonesData && lastPlacementZonesData.editingItemData;
      });
    }).then(function() {
      fillName($container, 'rp-edit-test-updated');

        // Trigger update
      $container.find('.placementZoneEdit .placementZoneEdit-save').trigger('click');

      // Reset the data and wait for new data to be set
      lastPlacementZonesData = null;

      return testUtils.waitFor(function() {
        return lastPlacementZonesData && lastPlacementZonesData.updatedItem;
      });
    }).then(function() {
      expect(lastPlacementZonesData.updatedItem.resourcePoolState.id).toEqual(
          testConfig.resourcePoolState.id);
      expect(lastPlacementZonesData.updatedItem.resourcePoolState.name).toEqual(
          'rp-edit-test-updated');

      done();
    });
  });

  it('it should delete a resource pool', function(done) {
    var testConfig = {
      resourcePoolState: {
        id: 'rp-edit-test',
        name: 'rp-edit-test',
        minCpuCount: 1,
        minMemoryBytes: 4
      }
    };
    var createdTestConfig;

    services.createPlacementZone(testConfig).then(function(responseBody) {
      createdTestConfig = responseBody;

      PlacementZonesActions.retrievePlacementZones();
      // Reset the data and wait for new data to be set
      lastPlacementZonesData = null;

      return testUtils.waitFor(function() {
        return lastPlacementZonesData && lastPlacementZonesData.items &&
            lastPlacementZonesData.items.length >= 0;
      });
    }).then(function() {
      var $itemChild = $container.find('.item td[title="rp-edit-test"]');
      var $item = $itemChild.closest('.item');
      $item.find('.item-delete').trigger('click');
      // Need to confirm deletion
      $item.find('.delete-inline-item-confirmation-confirm').trigger('click');

      // Reset the data and wait for new data to be set
      lastPlacementZonesData = null;

      return testUtils.waitFor(function() {
        return lastPlacementZonesData && lastPlacementZonesData.items &&
            lastPlacementZonesData.items.length >= 0;
      });
    }).then(function() {
      for (var i = 0; i < lastPlacementZonesData.items.length; i++) {
        if (lastPlacementZonesData.items[i].resourcePoolState.id === testConfig.id) {
          done.fail('Item with id ' + testConfig.id + ' was expected to be deleted');
          return;
        }
      }

      services.loadPlacementZone(createdTestConfig.resourcePoolState.documentSelfLink)
        .then(function() {
          done.fail('Load resource pool was expected to fail with 404');
        }).catch(function(e) {
          if (e.status !== 404) {
            done.fail('Load resource pool was expected to fail with 404 but failed with ' + e.status);
          } else {
            done();
          }
        });
    });
  });
});

var fillName = function($container, name) {
  // Fill the name value
  $container.find('.placementZoneEdit .placementZoneEdit-properties .name-input').val(name);
};
