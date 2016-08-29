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

import MulticolumnInputs from 'components/common/MulticolumnInputs';

var VueMulticolumnInputs = Vue.extend({
  template: '<div></div>',
  props: {
    value: {},
    columnModel: {required: true}
  },

  attached: function() {
    this.multicolumnInputs = new MulticolumnInputs($(this.$el), this.columnModel);
    this.unwatchModel = this.$watch('value', (data) => {
      this.multicolumnInputs.setData(data);
    }, {immediate: true});
  },

  detached: function() {
    this.unwatchModel();
    this.multicolumnInputs = null;
  },

  methods: {
    addColumn: function(column) {
      if (!column.key) {
        throw 'key is required property for column definition';
      }
      this.columnModel[column.key] = {
        header: column.header,
        placeholder: column.placeholder,
        type: column.type
      };
    },
    getData: function() {
      return this.multicolumnInputs.getData();
    }
  }
});

Vue.component('multicolumn-inputs', VueMulticolumnInputs);

export default VueMulticolumnInputs;
