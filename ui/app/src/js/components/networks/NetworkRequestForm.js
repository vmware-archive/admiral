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

import NetworkRequestFormVue from 'components/networks/NetworkRequestFormVue.html';
import NetworkDefinitionForm from 'components/networks/NetworkDefinitionForm'; // eslint-disable-line
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
  data: function() {
    return {
      creatingNetwork: false,
      disableCreatingNetworkButton: true
    };
  },
  methods: {
    createNetwork: function() {
      var networkForm = this.$refs.networkEditForm;
      var validationErrors = networkForm.validate();
      if (!validationErrors) {
        var network = networkForm.getNetworkDefinition();

        var hosts = this.$refs.hostPicker.getHosts();
        var hostIds = hosts.map(h => utils.getDocumentId(h.documentSelfLink));

        this.savingNetwork = true;
        ContainerActions.createNetwork(network, hostIds);
      }
    }
  },
  attached: function() {
    var _this = this;
    $(this.$el).on('change input', function() {
      toggleButtonsState.call(_this);
    });

    this.unwatchModel = this.$watch('model.definitionInstance', () => {
      this.creatingNetwork = false;
      this.disableCreatingNetworkButton = true;
    }, {immediate: true});
  },
  detached: function() {
    this.unwatchModel();
  },
  components: {
    hostPicker: HostPicker
  },
  events: {
    'change': function() {
      toggleButtonsState.call(this);
    }
  }
});

var toggleButtonsState = function() {
  var networkName = this.$refs.networkEditForm.getNetworkDefinition().name;
  var host = this.$refs.hostPicker.getHosts();
  if (networkName && (host && host.length > 0)) {
    this.disableCreatingNetworkButton = false;
  } else {
    this.disableCreatingNetworkButton = true;
  }
};
Vue.component('network-request-form', NetworkRequestForm);

export default NetworkRequestForm;
