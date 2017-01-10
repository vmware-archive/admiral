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

export default Vue.component('dropdown', {
  template: `
    <div class="dropdown-holder"></div>
  `,
  props: {
    disabled: {
      default: false,
      required: false,
      type: Boolean
    },
    entity: {
      required: true,
      type: String
    },
    loading: {
      default: false,
      required: false,
      type: Boolean
    },
    options: {
      default: [],
      required: false,
      type: Array
    },
    value: {
      required: false,
      type: String
    }
  },
  attached: function() {
    let dropdownSearchMenu = new DropdownSearchMenu($(this.$el), {
      title: i18n.t('dropdownSearchMenu.title', {
        entity: this.entity
      }),
      searchDisabled: true
    });
    dropdownSearchMenu.setOptionSelectCallback(() =>
        this.$dispatch('change', dropdownSearchMenu.getSelectedOption()));
    dropdownSearchMenu.setClearOptionSelectCallback(() =>
        this.$dispatch('change', dropdownSearchMenu.getSelectedOption()));
    dropdownSearchMenu.setDisabled(this.disabled);
    dropdownSearchMenu.setLoading(this.loading);
    dropdownSearchMenu.setOptions(this.options);
    dropdownSearchMenu.setSelectedOption(this.value);

    this.unwatchDisabled = this.$watch('disabled', (disabled) => {
      this.disabled = disabled;
      dropdownSearchMenu.setDisabled(disabled);
    });
    this.unwatchLoading = this.$watch('loading', (loading) => {
      this.loading = loading;
      dropdownSearchMenu.setLoading(loading);
    });
    this.unwatchOptions = this.$watch('options', (options) => {
      this.options = options;
      dropdownSearchMenu.setOptions(options);
    });
    this.unwatchValue = this.$watch('value', (value) => {
      this.value = value;
      dropdownSearchMenu.setSelectedOption(value);
    });
  },
  detached: function() {
    this.unwatchDisabled();
    this.unwatchLoading();
    this.unwatchOptions();
    this.unwatchValue();
  }
});
