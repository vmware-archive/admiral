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

export default Vue.component('select-control', {
  template: `
    <div class="form-control">
      <div class="select">
        <select
          :disabled="disabled"
          :id="id"
          :name="name"
          @change="onChange">
          <option
            v-for="option in options"
            :selected="option.value === value.value"
            :value="option.value">
            {{option.name}}
          </option>
        </select>
      </div>
    </div>
  `,
  props: {
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
    onChange($event) {
      $event.preventDefault();
      $event.stopPropagation();

      this.value = this.options && this.options[$event.currentTarget.selectedIndex];
      this.$emit('change', this.value);
    }
  }
});
