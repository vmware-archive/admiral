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

import DropdownSearchMenu from 'components/common/DropdownSearchMenu';

const INITIAL_FILTER = '';
const RESULT_LIMIT = 10;

var DropdownSearch = Vue.extend({
  template: '<div class="dropdown-holder"></div>',
  props: {
    disabled: false,
    entity: null,
    filter: null,
    value: null
  },
  attached: function() {
    let dropdownSearchMenu = new DropdownSearchMenu($(this.$el), {
      title: i18n.t('dropdownSearchMenu.title', {
        entity: this.entity
      }),
      searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
        entity: this.entity
      })
    });
    dropdownSearchMenu.setSelectedOption(this.value);
    dropdownSearchMenu.setDisabled(this.disabled);
    dropdownSearchMenu.setOptionSelectCallback(() =>
        this.$dispatch('change', dropdownSearchMenu.getSelectedOption()));
    dropdownSearchMenu.setClearOptionSelectCallback(() =>
        this.$dispatch('change', dropdownSearchMenu.getSelectedOption()));
    if (this.filter) {
      dropdownSearchMenu.setFilterCallback((q, callback) => {
        this.filter.call(this, q || INITIAL_FILTER, RESULT_LIMIT).then((result) => {
          callback(result);
        });
      });
      dropdownSearchMenu.setFilter(INITIAL_FILTER);
    }
  }
});

Vue.component('dropdown-search', DropdownSearch);

export default DropdownSearch;
