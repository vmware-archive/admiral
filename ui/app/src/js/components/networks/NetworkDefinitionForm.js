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

import NetworkDefinitionFormVue from 'NetworkDefinitionFormVue';
import MulticolumnInputs from 'components/common/MulticolumnInputs';

var NetworkDefinitionForm = Vue.extend({
  template: NetworkDefinitionFormVue,
  props: {
    model: {
      required: false,
      type: Object
    }
  },
  data: function() {
    return {
      showAdvanced: false
    };
  },
  computed: {
    buttonsDisabled: function() {
      return this.creatingContainer || this.savingTemplate;
    }
  },
  methods: {
    getNetworkDefinition: function() {
      var network = {
        name: $(this.$el).find('.network-name .form-control').val()
      };

      if (this.model) {
        network.documentSelfLink = this.model.documentSelfLink;
      }

      if (this.showAdvanced) {
        network.driver = $(this.$el).find('.network-driver .form-control').val();
        var ipamConfigData = this.ipamConfigEditor.getData();
        if (ipamConfigData && ipamConfigData.length) {
          network.ipam = network.ipam || {};
          network.ipam.config = ipamConfigData;
        }

        var ipamDriver = $(this.$el).find('.ipam-driver .form-control').val();
        if (ipamDriver) {
          network.ipam = network.ipam || {};
          network.ipam.driver = ipamDriver;
        }
      }

      return network;
    }
  },
  attached: function() {
    this.ipamConfigEditor = new MulticolumnInputs(
      $(this.$el).find('.ipam-config .form-control'), {
        subnet: {
          header: 'Subnet',
          placeholder: '172.16.238.0/24'
        },
        'ipRange': {
          header: 'IP range',
          placeholder: '172.28.5.0/24'
        },
        gateway: {
          header: 'Gateway',
          placeholder: '172.16.238.1'
        }
      }
    );

    this.unwatchModel = this.$watch('model', (network) => {
      if (network) {
        $(this.$el).find('.network-name .form-control').val(network.name);

        this.showAdvanced = network.driver ||
          (network.ipam && (network.ipam.config || network.ipam.driver));

        $(this.$el).find('.network-driver .form-control').val(network.driver);

        var ipam = network.ipam || {};
        $(this.$el).find('.ipam-driver .form-control').val(ipam.driver);

        var ipamConfig = ipam.config || [];
        this.ipamConfigEditor.setData(ipamConfig);
      }
    }, {immediate: true});
  },
  detached: function() {
    this.unwatchModel();
  }
});

Vue.component('network-definition-form', NetworkDefinitionForm);

export default NetworkDefinitionForm;
