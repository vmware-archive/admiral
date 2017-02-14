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

export default Vue.component('form-input', {
  template: `
    <div :class="['form-group', class]">
      <label
        :class="{'required': required}"
        :for="name">
        {{label}}
      </label>
      <input
        :disabled="disabled"
        :id="name"
        :name="name"
        :type="type"
        :value="value"
        @change="onChange($event)"
        @input="onChange($event)">
    </div>
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
    type: {
      required: true,
      type: String
    },
    value: {
      required: false,
      type: String
    }
  },
  methods: {
    onChange($event) {
      $event.preventDefault();
      $event.stopPropagation();

      this.$dispatch('change', $event.currentTarget.value, this);
    }
  }
});
