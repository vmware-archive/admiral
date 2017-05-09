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
import VueFormGroup from 'components/common/VueFormGroup'; //eslint-disable-line
import VueFormLabel from 'components/common/VueFormLabel'; //eslint-disable-line

export default Vue.component('dropdown-search-group', {
  template: `
    <form-group
      :class="class">
      <form-label
        :required="required">
        {{label}}
      </form-label>
      <dropdown-search
        :disabled="disabled"
        :entity="entity"
        :filter="filter"
        :loading="loading"
        :manage="manage"
        :options="options"
        :renderer="renderer"
        :value="value"
        :value-renderer="valueRenderer"
        @change="onChange">
      </dropdown-search>
    </form-group>
  `,
  props: {
    class: {
      required: false,
      type: String
    },
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
    },
    valueRenderer: {
      required: false,
      type: Function
    }
  },
  methods: {
    onChange(value) {
      this.value = value;
      this.$emit('change', this.value);
    }
  }
});
