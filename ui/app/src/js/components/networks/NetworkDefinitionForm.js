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

import NetworkDefinitionFormVue from 'components/networks/NetworkDefinitionFormVue.html';
import MulticolumnInputs from 'components/common/MulticolumnInputs';
import utils from 'core/utils';
import services from 'core/services';
import constants from 'core/constants';

let constraints = {
  name: function(name) {
    if (!name || validator.trim(name).length === 0) {
      return 'errors.required';
    }
  },
  existsInTemplate: function(existsInTemplate) {
    if (existsInTemplate) {
        return 'errors.networkExists';
    }
  }
};

const NETWORK_RESULT_LIMIT = 10;
const INITIAL_FILTER = '';

function createTypeaheadSource($typeaheadHolder) {
  var timeout;
  var lastCallback;
  var source = {};

  source.fn = (q, syncCallback, asyncCallback) => {
    lastCallback = asyncCallback;
    clearTimeout(timeout);

    var promiseCallback = (result) => {

      if (result.items) {
        var networksNames = []; // names of networks
        var filteredData = {}; // networks with unique name
        var filteredDocLinks = []; // holder of unique documentLinks
        $.each(result.items, function(documentSelfLink, network) {
          if (networksNames.indexOf(network.name) === -1) {
            networksNames.push(network.name);
            filteredData[documentSelfLink] = network;
            filteredDocLinks.push(documentSelfLink);
          } else {
            // find network with duplicate name in order to increase the instances.
            for (var uniqueNetwork in filteredData) {
              if (filteredData[uniqueNetwork].name === network.name) {
                if (filteredData[uniqueNetwork].instances) {
                  filteredData[uniqueNetwork].instances += 1;
                } else {
                  filteredData[uniqueNetwork].instances = 2;
                }
              }
            }
          }
        });

        var documentLinks = filteredDocLinks || [];

        result.items = documentLinks.map((link) => {
          return filteredData[link];
        });
      }

      if (lastCallback === asyncCallback) {
        source.lastResult = result;
        asyncCallback(result.items);
        $typeaheadHolder.removeClass('loading');
      }
    };

    $typeaheadHolder.addClass('loading');

    var searchContainerNetworks =
                        services.searchContainerNetworks(q || INITIAL_FILTER, NETWORK_RESULT_LIMIT);
    if (!q) {
      searchContainerNetworks.then(promiseCallback);
    } else {
      timeout = setTimeout(() => {
        searchContainerNetworks.then(promiseCallback);
      }, 300);
    }
  };

  return source;
}

function checkForDuplicateNames(el, network) {
  var networkName = network.name;
  el.each(function() {
    if ($(this).text() === networkName) {
        network.existsInTemplate = true;
    }
  });
}

