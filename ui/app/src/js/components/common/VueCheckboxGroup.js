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

export default Vue.component('checkbox-group', {
  template: `
    <form-group
      :class="class">
      <checkbox-control
        :disabled="disabled"
        :id="id || name"
        :name="name"
        :value="value"
        @change="onChange">
      </checkbox-control>
      <span> </span>
      <form-label
        :for="name"
        :required="required">
        {{label}}
      </form-label>
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
    value: {
      required: false,
      type: Boolean
    }
  },
  methods: {
    onChange(value) {
      this.value = value;
      this.$emit('change', this.value);
    }
  }
});
