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

import services from 'core/services';
import InlineEditableListFactory from 'components/common/InlineEditableListFactory';
import ResourcePoolsStore from 'stores/ResourcePoolsStore';
import { ResourcePoolsActions } from 'actions/Actions';

describe("Resource pools integration test", function() {
  var $container;

  var unspubscribeDataListener;
  var lastResourcePoolsData;
  var rpView;

  var createdResourcePools = [];

  beforeEach(function() {
    $container = $('<div>');
    $('body').append($container);

    var realCreate = services.createResourcePool;
    // intercepts the creation method
    spyOn(services, 'createResourcePool').and.callFake(function(params) {
      return new Promise(function(resolve, reject){
        realCreate.call(null, params)
          .then(function(createdResourcePool) {
            createdResourcePools.push(createdResourcePool);
            resolve(createdResourcePool);
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
    createdResourcePools.forEach(function(rp) {
      deletionPromises.push(services.deleteResourcePool(rp));
    });

    Promise.all(deletionPromises).then(done);
  });

  it("it should create a resource pool", function(done) {
    ResourcePoolsActions.retrieveResourcePools();
    // Reset the data and wait for new data to be set
    lastResourcePoolsData = null;

    testUtils.waitFor(function() {
      return lastResourcePoolsData && lastResourcePoolsData.items && lastResourcePoolsData.items.length >= 0;
    }).then(function() {

      // Open up the editor
      $container.find('.new-item').trigger('click');

      lastResourcePoolsData = null;
      return testUtils.waitFor(function() {
        return lastResourcePoolsData && lastResourcePoolsData.editingItemData;
      })
    }).then(function() {
      fillName($container, 'RP-test');

      // Initially there should be one name/value input
      var $customPropertyInputs = $container.find('.resourcePoolEdit .custom-properties .multicolumn-input');
      expect($customPropertyInputs.length).toEqual(1);

      addCustomProperty($container, 'custom prop 1', 'value 1');

      $customPropertyInputs = $container.find('.resourcePoolEdit .custom-properties .multicolumn-input');
      expect($customPropertyInputs.length).toEqual(2);

      addCustomProperty($container, 'custom prop 2', 'value 2');

      // Trigger creation
      $container.find('.resourcePoolEdit .resourcePoolEdit-save').trigger('click');

      // Reset the data and wait for new data to be set
      lastResourcePoolsData = null;

      testUtils.waitFor(function() {
        return lastResourcePoolsData && lastResourcePoolsData.newItem;
      }).then(function() {
        expect(lastResourcePoolsData.newItem.name).toEqual("RP-test");

        var expectedCustomProperties = {
          'custom prop 1': 'value 1',
          'custom prop 2': 'value 2'
        };
        expect(JSON.stringify(lastResourcePoolsData.newItem.customProperties)).toEqual(JSON.stringify(expectedCustomProperties));

        expect(lastResourcePoolsData.items).toContain(lastResourcePoolsData.newItem);

        done();
      });
    });
  });

  // Disabled as update uses HTTP PATCH, which seems PhantomJS 1.x has issues with, does not send body.
  // See issue here https://github.com/ariya/phantomjs/issues/11384
  // Chrome passes well, but on the CI we rely on Phantom.
  xit("it should edit a resource pool", function(done) {

    var testResourcePool = {
      id: 'rp-edit-test',
      name: 'rp-edit-test',
      minCpuCount: 1,
      minMemoryBytes: 4
    };
    services.createResourcePool(testResourcePool)
    .then(function() {
      ResourcePoolsActions.retrieveResourcePools();
      // Reset the data and wait for new data to be set
      lastResourcePoolsData = null;

      return testUtils.waitFor(function() {
        return lastResourcePoolsData && lastResourcePoolsData.items && lastResourcePoolsData.items.length >= 0;
      });
    }).then(function() {
      var $itemChild = $container.find('.item td[title="rp-edit-test"]');
      var $item = $itemChild.closest('.item');
      $item.find('.item-edit').trigger('click');

      lastResourcePoolsData = null;
      return testUtils.waitFor(function() {
        return lastResourcePoolsData && lastResourcePoolsData.editingItemData;
      })
    }).then(function() {
      fillName($container, 'rp-edit-test-updated');
      addCustomProperty($container, 'custom prop 1', 'value 1');

        // Trigger update
      $container.find('.resourcePoolEdit .resourcePoolEdit-save').trigger('click');

      // Reset the data and wait for new data to be set
      lastResourcePoolsData = null;

      return testUtils.waitFor(function() {
        return lastResourcePoolsData && lastResourcePoolsData.updatedItem;
      })
    }).then(function() {
      expect(lastResourcePoolsData.updatedItem.id).toEqual(testResourcePool.id);
      expect(lastResourcePoolsData.updatedItem.name).toEqual('rp-edit-test-updated');

      var expectedCustomProperties = {
        'custom prop 1': 'value 1'
      };
      expect(JSON.stringify(lastResourcePoolsData.updatedItem.customProperties)).toEqual(JSON.stringify(expectedCustomProperties));

      done();
    });
  });

  it("it should delete a resource pool", function(done) {
    var testResourcePool = {
      id: 'rp-edit-test',
      name: 'rp-edit-test',
      minCpuCount: 1,
      minMemoryBytes: 4
    };
    services.createResourcePool(testResourcePool)
    .then(function() {
      ResourcePoolsActions.retrieveResourcePools();
      // Reset the data and wait for new data to be set
      lastResourcePoolsData = null;

      return testUtils.waitFor(function() {
        return lastResourcePoolsData && lastResourcePoolsData.items && lastResourcePoolsData.items.length >= 0;
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
        return lastResourcePoolsData && lastResourcePoolsData.items && lastResourcePoolsData.items.length >= 0;
      })
    }).then(function() {
      for (var i = 0; i < lastResourcePoolsData.items.length; i++) {
        if (lastResourcePoolsData.items[i].id === testResourcePool.id) {
          done.fail("Item with id " + testResourcePool.id + " was expected to be deleted");
          return;
        }
      }

      services.loadResourcePool(testResourcePool.id)
        .then(function(){
          done.fail("Load resource pool was expected to fail with 404");
        }).catch(done);
    });
  });
});

var fillName = function($container, name) {
  // Fill the name value
  $container.find('.resourcePoolEdit .resourcePoolEdit-properties > .name-input').val(name);
};

var addCustomProperty = function($container, key, value) {
  var $customPropertyInputs = $container.find('.resourcePoolEdit .custom-properties .multicolumn-input');
  var $lastInput = $($customPropertyInputs[$customPropertyInputs.length - 1]);

  // Fill custom properties
  $lastInput.find('.inline-input[name="name"]').val(key);
  $lastInput.find('.inline-input[name="value"]').val(value);

  // Add a new custom property
  $lastInput.find('.multicolumn-input-add').trigger('click');
};