var NetworkDefinitionForm = Vue.extend({
  template: NetworkDefinitionFormVue,
  props: {
    model: {
      required: false,
      type: Object
    },
    allowExisting: {
      type: Boolean
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

      var existingTemplateNetworks = $(this.$root.$el).find('.network-label');

      if (this.existingNetwork) {
        network.name = this.$networksSearch.typeahead('val');
      } else {
        network.name = $(this.$el).find('.network-name .form-control').val();

        if (this.showAdvanced) {

          var customProperties = this.customProperties.getData();

          if (customProperties && customProperties.length) {
              network.ipam = network.ipam || {};
              var customPropertiesLength = customProperties.length;
              var properties = {};
              for (var i = 0; i < customPropertiesLength; i++) {
                if (customProperties[i].key === 'containers.network.driver') {
                    network.driver = customProperties[i].value;
                } else if (customProperties[i].key === 'containers.ipam.driver') {
                        network.ipam = network.ipam || {};
                        network.ipam.driver = customProperties[i].value;
                } else {
                    properties[customProperties[i].key] = customProperties[i].value;
                }
              }
              network.customProperties = properties;
          }


          var ipamConfigData = this.ipamConfigEditor.getData();
          if (ipamConfigData && ipamConfigData.length) {
            network.ipam = network.ipam || {};
            network.ipam.config = ipamConfigData;
          }
        }
      }

      if (existingTemplateNetworks && existingTemplateNetworks.length &&
          !(this.model && this.model.name === network.name)) {
           checkForDuplicateNames(existingTemplateNetworks, network);
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

      var networkName = $(this.$el).find('.network-name');
      utils.applyValidationError(networkName, errors.name);
      utils.applyValidationError(networkName, errors.existsInTemplate);

      var imageSearch = $(this.$el).find('.network-name-search');
      utils.applyValidationError(imageSearch, errors.name);
      utils.applyValidationError(imageSearch, errors.existsInTemplate);
    }
  },
  attached: function() {
    var _this = this;
    this.ipamConfigEditor = new MulticolumnInputs(
      $(this.$el).find('.ipam-config .form-control'), {
        subnet: {
          header: i18n.t('app.template.details.editNetwork.ipamConfigSubnet'),
          placeholder: i18n.t('app.template.details.editNetwork.ipamConfigSubnetHint')
        },
        'ipRange': {
          header: i18n.t('app.template.details.editNetwork.ipamConfigIPRange'),
          placeholder: i18n.t('app.template.details.editNetwork.ipamConfigIPRangeHint')
        },
        gateway: {
          header: i18n.t('app.template.details.editNetwork.ipamConfigGateway'),
          placeholder: i18n.t('app.template.details.editNetwork.ipamConfigGatewayHint')
        }
      }
    );

   this.customProperties = new MulticolumnInputs(
      $(this.$el).find('.custom-properties .form-control'), {
        key: {
          header: i18n.t('customProperties.name'),
          placeholder: i18n.t('customProperties.nameHint')
        },
        value: {
          header: i18n.t('customProperties.value'),
          placeholder: i18n.t('customProperties.valueHint')
        }
      }
    );

    this.$networksSearch = $(this.$el).find('.network-name-search .search-input input');

    $(this.$networksSearch).on('change input', function() {
      toggleButtonsState.call(_this);
    });

    $(this.$el).find('.network-name').on('change input', function() {
      toggleButtonsState.call(_this);
    });

    var typeaheadSource = createTypeaheadSource(
                                    $(this.$el).find('.network-name-search .search-input'));

    this.$networksSearch.typeahead({
      minLength: 0
    }, {
      name: 'networks-search',
      limit: NETWORK_RESULT_LIMIT,
      source: typeaheadSource.fn,
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
            if (context.instances) {
                suffix += ' <span class="network-search-item-secondary">(' + context.instances
                  + ' ' + i18n.t('app.template.details.editNetwork.showingInstances') + ')</span>';
            }
          }
          root = name.substring(start, end);

          return `
            <div>
              <div class="network-search-item-primary">
                ${prefix}<strong>${root}</strong>${suffix}
              </div>
            </div>`;
        },
        footer: function(q) {
          if (q.suggestions && q.suggestions.length > 0 && typeaheadSource.lastResult) {
             var i18nOption = {
              count: q.suggestions.length,
              totalCount: typeaheadSource.lastResult.totalCount
            };
            var label = i18n.t('app.template.details.editNetwork.showingCount', i18nOption);
            return `<div class="tt-options-hint">${label}</div>`;
          }
        },
        notFound: function() {
          var label = i18n.t('app.template.details.editNetwork.noResults');
          return `<div class="tt-options-hint">${label}</div>`;
        }
      }
    }).on('typeahead:selected', function() {
      toggleButtonsState.call(_this);
    });

    this.unwatchModel = this.$watch('model', (network) => {
      if (network) {
        $(this.$el).find('.network-name .form-control').val(network.name);

        this.$networksSearch.typeahead('val', network.name);

        this.existingNetwork = !!network.external;
        this.hasAdvancedSettings = !!(network.driver || network.customProperties ||
                                    (network.ipam && (network.ipam.config || network.ipam.driver)));

        var ipam = network.ipam || {};
        var ipamConfig = ipam.config || [];

        this.ipamConfigEditor.setData(ipamConfig);

        if (network.driver) {
          if (network.customProperties == null) {
            network.customProperties = {};
          }
          network.customProperties['containers.network.driver'] = network.driver;
        }

        if (network.ipam && network.ipam.driver) {
          if (network.customProperties == null) {
            network.customProperties = {};
          }
          network.customProperties['containers.ipam.driver'] = network.ipam.driver;
        }

        if (network.customProperties) {
          var properties = [];

          for (var key in network.customProperties) {
            if (network.customProperties.hasOwnProperty(key)) {
              var value = network.customProperties[key];
              var keyValuePair = {
                'key': key,
                'value': value
              };
              properties.push(keyValuePair);
            }
          }

          this.customProperties.setData(properties);
        } else {
          this.customProperties.setData([]);
        }

        var alertMessage = (network.error) ? network.error._generic : network.error;
        if (alertMessage) {
          this.$dispatch('container-form-alert', alertMessage, constants.ALERTS.TYPE.FAIL);
        }
      }
    }, {immediate: true});

    this.unwatchExistingNetwork = this.$watch('existingNetwork', (existingNetwork) => {
      if (existingNetwork) {
        let name = $(this.$el).find('.network-name .form-control').val();
        this.$networksSearch.typeahead('val', name);
        $(this.$el).find('.network-name .form-control').val('');
      } else {
        let name = this.$networksSearch.typeahead('val');
        $(this.$el).find('.network-name .form-control').val(name);
        this.$networksSearch.typeahead('val', '');
      }
      toggleButtonsState.call(_this);
    });
  },
  detached: function() {
    this.unwatchModel();
    this.unwatchExistingNetwork();
  }
});

var toggleButtonsState = function() {
  var networkNameInput = $(this.$el).find('.network-name input').val();
  var networkSearchNameInput = this.$networksSearch.typeahead('val');

  if ((!this.existingNetwork && networkNameInput) ||
      (this.existingNetwork && networkSearchNameInput)) {
    this.$dispatch('disableNetworkSaveButton', false);
  } else {
    this.$dispatch('disableNetworkSaveButton', true);
  }
};

Vue.component('network-definition-form', NetworkDefinitionForm);

export default NetworkDefinitionForm;
