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

import utils from 'core/utils';

var InstanceTypeSearch = {
  template: `
    <div>
      <div class="loading" v-if="loadingInstanceTypes">
        <span class="vertical-helper"></span>
        <div class="spinner"></div>
      </div>
      <multicolumn-editor-group
          :headers="[
              i18n('app.profile.edit.nameLabel'),
              i18n('app.profile.edit.valueLabel')
          ]"
          :label="i18n('app.profile.edit.instanceTypeMappingLabel')"
          :value="value"
          @change="onChange"
          v-if="!loadingInstanceTypes">
          <multicolumn-cell name="name">
              <text-control></text-control>
          </multicolumn-cell>
          <multicolumn-cell name="value">
              <typeahead-control :source="searchInstanceTypes" :limit="20"
              :renderer="renderer">
              </typeahead-control>
          </multicolumn-cell>
      </multicolumn-editor-group>
    </div>
  `,
  props: {
    endpoint: {
      required: true,
      type: Object
    },
    instanceTypeMapping: {
      required: false,
      type: Object
    },
    loadingInstanceTypes: {
      type: Boolean
    },
    renderer: {
      required: false,
      type: Function
    }
  },
  data() {
    let value = [];
    if (this.instanceTypeMapping) {
      value = Object.keys(this.instanceTypeMapping).map((key) => {
        return {
          name: key,
          value: this.instanceTypeMapping[key].instanceType
        };
      });
    }
    return {
      instanceTypeOptions: null,
      value: value
    };
  },
  attached() {
    this.unwatchEndpoint = this.$watch('endpoint', () => {
      this.pollInstanceTypes();
    }, {immediate: true});

    this.emitChange();
  },
  detached: function() {
    this.unwatchEndpoint();
  },
  methods: {
    searchInstanceTypes(name) {
      var _this = this;

      if (this.loadingInstanceTypes) {
        return new Promise(function(resolve) {
          var unwatchLoadingInstanceTypes = _this.$watch('loadingInstanceTypes', function() {
            unwatchLoadingInstanceTypes();
            resolve(_this.findInstanceTypes(name));
          });
        });
      } else {
        return Promise.resolve(this.findInstanceTypes(name));
      }
    },
    findInstanceTypes(name) {
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
      this.loadingInstanceTypes = true;

      return mcp.client.get(
        '/adapter/' + this.endpoint.endpointType + '/instance-type-adapter?endpoint=' +
          this.endpoint.documentSelfLink)
          .then(function(data) {
              this.loadingInstanceTypes = false;

              let result = {
                  totalCount: data.instanceTypes.length,
                  items: data.instanceTypes
              };
              this.instanceTypeOptions = result;

              this.emitChange();
          }.bind(this));
    },
    onChange(value) {
      this.value = value;
      this.emitChange();
    },

    emitChange() {
      this.$emit('change', {
        properties: {
          instanceTypeMapping: this.value.reduce((previous, current) => {
            if (current.name) {
              previous[current.name] = {
                instanceType: current.value
              };
            }
            return previous;
          }, {})
        },
        valid: this.validateMapping(this.value)
      });
    },

    validateMapping: function(mapping) {
      if (mapping.length === 0) {
        return false;
      }
      for (var i = 0; i < mapping.length; i++) {
        var mappingInstance = mapping[i];
        if (utils.xor(mappingInstance.name, mappingInstance.value)) {
          return false;
        }
      }
      return true;
    }
  }
};

export default InstanceTypeSearch;
