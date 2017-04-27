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

import VueFormGroup from 'components/common/VueFormGroup'; //eslint-disable-line
import VueFormLabel from 'components/common/VueFormLabel'; //eslint-disable-line
import utils from 'core/utils';

export default Vue.component('select-group', {
  template: `
    <form-group
      :class="class">
      <form-label
        :for="name"
        :required="required">
        {{label}}
      </form-label>
      <select-control
        :disabled="disabled"
        :id="id || name"
        :name="name"
        :options="options"
        :value="value"
        @change="onChange">
      </select-control>
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
    id: {
      required: false,
      type: String
    },
    label: {
      required: true,
      type: String
    },
    name: {
      default: () => utils.uuid(),
      required: false,
      type: String
    },
    required: {
      default: false,
      required: false,
      type: Boolean
    },
    options: {
      default: [],
      required: false,
      type: Array
    },
    value: {
      required: false,
      type: Object
    }
  },
  methods: {
    onChange(value) {
      this.value = value;
      this.$emit('change', this.value);
    }
  }
});
