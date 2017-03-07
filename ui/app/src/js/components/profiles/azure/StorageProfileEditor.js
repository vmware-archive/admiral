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

export default Vue.component('azure-storage-profile-editor', {
  template: `
    <div>
      <multicolumn-editor-group
        :headers="[
          i18n('app.environment.edit.nameLabel'),
          i18n('app.environment.edit.valueLabel')
        ]"
        :label="i18n('app.environment.edit.bootDiskPropertyMappingLabel')"
        :value="bootDiskPropertyMapping"
        @change="onBootDiskPropertyMappingChange">
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
    let bootDiskPropertyMapping = this.model.bootDiskPropertyMapping &&
        this.model.bootDiskPropertyMapping.asMutable() || [];
    return {
      bootDiskPropertyMapping: Object.keys(bootDiskPropertyMapping).map((key) => {
        return {
          name: key,
          value: bootDiskPropertyMapping[key]
        };
      })
    };
  },
  attached() {
    this.emitChange();
  },
  methods: {
    onBootDiskPropertyMappingChange(value) {
      this.bootDiskPropertyMapping = value;
      this.emitChange();
    },
    emitChange() {
      this.$emit('change', {
        properties: {
          bootDiskPropertyMapping: this.bootDiskPropertyMapping.reduce((previous, current) => {
            if (current.name) {
              previous[current.name] = current.value;
            }
            return previous;
          }, {})
        },
        valid: true
      });
    }
  }
});
