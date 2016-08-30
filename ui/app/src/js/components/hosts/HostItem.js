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

import HostItemVue from 'HostItemVue';
import DeleteConfirmationSupportMixin from 'components/common/DeleteConfirmationSupportMixin'; //eslint-disable-line
import VueDeleteItemConfirmation from 'components/common/VueDeleteItemConfirmation'; //eslint-disable-line
import { HostActions, NavigationActions } from 'actions/Actions';
import constants from 'core/constants';
import utils from 'core/utils';

var HostItem = Vue.extend({
  template: HostItemVue,
  mixins: [DeleteConfirmationSupportMixin],
  props: {
    model: {required: true}
  },
  computed: {
    powerStateOn: function() {
      return this.model.powerState === constants.STATES.ON;
    },

    hostName: function() {
      return utils.getHostName(this.model);
    }
  },
  methods: {
    operationSupported: function(op) {

      return utils.operationSupportedHost(op, this.model);
    },
    startHost: function(event) {
      event.preventDefault();
      // currently this host day 2 action is not supported
    },
    stopHost: function(event) {
      event.preventDefault();
      // currently this host day 2 action is not supported
    },
    editHost: function(event) {
      event.preventDefault();

      NavigationActions.editHost(this.model.selfLinkId);
    },
    disableHost: function(event) {
      event.preventDefault();

      HostActions.disableHost(this.model.selfLinkId);
    },
    enableHost: function(event) {
      event.preventDefault();

      HostActions.enableHost(this.model.selfLinkId);
    },

    removeHost: function() {
      this.confirmRemoval(HostActions.removeHost, [this.model.selfLinkId]);
    },

    hostStateMessage: function(state) {
      return i18n.t('state.' + state);
    },

    hostPercentageLevel: function(percentage) {
      if (percentage < 50) {
        return 'success';
      } else if (percentage < 80) {
        return 'warning';
      } else {
        return 'danger';
      }
    },

    showHostsPerResourcePool: function(event) {
      event.preventDefault();

      var queryOptions = {
        resourcePool: this.model.resourcePoolDocumentId
      };

      NavigationActions.openHosts(queryOptions);
    }
  }
});

Vue.component('host-grid-item', HostItem);

export default HostItem;
