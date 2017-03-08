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

import PlacementsStore from 'stores/PlacementsStore';
import ProfilesStore from 'stores/ProfilesStore';
import EndpointsStore from 'stores/EndpointsStore';
import MachinesStore from 'stores/MachinesStore';
import ComputeStore from 'stores/ComputeStore';
import * as actions from 'actions/Actions';
import ft from 'core/ft';
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

  EndpointsStore.listen((data) => {
    if (this.data.centerView && this.data.centerView.name === constants.VIEWS.ENDPOINTS.name) {
      this.setInData(['centerView', 'data'], data);
      this.emitChange();
    } else if (this.data.centerView && this.data.centerView.name === constants.VIEWS.HOME.name) {
      // While we were showing the home view, endpoints have been loaded, this mean we can safely
      // switch to endpoints view.
      if (data.items && data.items.length > 0) {
        actions.NavigationActions.openEndpointsSilently();
        this.setInData(['endpointsTransition'], true);
        this.emitChange();
        Vue.nextTick(() => {
          this.setInData(['endpointsTransition'], false);
          updateCenterViewIfNeeded.call(this, constants.VIEWS.ENDPOINTS.name, data);
          updateSideView.call(this, constants.VIEWS.ENDPOINTS.name);
        });
      } else if (data.hostAddView) {
        this.setInData(['centerView', 'data', 'hostAddView'], data.hostAddView);
        this.emitChange();
      }
    }

  });

  ProfilesStore.listen((data) => {
    if (this.data.centerView && this.data.centerView.name === constants.VIEWS.PROFILES.name) {
      this.setInData(['centerView', 'data'], data);
      this.emitChange();
    }
  });
  PlacementsStore.listen((data) => {
    if (this.data.centerView && this.data.centerView.name === constants.VIEWS.PLACEMENTS.name) {
      this.setInData(['centerView', 'data'], data);
      this.emitChange();
    }
  });
  MachinesStore.listen((data) => {
    if (this.data.centerView && this.data.centerView.name
                                              === constants.VIEWS.RESOURCES.VIEWS.MACHINES.name) {
      this.setInData(['centerView', 'data'], data);
      this.emitChange();
    }
  });
  ComputeStore.listen((data) => {
    if (this.data.centerView && this.data.centerView.name === constants.VIEWS.COMPUTE.name) {
      this.setInData(['centerView', 'data'], data);
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
    updateCenterViewIfNeeded.call(this, constants.VIEWS.HOME.name, {
      isContextAwareHelpAvailable: ft.isContextAwareHelpAvailable()
    }, true);
    updateSideView.call(this, null);

    if (firstLoad) {
      // We immediately initialize and load the home page. In the mean time start loading the hosts,
      // if there are any we will close the home page and open the hosts
      // actions.HostActions.openHosts();
      actions.EndpointsActions.retrieveEndpoints();
    }
  },

  onOpenView: function(viewName) {
    updateCenterViewIfNeeded.call(this, viewName, {});
    updateSideView.call(this, viewName);
  },

  onOpenToolbarEventLogs: function(highlightedItemLink) {
    if (this.data.centerView &&
        this.data.centerView.name === constants.VIEWS.RESOURCES.VIEWS.MACHINES.name) {
      actions.MachinesContextToolbarActions.openToolbarEventLogs(highlightedItemLink);
      return;
    }
  }
});

if (utils.isDebugModeEnabled()) {
  window._getData = function() {
    return AppComputeStore.getData();
  };
}

export default AppComputeStore;
