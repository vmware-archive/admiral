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

import HostsStore from 'stores/HostsStore';
import PlacementsStore from 'stores/PlacementsStore';
import TemplatesStore from 'stores/TemplatesStore';
import ContainersStore from 'stores/ContainersStore';
import * as actions from 'actions/Actions';
import routes from 'core/routes';
import constants from 'core/constants';
import utils from 'core/utils';
import ft from 'core/ft';
import docs from 'core/docs';
import services from 'core/services';
import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';
import recommendedImages from 'core/recommendedImages';

let AppStore;

var isSameView = function(newViewName, oldViewName) {
  return newViewName === oldViewName ||
    utils.showResourcesView(newViewName) && utils.showResourcesView(oldViewName);
};

var updateCenterViewIfNeeded = function(viewName, data, force) {
  var oldViewName = this.data.centerView && this.data.centerView.name;

  if (!force && isSameView(viewName, oldViewName)) {
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
  HostsStore.listen((hostsData) => {
    // When the HostsStore data changes, we update only if the current view is interested
    // in this data.
    if (this.data.centerView && this.data.centerView.name === constants.VIEWS.HOSTS.name) {
      this.setInData(['centerView', 'data'], hostsData);
      this.emitChange();
    } else if (this.data.centerView && this.data.centerView.name === constants.VIEWS.HOME.name) {
      // While we were showing the home view, hosts have been loaded, this mean we can safely
      // switch to hosts view.
      if (hostsData.listView && hostsData.listView.items &&
          hostsData.listView.items.length > 0) {
        actions.NavigationActions.openHostsSilently();
        this.setInData(['hostsTransition'], true);
        this.emitChange();
        Vue.nextTick(() => {
          this.setInData(['hostsTransition'], false);
          updateCenterViewIfNeeded.call(this, constants.VIEWS.HOSTS.name, hostsData);
          updateSideView.call(this, constants.VIEWS.HOSTS.name);
        });
      } else if (hostsData.hostAddView) {
        this.setInData(['centerView', 'data', 'hostAddView'], hostsData.hostAddView);
        this.emitChange();
      }
    }
  });

  PlacementsStore.listen((quotesData) => {
    if (this.data.centerView && this.data.centerView.name === constants.VIEWS.PLACEMENTS.name) {
      this.setInData(['centerView', 'data'], quotesData);
      this.emitChange();
    }
  });

  TemplatesStore.listen((templatesData) => {
    if (this.data.centerView && this.data.centerView.name === constants.VIEWS.TEMPLATES.name) {
      this.setInData(['centerView', 'data'], templatesData);
      this.emitChange();
    }
  });

  ContainersStore.listen((containersData) => {
    if (this.data.centerView && utils.showResourcesView(this.data.centerView.name)) {
      this.setInData(['centerView', 'data'], containersData);
      this.emitChange();
    }
  });
};

AppStore = Reflux.createStore({
  mixins: [CrudStoreMixin],
  listenables: [actions.AppActions],

  init: function() {
  },

  onInit: function() {
    initializeStoreListeners.call(this);
    var loadRecommendedImagesPromise = recommendedImages.loadImages().catch((err) => {
      console.warn('Error when loading the recommended images! Error: ' + err);
    });

    var promises = [loadRecommendedImagesPromise];

    if (!utils.isApplicationEmbedded()) {
      var loadCurrentUsersPromise = services.loadCurrentUser().then((user) => {
        this.setInData(['currentUser'], user);
        this.emitChange();
      }).catch((err) => {
          console.warn('Error when loading the current user! Error: ' + err);
      });
      promises.push(loadCurrentUsersPromise);
    }

    Promise.all(promises).then(() => {
      routes.initialize(true);
    });

    if (ft.isContextAwareHelpEnabled()) {
      docs.checkIfAvailable(() => {
        var isContextAwareHelpAvailable = ft.isContextAwareHelpAvailable();

        if (this.data.centerView && this.data.centerView.name === constants.VIEWS.HOME.name) {
          this.setInData(['centerView', 'data', 'isContextAwareHelpAvailable'],
                         isContextAwareHelpAvailable);
        }

        this.setInData(['isContextAwareHelpAvailable'], isContextAwareHelpAvailable);
        this.emitChange();
      });
    }
  },

  onOpenHome: function() {
    var firstLoad = !this.data.centerView;
    var isContextAwareHelpAvailable = ft.isContextAwareHelpAvailable();
    updateCenterViewIfNeeded.call(this, constants.VIEWS.HOME.name, {
      isContextAwareHelpAvailable: isContextAwareHelpAvailable
    }, true);
    updateSideView.call(this, null);

    if (firstLoad) {
      // We immediately initialize and load the home page. In the mean time start loading the hosts,
      // if there are any we will close the home page and open the hosts
      actions.HostActions.openHosts();
    }
  },

  onOpenView: function(viewName, viewRoute) {
    var data = {};
    if (viewRoute) {
      data.viewRoute = viewRoute;
    }
    updateCenterViewIfNeeded.call(this, viewName, data);
    updateSideView.call(this, viewName);
  },

  onOpenToolbarEventLogs: function(highlightedItemLink) {
    if (this.data.centerView && this.data.centerView.name === constants.VIEWS.HOSTS.name) {
      actions.HostsContextToolbarActions.openToolbarEventLogs(highlightedItemLink);
      return;
    }

    if (this.data.centerView && this.data.centerView.name === constants.VIEWS.TEMPLATES.name) {
      actions.TemplatesContextToolbarActions.openToolbarEventLogs(highlightedItemLink);
      return;
    }

    if (this.data.centerView && utils.showResourcesView(this.data.centerView.name)) {
      actions.ContainersContextToolbarActions.openToolbarEventLogs(highlightedItemLink);
      return;
    }
  }
});

if (utils.isDebugModeEnabled()) {
  window._getData = function() {
    return AppStore.getData();
  };
}

export default AppStore;
