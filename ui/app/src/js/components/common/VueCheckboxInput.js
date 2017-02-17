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

export default Vue.component('checkbox-input', {
  template: `
    <input
      type="checkbox"
      :checked="value ? 'checked' : ''"
      :disabled="disabled"
      :id="name"
      :name="name"
      @change="onChange($event)">
  `,
  props: {
    disabled: {
      default: false,
      required: false,
      type: Boolean
    },
    name: {
      required: false,
      type: String
    },
    value: {
      required: false,
      type: Boolean
    }
  },
  methods: {
    onChange($event) {
      $event.preventDefault();
      $event.stopPropagation();

      this.$dispatch('change', $event.currentTarget.checked, this);
    }
  }
});
