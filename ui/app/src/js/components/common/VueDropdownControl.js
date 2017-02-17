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

import VueDropdown from 'components/common/VueDropdown'; //eslint-disable-line
import VueFormControl from 'components/common/VueFormControl'; //eslint-disable-line

export default Vue.component('dropdown-control', {
  template: `
    <form-control
      :class="class"
      :label="label"
      :name="name"
      :required="required">
      <dropdown
          :disabled="disabled"
          :entity="entity"
          :loading="loading"
          :manage="manage"
          :options="options"
          :renderer="renderer"
          :value="value"
          @change="onChange">
      </dropdown>
    </form-control>
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
