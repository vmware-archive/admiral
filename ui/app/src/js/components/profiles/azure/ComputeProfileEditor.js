/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import services from 'core/services';
import utils from 'core/utils';

export default Vue.component('azure-compute-profile-editor', {
  template: `
    <div>
      <multicolumn-editor-group
        :headers="[
          i18n('app.profile.edit.nameLabel'),
          i18n('app.profile.edit.valueLabel')
        ]"
        :label="i18n('app.profile.edit.instanceTypeMappingLabel')"
        :value="instanceTypeMapping"
        @change="onInstanceTypeMappingChange">
        <multicolumn-cell name="name">
          <text-control></text-control>
        </multicolumn-cell>
        <multicolumn-cell name="value">
          <typeahead-control :source="searchInstanceTypes" :limit="20"
            :renderer="renderInstanceTypeOption">
          </typeahead-control>
        </multicolumn-cell>
      </multicolumn-editor-group>
      <multicolumn-editor-group
        :headers="[
          i18n('app.profile.edit.nameLabel'),
          i18n('app.profile.edit.valueLabel')
        ]"
        :label="i18n('app.profile.edit.imageMappingLabel')"
        :value="imageMapping"
        @change="onImageMappingChange">
        <multicolumn-cell name="name">
          <text-control></text-control>
        </multicolumn-cell>
        <multicolumn-cell name="value">
          <typeahead-control
            :source="searchImages" :limit="20">
          </typeahead-control>
        </multicolumn-cell>
      </multicolumn-editor-group>
    </div>
  `,
  props: {
    endpoint: {
      required: false,
      type: Object
    },
    model: {
      required: true,
      type: Object
    }
  },
  data() {
    let instanceTypeMapping = this.model.instanceTypeMapping &&
        this.model.instanceTypeMapping.asMutable() || [];
    let imageTypeMapping = this.model.imageMapping &&
        this.model.imageMapping.asMutable() || [];
    return {
      instanceTypeOptions: null,
      instanceTypeMapping: Object.keys(instanceTypeMapping).map((key) => {
        return {
          name: key,
          value: instanceTypeMapping[key].instanceType
        };
      }),
      imageMapping: Object.keys(imageTypeMapping).map((key, index) => {
        return {
          name: key,
          value: this.model.images[index]
        };
      })
    };
  },
  attached() {
    this.pollInstanceTypes();
    this.emitChange();
  },
  methods: {
    searchInstanceTypes(name) {
        var f = {};
        if (this.instanceTypeOptions) {
            f.items = this.instanceTypeOptions.items;
            name = name && name.toLowerCase();
            f.items = f.items.filter(
                a => a.name.toLowerCase().indexOf(name) >= 0 ||
                  a.id.toLowerCase().indexOf(name) >= 0);
            f.totalCount = f.items.length;
        }
        return Promise.resolve(f);
    },
    pollInstanceTypes() {
      return mcp.client.get('/adapter/azure/instance-type-adapter?endpoint=' +
          this.endpoint.documentSelfLink)
          .then(function(data) {
                let result = {
                  totalCount: data.instanceTypes.length,
                  items: data.instanceTypes
              };
              this.instanceTypeOptions = result;
              this.emitChange();
          }.bind(this));
    },
    renderInstanceTypeOption: function(context) {
      let display = context.name;
      let query = context._query || '';
      let index = query ? display.toLowerCase().indexOf(query.toLowerCase()) : -1;
      if (index >= 0) {
          display = utils.escapeHtml(display.substring(0, index))
              + '<strong>'
              + utils.escapeHtml(display.substring(index, index + query.length))
              + '</strong>'
              + utils.escapeHtml(display.substring(index + query.length));
      } else {
          display = utils.escapeHtml(display);
      }

      return '<div>' +
            '   <div class="host-picker-item-primary">' +
            '      ' + display +
            '   </div>' +
            '   <div class="host-picker-item-secondary truncateText">' +
            '      ' + i18n.t('app.profile.edit.instanceTypeMappingDisplay', context) +
            '   </div>' +
            '</div>';
    },
    searchImages(...args) {
      if (!this.endpoint) {
        return Promise.resolve([]);
      }
      return new Promise((resolve, reject) => {
        services.searchImageResources.apply(null, [
          this.endpoint && this.endpoint.documentSelfLink,
          ...args
        ]).then((result) => {
          resolve(result);
        }).catch(reject);
      });
    },
    onInstanceTypeMappingChange(value) {
      this.instanceTypeMapping = value;
      this.emitChange();
    },
    onImageMappingChange(value) {
      this.imageMapping = value;
      this.emitChange();
    },
    emitChange() {
      this.$emit('change', {
        properties: {
          instanceTypeMapping: this.instanceTypeMapping.reduce((previous, current) => {
            if (current.name) {
              previous[current.name] = {
                instanceType: current.value
              };
            }
            return previous;
          }, {}),
          imageMapping: this.imageMapping.reduce((previous, current) => {
            if (current.name) {
              previous[current.name] = {
                image: current.value
              };
            }
            return previous;
          }, {})
        },
        valid: this.validateMapping(this.instanceTypeMapping)
          && this.validateMapping(this.imageMapping)
      });
    },
    validateMapping: function(mapping) {
      for (var i = 0; i < mapping.length; i++) {
        var mappingInstance = mapping[i];
        if (utils.xor(mappingInstance.name, mappingInstance.value)) {
          return false;
        }
      }
      return true;
    }
  }
});
