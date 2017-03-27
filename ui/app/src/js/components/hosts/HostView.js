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

import HostViewVue from 'components/hosts/HostViewVue.html';
import HostAddView from 'components/hosts/HostAddView'; //eslint-disable-line
import HostCreateView from 'components/hosts/HostCreateView'; //eslint-disable-line
import { HostContextToolbarActions, NavigationActions } from 'actions/Actions';
import utils from 'core/utils';
import ft from 'core/ft';

// Constants
const TAB_ID_ADD_HOST = 'AddHost';
const TAB_ID_CREATE_HOST = 'CreateHost';

// The Host View component
var HostView = Vue.extend({
  template: HostViewVue,

  props: {
    model: {
      required: true,
      type: Object,
      default: () => {
        return {
          contextView: {},
          placementZones: {},
          credentials: {},
          deploymentPolicies: {},
          certificates: {},
          shouldAcceptCertificate: null,
          validationErrors: null,
          isUpdate: false,
          id: null,
          address: null,
          placementZone: null,
          credential: null,
          connectionType: 'API'
        };
      }
    }
  },
  computed: {
    showTabAddHost: function() {
      return this.selectedTab === TAB_ID_ADD_HOST;
    },
    showTabCreateHost: function() {
      return !this.model.isUpdate && (this.selectedTab === TAB_ID_CREATE_HOST);
    },
    createHostEnabled: function() {
      return ft.isCreateHostOptionEnabled();
    },
    activeContextItem: function() {
      return this.model.contextView && this.model.contextView.activeItem &&
        this.model.contextView.activeItem.name;
    },
    contextExpanded: function() {
      return this.model.contextView && this.model.contextView.expanded;
    },
    validationErrors: function() {
      return this.model.validationErrors || {};
    }
  },

  data: function() {
    return {
      selectedTab: TAB_ID_ADD_HOST
    };
  },

  methods: {
    isApplicationEmbedded: function() {
      return utils.isApplicationEmbedded();
    },
    goBack: function() {
      NavigationActions.openHosts();
    },

    selectTab: function($event, tabId) {
      $event.preventDefault();
      $event.stopPropagation();

      let leftTab = $(this.$el).find('.view-title .left');
      let rightTab = $(this.$el).find('.view-title .right');

      if (tabId === 'leftTab') {
        // Left tab is selected
        leftTab.addClass('selected');
        rightTab.removeClass('selected');
        this.selectedTab = TAB_ID_ADD_HOST;

      } else if (tabId === 'rightTab') {
        // Right tab is selected
        rightTab.addClass('selected');
        leftTab.removeClass('selected');

        this.selectedTab = TAB_ID_CREATE_HOST;
      }
    },

    openToolbarPlacementZones: HostContextToolbarActions.openToolbarPlacementZones,
    openToolbarCredentials: HostContextToolbarActions.openToolbarCredentials,
    openToolbarCertificates: HostContextToolbarActions.openToolbarCertificates,
    openToolbarDeploymentPolicies: HostContextToolbarActions.openToolbarDeploymentPolicies,
    openToolbarEndpoints: HostContextToolbarActions.openToolbarEndpoints,
    closeToolbar: HostContextToolbarActions.closeToolbar
  }
});

Vue.component('host-view', HostView);

export default HostView;
