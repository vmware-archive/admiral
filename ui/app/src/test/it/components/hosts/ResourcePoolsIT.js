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
import ResourcePoolsStore from 'stores/ResourcePoolsStore';
import { ResourcePoolsActions } from 'actions/Actions';

describe('Resource pools integration test', function() {
  var $container;

  var unspubscribeDataListener;
  var lastResourcePoolsData;
  var rpView;

  var createdConfigs = [];

  beforeEach(function() {
    $container = $('<div>');
    $('body').append($container);

    var realCreate = services.createResourcePool;
    // intercepts the creation method
    spyOn(services, 'createResourcePool').and.callFake(function(params) {
      return new Promise(function(resolve, reject) {
        realCreate.call(null, params)
          .then(function(createdConfig) {
            createdConfigs.push(createdConfig);
            resolve(createdConfig);
          }).catch(reject);
      });
    });

    // Initialize and show the view
    rpView = InlineEditableListFactory.createResourcePoolsList($container);

    unspubscribeDataListener = ResourcePoolsStore.listen(function(resourcePoolsData) {
      lastResourcePoolsData = resourcePoolsData;
      rpView.setData(resourcePoolsData);
    });
  });

  afterEach(function(done) {
    $container.remove();

    unspubscribeDataListener();
    lastResourcePoolsData = null;

    var deletionPromises = [];
    createdConfigs.forEach(function(config) {
      deletionPromises.push(services.deleteResourcePool(config));
    });

    Promise.all(deletionPromises).then(done);
  });

  it('it should create a resource pool', function(done) {
    ResourcePoolsActions.retrieveResourcePools();
    // Reset the data and wait for new data to be set
    lastResourcePoolsData = null;

    testUtils.waitFor(function() {
      return lastResourcePoolsData && lastResourcePoolsData.items &&
          lastResourcePoolsData.items.length >= 0;
    }).then(function() {

      // Open up the editor
      $container.find('.new-item').trigger('click');

      lastResourcePoolsData = null;
      return testUtils.waitFor(function() {
        return lastResourcePoolsData && lastResourcePoolsData.editingItemData;
      });
    }).then(function() {
      fillName($container, 'RP-test');

      // Trigger creation
      $container.find('.resourcePoolEdit .resourcePoolEdit-save').trigger('click');

      // Reset the data and wait for new data to be set
      lastResourcePoolsData = null;

      testUtils.waitFor(function() {
        return lastResourcePoolsData && lastResourcePoolsData.newItem;
      }).then(function() {
        expect(lastResourcePoolsData.newItem.resourcePoolState.name).toEqual('RP-test');
        expect(lastResourcePoolsData.items).toContain(lastResourcePoolsData.newItem);

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

    services.createResourcePool(testConfig).then(function(a) {
      ResourcePoolsActions.retrieveResourcePools();
      // Reset the data and wait for new data to be set
      lastResourcePoolsData = null;

      return testUtils.waitFor(function() {
        return lastResourcePoolsData && lastResourcePoolsData.items &&
            lastResourcePoolsData.items.length >= 0;
      });
    }).then(function() {
      var $itemChild = $container.find('.item td[title="rp-edit-test"]');
      var $item = $itemChild.closest('.item');
      $item.find('.item-edit').trigger('click');

      lastResourcePoolsData = null;
      return testUtils.waitFor(function() {
        return lastResourcePoolsData && lastResourcePoolsData.editingItemData;
      });
    }).then(function() {
      fillName($container, 'rp-edit-test-updated');

        // Trigger update
      $container.find('.resourcePoolEdit .resourcePoolEdit-save').trigger('click');

      // Reset the data and wait for new data to be set
      lastResourcePoolsData = null;

      return testUtils.waitFor(function() {
        return lastResourcePoolsData && lastResourcePoolsData.updatedItem;
      });
    }).then(function() {
      expect(lastResourcePoolsData.updatedItem.resourcePoolState.id).toEqual(
          testConfig.resourcePoolState.id);
      expect(lastResourcePoolsData.updatedItem.resourcePoolState.name).toEqual(
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
    services.createResourcePool(testConfig).then(function() {
      ResourcePoolsActions.retrieveResourcePools();
      // Reset the data and wait for new data to be set
      lastResourcePoolsData = null;

      return testUtils.waitFor(function() {
        return lastResourcePoolsData && lastResourcePoolsData.items &&
            lastResourcePoolsData.items.length >= 0;
      });
    }).then(function() {
      var $itemChild = $container.find('.item td[title="rp-edit-test"]');
      var $item = $itemChild.closest('.item');
      $item.find('.item-delete').trigger('click');
      // Need to confirm deletion
      $item.find('.delete-inline-item-confirmation-confirm').trigger('click');

      // Reset the data and wait for new data to be set
      lastResourcePoolsData = null;

      return testUtils.waitFor(function() {
        return lastResourcePoolsData && lastResourcePoolsData.items &&
            lastResourcePoolsData.items.length >= 0;
      });
    }).then(function() {
      for (var i = 0; i < lastResourcePoolsData.items.length; i++) {
        if (lastResourcePoolsData.items[i].resourcePoolState.id === testConfig.id) {
          done.fail('Item with id ' + testConfig.id + ' was expected to be deleted');
          return;
        }
      }

      services.loadResourcePool(testConfig.resourcePoolState.id)
        .then(function() {
          done.fail('Load resource pool was expected to fail with 404');
        }).catch(done);
    });
  });
});

var fillName = function($container, name) {
  // Fill the name value
  $container.find('.resourcePoolEdit .resourcePoolEdit-properties .name-input').val(name);
};
