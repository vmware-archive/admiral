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

import AppStore from 'stores/AppStore';
import { AppActions, HostActions } from 'actions/Actions';
import constants from 'core/constants';
import services from 'core/services';
import routes from 'core/routes';
import PlacementZonesStore from 'stores/PlacementZonesStore';
import CredentialsStore from 'stores/CredentialsStore';
import CertificatesStore from 'stores/CertificatesStore';

describe("AppStore test", function() {

  var waitForData = function(conditionFn) {
    return testUtils.waitForListenable(AppStore, conditionFn);
  };

  beforeEach(function() {
    spyOn(routes, 'initialize').and.callFake(function() {
    });
    spyOn(PlacementZonesStore, 'onRetrievePlacementZones').and.callFake(function() {
    });
    spyOn(CredentialsStore, 'onRetrieveCredentials').and.callFake(function() {
    });
    spyOn(CertificatesStore, 'onRetrieveCertificates').and.callFake(function() {
    });
    spyOn(services, 'loadConfigurationProperties').and.callFake(function() {
	  return new Promise(function(resolve, reject){
	    setTimeout(function() {
	      resolve([]);
	    }, 0);
	  });
	});
    spyOn(services, 'loadPopularImages').and.callFake(function() {
	  return new Promise(function(resolve, reject){
	    setTimeout(function() {
	      resolve([]);
	    }, 0);
	  });
	});
    spyOn(services, 'loadCurrentUser').and.callFake(function() {
	  return new Promise(function(resolve, reject){
	    setTimeout(function() {
	      resolve({});
	    }, 0);
	  });
	});

    AppStore.onInit();
  });

  describe("Page change actions", function() {
    it("should set data to show home view on home view action", function(done) {
        waitForData((data) => {
          return data.centerView;
        }).then((data) => {
        expect(data.centerView.name).toEqual(constants.VIEWS.HOME.name);
        done();
      });

      AppActions.openHome();
    });

    it("should set data to show add host view on open add host action", function(done) {
      waitForData((data) => {
          return !!data.centerView.data.hostAddView;
      }).then((data) => {
        expect(data.centerView.data.hostAddView).not.toBeNull();

        done();
      });

      AppActions.openHome();
      HostActions.openAddHost();
    });
  });
});