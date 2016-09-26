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
import utils from 'core/utils';
import services from 'core/services';

let constraints = {
  name: function(name) {
    if (!name || validator.trim(name).length === 0) {
      return 'errors.required';
    }
  }
};

const NETWORK_RESULT_LIMIT = 10;

function typeaheadSource($typeaheadHolder) {
  var timeout;
  var lastCallback;
  return (q, syncCallback, asyncCallback) => {
    lastCallback = asyncCallback;
    clearTimeout(timeout);
    if (!q) {
      asyncCallback([]);
      $typeaheadHolder.removeClass('loading');
      return;
    }

    $typeaheadHolder.addClass('loading');
    timeout = setTimeout(() => {
      services.searchNetworks(q, NETWORK_RESULT_LIMIT).then((results) => {
        if (lastCallback === asyncCallback) {
          asyncCallback(results);
          $typeaheadHolder.removeClass('loading');
        }
      });
    }, 300);
  };
}

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
      hasAdvancedSettings: false,
      existingNetwork: false
    };
  },
  computed: {
    buttonsDisabled: function() {
      return this.creatingContainer || this.savingTemplate;
    },
    showAdvanced: function() {
      return !this.existingNetwork && this.hasAdvancedSettings;
    }
  },
  methods: {
    getNetworkDefinition: function() {

      var network = {};

      if (this.existingNetwork) {
        network.name = this.$networksSearch.typeahead('val');
      } else {
        network.name = $(this.$el).find('.network-name .form-control').val();

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
      }

      network.external = this.existingNetwork;

      if (this.model) {
        network.documentSelfLink = this.model.documentSelfLink;
      }

      return network;
    },
    validate: function() {
      var definition = this.getNetworkDefinition();
      var validationErrors = utils.validate(definition, constraints);
      this.applyValidationErrors(validationErrors);
      return validationErrors;
    },

    applyValidationErrors: function(errors) {
      errors = errors || {};

      var image = $(this.$el).find('.network-name');
      utils.applyValidationError(image, errors.name);

      var imageSearch = $(this.$el).find('.network-name-search');
      utils.applyValidationError(imageSearch, errors.name);
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

    this.$networksSearch = $(this.$el).find('.network-name-search .form-control');
    this.$networksSearch.typeahead({}, {
      name: 'networks-search',
      limit: NETWORK_RESULT_LIMIT,
      source: typeaheadSource($(this.$el).find('.network-name-search')),
      display: 'name',
      templates: {
        suggestion: function(context) {
          var name = context.name || '';
          var query = context._query || '';
          var start = name.indexOf(query);
          var end = start + query.length;
          var prefix = '';
          var suffix = '';
          var root = '';
          if (start > 0) {
            prefix = name.substring(0, start);
          }
          if (end < name.length) {
            suffix = name.substring(end, name.length);
          }
          root = name.substring(start, end);

          return '<div>' + prefix + '<strong>' + root + '</strong>' + suffix
            + ' (' + context.id + ')</div>';
        }
      }
    });

    this.unwatchModel = this.$watch('model', (network) => {
      if (network) {
        $(this.$el).find('.network-name .form-control').val(network.name);

        this.$networksSearch.typeahead('val', network.name);

        this.existingNetwork = !!network.external;
        this.hasAdvancedSettings = !!(network.driver ||
          (network.ipam && (network.ipam.config || network.ipam.driver)));

        $(this.$el).find('.network-driver .form-control').val(network.driver);

        var ipam = network.ipam || {};
        $(this.$el).find('.ipam-driver .form-control').val(ipam.driver);

        var ipamConfig = ipam.config || [];
        this.ipamConfigEditor.setData(ipamConfig);

        var alertMessage = (network.error) ? network.error._generic : network.error;
        if (alertMessage) {
          this.$dispatch('container-form-alert', alertMessage);
        }
      }
    }, {immediate: true});
  },
  detached: function() {
    this.unwatchModel();
  }
});

Vue.component('network-definition-form', NetworkDefinitionForm);

export default NetworkDefinitionForm;
