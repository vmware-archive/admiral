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

import PoliciesStore from 'stores/PoliciesStore';
import EnvironmentsStore from 'stores/EnvironmentsStore';
import MachinesStore from 'stores/MachinesStore';
import * as actions from 'actions/Actions';
import routes from 'core/routes';
import constants from 'core/computeConstants';
import utils from 'core/utils';
import services from 'core/services';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';

let AppComputeStore;

var updateCenterViewIfNeeded = function(viewName, data, force) {
  if (!force && this.data.centerView && this.data.centerView &&
      this.data.centerView.name === viewName) {
    return;
  }

  var view = {
    name: viewName
  };

  if (data) {
    view.data = data;
  }

  this.setInData(['centerView'], view);
  this.emitChange();
};

let updateSideView = function(view) {
  if (view) {
    this.setInData(['sideView', 'currentView'], view);
  } else {
    this.setInData(['sideView'], null);
  }

  this.emitChange();
};

let initializeStoreListeners = function() {
  EnvironmentsStore.listen((data) => {
    if (this.data.centerView && this.data.centerView.name === constants.VIEWS.ENVIRONMENTS.name) {
      this.setInData(['centerView', 'data'], data);
      this.emitChange();
    }
  });
  PoliciesStore.listen((quotesData) => {
    if (this.data.centerView && this.data.centerView.name === constants.VIEWS.POLICIES.name) {
      this.setInData(['centerView', 'data'], quotesData);
      this.emitChange();
    }
  });

  MachinesStore.listen((quotesData) => {
    if (this.data.centerView && this.data.centerView.name === constants.VIEWS.MACHINES.name) {
      this.setInData(['centerView', 'data'], quotesData);
      this.emitChange();
    }
  });
};

AppComputeStore = Reflux.createStore({
  mixins: [CrudStoreMixin],
  listenables: [actions.AppActions],

  init: function() {
  },

  onInit: function() {
    initializeStoreListeners.call(this);
    if (!utils.isApplicationEmbedded()) {
      services.loadCurrentUser().then((user) => {
        this.setInData(['currentUser'], user);
        routes.initialize(true);
      }).catch(() => {
        routes.initialize(true);
      });
    } else {
      routes.initialize(true);
    }
  },

  onOpenHome: function() {
    var firstLoad = !this.data.centerView;
    updateCenterViewIfNeeded.call(this, constants.VIEWS.HOME.name, {}, true);
    updateSideView.call(this, null);

    if (firstLoad) {
      // We immediately initialize and load the home page. In the mean time start loading the hosts,
      // if there are any we will close the home page and open the hosts
      actions.HostActions.openHosts();
    }
  },

  onOpenView: function(viewName) {
    updateCenterViewIfNeeded.call(this, viewName, {});
    updateSideView.call(this, viewName);
  }
});

if (utils.isDebugModeEnabled()) {
  window._getData = function() {
    return AppComputeStore.getData();
  };
}

export default AppComputeStore;
