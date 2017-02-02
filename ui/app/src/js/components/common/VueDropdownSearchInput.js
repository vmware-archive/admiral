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

import VueDropdownSearch from 'components/common/VueDropdownSearch'; //eslint-disable-line

export default Vue.component('dropdown-search-input', {
  template: `
    <div :class="['form-group', name]">
      <label :class="{'required': required}">
        {{label}}
      </label>
      <dropdown-search
          :disabled="disabled"
          :entity="entity"
          :filter="filter"
          :loading="loading"
          :manage="manage"
          :options="options"
          :renderer="renderer"
          :value="value"
          @change="onChange">
      </dropdown-search>
    </div>
  `,
  props: {
    disabled: {
      default: false,
      required: false,
      type: Boolean
    },
    entity: {
      required: true,
      type: String
    },
    filter: {
      required: false,
      type: Function
    },
    label: {
      required: true,
      type: String
    },
    loading: {
      default: false,
      required: false,
      type: Boolean
    },
    manage: {
      default: () => [],
      required: false,
      type: Array
    },
    name: {
      required: false,
      type: String
    },
    options: {
      default: () => [],
      required: false,
      type: Array
    },
    renderer: {
      required: false,
      type: Function
    },
    required: {
      default: false,
      required: false,
      type: Boolean
    },
    value: {
      required: false,
      type: Object
    }
  },
  methods: {
    onChange(value, instance) {
      this.$dispatch('change', value, instance);
    }
  }
});
