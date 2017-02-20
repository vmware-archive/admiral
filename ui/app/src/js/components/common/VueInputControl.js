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

const props = {
  disabled: {
    default: false,
    required: false,
    type: Boolean
  },
  id: {
    required: false,
    type: String
  },
  name: {
    required: false,
    type: String
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

export default Vue.component('input-control', {
  template: `
    <div class="form-control">
      <input
        class="form-control"
        :disabled="disabled"
        :id="id"
        :name="name"
        :type="type"
        :value="value"
        @change="onChange"
        @input="onChange">
    </div>
  `,
  props,
  methods: {
    onChange($event) {
      $event.preventDefault();
      $event.stopPropagation();

      this.value = $event.currentTarget.value;
      this.$emit('change', this.value);
    }
  }
});

Vue.component('number-control', {
  template: `
    <input-control
      type="number"
      :disabled="disabled"
      :id="id"
      :name="name"
      :value="value"
      @change="onChange">
    </input-control>
  `,
  props,
  methods
});

Vue.component('password-control', {
  template: `
    <input-control
      type="password"
      :disabled="disabled"
      :id="id"
      :name="name"
      :value="value"
      @change="onChange">
    </input-control>
  `,
  props,
  methods
});

Vue.component('text-control', {
  template: `
    <input-control
      type="text"
      :disabled="disabled"
      :id="id"
      :name="name"
      :value="value"
      @change="onChange">
    </input-control>
  `,
  props,
  methods
});
