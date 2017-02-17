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

import VueFormControl from 'components/common/VueFormControl'; //eslint-disable-line
import VuePasswordInput from 'components/common/VuePasswordInput'; //eslint-disable-line

export default Vue.component('password-control', {
  template: `
    <form-control
      :class="class"
      :label="label"
      :name="name"
      :required="required">
      <password-input
        :disabled="disabled"
        :name="name"
        :value="value"
        @change="onChange">
      </password-input>
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
    label: {
      required: true,
      type: String
    },
    name: {
      required: false,
      type: String
    },
    required: {
      default: false,
      required: false,
      type: Boolean
    },
    value: {
      required: false,
      type: String
    }
  },
  methods: {
    onChange(value) {
      this.$dispatch('change', value, this);
    }
  }
});
