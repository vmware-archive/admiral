/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import NetworkRequestFormVue from 'components/networks/NetworkRequestFormVue.html';
import HostPicker from 'components/networks/HostPicker';
import { ContainerActions } from 'actions/Actions';
import utils from 'core/utils';

var NetworkRequestForm = Vue.extend({
  template: NetworkRequestFormVue,
  props: {
    model: {
      required: true,
      type: Object
    },
    fromResource: {
      type: Boolean
    }
  },
  components: {
    hostPicker: HostPicker
  },
  data: function() {
    return {
      disableCreatingNetworkButton: true
    };
  },
  attached: function() {
    var _this = this;
    $(this.$el).on('change input', function() {
      _this.toggleButtonsState();
    });

    this.unwatchModelErr = this.$watch('model.error', (err) => {
      if (err._generic) {
        this.disableCreatingNetworkButton = false;
      }
    });

    this.unwatchModel = this.$watch('model.definitionInstance', () => {
      this.disableCreatingNetworkButton = true;
    }, {immediate: true});
  },
  detached: function() {
    this.unwatchModelErr();
    this.unwatchModel();
  },
  methods: {
    createNetwork: function() {
      var networkForm = this.$refs.networkEditForm;
      var validationErrors = networkForm.validate();
      if (!validationErrors) {
        var network = networkForm.getNetworkDefinition();

        var hosts = this.$refs.hostPicker.getHosts();
        var hostIds = hosts.map(h => utils.getDocumentId(h.documentSelfLink));

        ContainerActions.createNetwork(network, hostIds);
      }
    },
    toggleButtonsState: function() {
      var networkName = this.$refs.networkEditForm.getNetworkDefinition().name;
      var selectedHost = this.$refs.hostPicker.getHosts();

      this.disableCreatingNetworkButton = !networkName
                                            || !(selectedHost && selectedHost.length > 0);
    }
  },
  events: {
    'change': function() {
      this.toggleButtonsState();
    }
  }
});

Vue.component('network-request-form', NetworkRequestForm);

export default NetworkRequestForm;
