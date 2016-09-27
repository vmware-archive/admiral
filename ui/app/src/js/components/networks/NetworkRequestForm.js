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

import NetworkRequestFormVue from 'NetworkRequestFormVue';
import NetworkDefinitionForm from 'components/networks/NetworkDefinitionForm'; // eslint-disable-line
import { ContainerActions } from 'actions/Actions';

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
      creatingNetwork: false
    };
  },
  methods: {
    createNetwork: function() {
      var networkForm = this.$refs.networkEditForm;
      var validationErrors = networkForm.validate();
      if (!validationErrors) {
        var network = networkForm.getNetworkDefinition();
        this.savingNetwork = true;
        ContainerActions.createNetwork(network);
      }
    }
  },
  attached: function() {
    this.unwatchModel = this.$watch('model.definitionInstance', () => {
      this.creatingNetwork = false;
    }, {immediate: true});
  },
  detached: function() {
    this.unwatchModel();
  }
});

Vue.component('network-request-form', NetworkRequestForm);

export default NetworkRequestForm;
