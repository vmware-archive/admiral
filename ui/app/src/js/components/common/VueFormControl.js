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

export default Vue.component('form-control', {
  template: `
    <div :class="['form-group', class]">
      <slot v-if="labelPosition === 'right'"></slot>
      <label
        :class="{'required': required}"
        :for="name">
        {{label}}
      </label>
      <slot v-if="labelPosition === 'left'"></slot>
    </div>
  `,
  props: {
    class: {
      required: false,
      type: String
    },
    label: {
      required: true,
      type: String
    },
    labelPosition: {
      default: 'left',
      required: false,
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
    }
  }
});
