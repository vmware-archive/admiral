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

import VueFormInput from 'components/common/VueFormInput'; //eslint-disable-line

export default Vue.component('password-input', {
  template: `
    <form-input
      type="password"
      :disabled="disabled"
      :label="label"
      :name="name"
      :required="required"
      :value="value"
      @change="onChange">
    </form-input>
  `,
  props: {
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
      this.$dispatch('change', value);
    }
  }
});
