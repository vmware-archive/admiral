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

const props = {
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
    required: false,
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
  type: {
    required: false,
    type: String
  },
  value: {
    required: false
  }
};

const methods = {
  onChange(value) {
    this.value = value;
    this.$emit('change', this.value);
  }
};

export default Vue.component('input-group', {
  template: `
    <form-group
      :class="class">
      <form-label
        :for="name"
        :required="required">
        {{label}}
      </form-label>
      <input-control
        :disabled="disabled"
        :id="id || name"
        :name="name"
        :type="type"
        :value="value"
        @change="onChange">
      </input-control>
    </form-group>
  `,
  props,
  methods: {
    onChange(value) {
      this.value = value;
      this.$emit('change', this.value);
    }
  }
});

Vue.component('number-group', {
  template: `
    <input-group
      type="number"
      :class="class"
      :disabled="disabled"
      :id="id"
      :label="label"
      :name="name"
      :required="required"
      :value="value"
      @change="onChange">
    </input-group>
  `,
  props,
  methods
});

Vue.component('password-group', {
  template: `
    <input-group
      type="password"
      :class="class"
      :disabled="disabled"
      :id="id"
      :label="label"
      :name="name"
      :required="required"
      :value="value"
      @change="onChange">
    </input-group>
  `,
  props,
  methods
});

Vue.component('text-group', {
  template: `
    <input-group
      type="text"
      :class="class"
      :disabled="disabled"
      :id="id"
      :label="label"
      :name="name"
      :required="required"
      :value="value"
      @change="onChange">
    </input-group>
  `,
  props,
  methods
});
