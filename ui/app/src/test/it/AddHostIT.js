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

import App from 'components/App';
import Store from 'stores/AppStore';
import services from 'core/services';

describe('Add host integration test', function() {
  var container;

  beforeEach(function() {
    container = $('<div>');
    $('body').append(container);
  });

  afterEach(function() {
    container.remove();

    hasher.changed.active = false; //disable changed signal
    hasher.setHash(''); //set hash without dispatching changed signal
    hasher.changed.active = true; //re-enable signal
  });

  describe('As new user', function() {
    beforeEach(function() {
      spyOn(services, 'loadHosts').and.callFake(function() {
        return new Promise(function(resolve) {
          // Return empty result for hosts, i.e. new user
          resolve({});
        });
      });
    });

    it('it should show home page by default', function(done) {
      var app = new App(container);
      Store.listen(app.setData, app);
      Store.onInit();

      testUtils.waitFor(function() {
        return container.find('.homepage .welcome-title').length === 1;
      }).then(function() {
        expect(location.hash).toBe('#/home');
        done();
      });
    });

    xit('it should open add host page from home page', function(done) {
      var app = new App(container);
      Store.listen(app.setData, app);
      Store.onInit();

      testUtils.waitFor(function() {
        return container.find('.homepage .addHost-btn').length === 1;
      }).then(function() {
        container.find('.addHost-btn').trigger('click');

        testUtils.waitFor(function() {
          return container.find('.center-view .host-view').length === 1;
        }).then(function() {
          expect(location.hash).toBe('#/home/newHost');
          done();
        });
      });
    });
  });
});
