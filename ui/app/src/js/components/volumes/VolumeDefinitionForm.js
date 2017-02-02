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

import VolumeDefinitionFormVue from 'components/volumes/VolumeDefinitionFormVue.html';
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
      return 'errors.volumeExists';
    }
  }
};

const VOLUME_RESULT_LIMIT = 10;
const INITIAL_FILTER = '';
var initialQueryPromise;

function createTypeaheadSource($typeaheadHolder) {
  var timeout;
  var lastCallback;
  var source = {};

  source.fn = (q, syncCallback, asyncCallback) => {
    lastCallback = asyncCallback;
    clearTimeout(timeout);

    var promiseCallback = (result) => {

      if (result.items) {
        var volumesNames = []; // names of volumes
        var filteredData = {}; // volumes with unique name
        var filteredDocLinks = []; // holder of unique documentLinks

        $.each(result.items, function(documentSelfLink, volume) {
          if (volumesNames.indexOf(volume.name) === -1) {
            volumesNames.push(volume.name);
            filteredData[documentSelfLink] = volume;
            filteredDocLinks.push(documentSelfLink);
          } else {
            // find volume with duplicate name in order to increase the instances.
            for (var uniqueVolume in filteredData) {
              if (filteredData[uniqueVolume].name === volume.name) {
                if (filteredData[uniqueVolume].instances) {
                  filteredData[uniqueVolume].instances += 1;
                } else {
                  filteredData[uniqueVolume].instances = 2;
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
    if (!q) {
      initialQueryPromise.then(promiseCallback);
    } else {
      timeout = setTimeout(() => {
        services.searchContainerVolumeDescriptions(q, VOLUME_RESULT_LIMIT).then(promiseCallback);
      }, 300);
    }
  };

  return source;
}

function checkForDuplicateNames(el, volume) {
  var volumeName = volume.name;

  el.each(function() {
    if ($(this).text() === volumeName) {
      volume.existsInTemplate = true;
    }
  });
}

var VolumeDefinitionForm = Vue.extend({
  template: VolumeDefinitionFormVue,

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
      existingVolume: false
    };
  },

  computed: {
    buttonsDisabled: function() {
      return this.creatingContainer || this.savingTemplate;
    },
    showAdvanced: function() {
      return !this.existingVolume && this.hasAdvancedSettings;
    }
  },

  attached: function() {
    var _this = this;

    this.driverOptions = new MulticolumnInputs(
      $(this.$el).find('.driver-options .form-control'), {
        key: {
          header: i18n.t('app.template.details.editVolume.driverOptions.keyTitle'),
          placeholder: i18n.t('app.template.details.editVolume.driverOptions.keyHint')
        },
        value: {
          header: i18n.t('app.template.details.editVolume.driverOptions.valueTitle'),
          placeholder: i18n.t('app.template.details.editVolume.driverOptions.valueHint')
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

    this.$volumesSearch = $(this.$el).find('.volume-name-search .search-input input');

    $(this.$volumesSearch).on('change input', function() {
      toggleButtonsState.call(_this);
    });

    $(this.$el).find('.volume-name').on('change input', function() {
      toggleButtonsState.call(_this);
    });

    initialQueryPromise = services.searchContainerVolumes(INITIAL_FILTER, VOLUME_RESULT_LIMIT);

    var typeaheadSource = createTypeaheadSource(
      $(this.$el).find('.volume-name-search .search-input'));

    this.$volumesSearch.typeahead({
      minLength: 0
    }, {
      name: 'volumes-search',
      limit: VOLUME_RESULT_LIMIT,
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
              suffix += ' <span class="volume-search-item-secondary">(' + context.instances
              + ' ' + i18n.t('app.template.details.editVolume.showingInstances') + ')</span>';
            }
          }
          root = name.substring(start, end);

          return `
              <div>
                <div class="volume-search-item-primary">
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
            var label = i18n.t('app.template.details.editVolume.showingCount', i18nOption);
            return `<div class="tt-options-hint">${label}</div>`;
          }
        },
        notFound: function() {
          var label = i18n.t('app.template.details.editVolume.noResults');
          return `<div class="tt-options-hint">${label}</div>`;
        }
      }
    }).on('typeahead:selected', function() {
      toggleButtonsState.call(_this);
    });

    this.unwatchModel = this.$watch('model', (volume) => {
      if (volume) {
        $(this.$el).find('.volume-name .form-control').val(volume.name);

        this.$volumesSearch.typeahead('val', volume.name);

        this.existingVolume = !!volume.external;
        this.hasAdvancedSettings = !!(volume.options || volume.customProperties);

        this.driverOptions.setData(utils.propertiesToArray(volume.options));
        this.customProperties.setData(utils.propertiesToArray(volume.customProperties));

        var alertMessage = (volume.error) ? volume.error._generic : volume.error;
        if (alertMessage) {
          this.$dispatch('container-form-alert', alertMessage, constants.ALERTS.TYPE.FAIL);
        }
      }
    }, {immediate: true});

    this.unwatchExistingVolume = this.$watch('existingVolume', (existingVolume) => {
      if (existingVolume) {
        let name = $(this.$el).find('.volume-name .form-control').val();
        this.$volumesSearch.typeahead('val', name);
        $(this.$el).find('.volume-name .form-control').val('');
      } else {
        let name = this.$volumesSearch.typeahead('val');
        $(this.$el).find('.volume-name .form-control').val(name);
        this.$volumesSearch.typeahead('val', '');
      }
      toggleButtonsState.call(_this);
    });
  },

  detached: function() {
    this.unwatchModel();
    this.unwatchExistingVolume();
  },

  methods: {
    getVolumeDefinition: function() {
      var volume = {};

      var existingTemplateVolumes = $(this.$root.$el).find('.volume-label');

      if (this.existingVolume) {
        volume.name = this.$volumesSearch.typeahead('val');
      } else {
        volume.name = $(this.$el).find('.volume-name .form-control').val();
        volume.driver = $(this.$el).find('.volume-driver .form-control').val();
        volume.options = utils.arrayToProperties(this.driverOptions.getData());
        volume.customProperties = utils.arrayToProperties(this.customProperties.getData());
      }

      if (existingTemplateVolumes && existingTemplateVolumes.length
          && !(this.model && this.model.name === volume.name)) {

        checkForDuplicateNames(existingTemplateVolumes, volume);
      }

      //volume.external = this.existingVolume;
      volume.existing = this.existingVolume;

      if (this.model) {
        volume.documentSelfLink = this.model.documentSelfLink;
      }

      return volume;
    },

    validate: function() {
      var definition = this.getVolumeDefinition();

      var validationErrors = utils.validate(definition, constraints);
      this.applyValidationErrors(validationErrors);

      return validationErrors;
    },

    applyValidationErrors: function(errors) {
      errors = errors || {};

      var volumeName = $(this.$el).find('.volume-name');
      utils.applyValidationError(volumeName, errors.name);
      utils.applyValidationError(volumeName, errors.existsInTemplate);

      var imageSearch = $(this.$el).find('.volume-name-search');
      utils.applyValidationError(imageSearch, errors.name);
      utils.applyValidationError(imageSearch, errors.existsInTemplate);
    }
  }
});

var toggleButtonsState = function() {
  var volumeNameInput = $(this.$el).find('.volume-name input').val();
  var volumeSearchNameInput = this.$volumesSearch.typeahead('val');

  let enableSave = (!this.existingVolume && volumeNameInput)
                      || (this.existingVolume && volumeSearchNameInput);

  this.$dispatch('disableVolumeSaveButton', !enableSave);
};

Vue.component('volume-definition-form', VolumeDefinitionForm);

export default VolumeDefinitionForm;
