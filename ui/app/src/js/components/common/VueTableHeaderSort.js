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

var VueTableHeaderSort = Vue.extend({
  template: `
    <th class="sort-header" :class="{'sort-down': isSortAsc, 'sort-up': isSortDesc}">
      <slot>dasda</slot>
    </th>
  `,
  props: {
    active: {},
    sortOrder: {}
  },
  computed: {
    isSortAsc: function() {
      return this.active && this.sortOrder === 1;
    },
    isSortDesc: function() {
      return this.active && this.sortOrder === -1;
    }
  }
});

Vue.component('thsort', VueTableHeaderSort);

export default VueTableHeaderSort;
