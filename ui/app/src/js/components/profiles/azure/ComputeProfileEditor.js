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
          <text-control></text-control>
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
          <text-control></text-control>
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
      instanceTypeMapping: Object.keys(instanceTypeMapping).map((key) => {
        return {
          name: key,
          value: instanceTypeMapping[key].instanceType
        };
      }),
      imageMapping: Object.keys(imageTypeMapping).map((key) => {
        return {
          name: key,
          value: imageTypeMapping[key].image
        };
      })
    };
  },
  attached() {
    this.emitChange();
  },
  methods: {
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
        valid: true
      });
    }
  }
});
