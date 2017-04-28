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
    manage: {
      default: () => [],
      required: false,
      type: Array
    },
    options: {
      default: () => [],
      required: false,
      type: Array
    },
    renderer: {
      required: false,
      type: Function
    },
    value: {
      required: false,
      type: Object
    },
    valueRenderer: {
      required: false,
      type: Function
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
        this.$emit('change', dropdownSearchMenu.getSelectedOption()));
    dropdownSearchMenu.setClearOptionSelectCallback(() =>
        this.$emit('change', dropdownSearchMenu.getSelectedOption()));
    dropdownSearchMenu.setDisabled(this.disabled);
    dropdownSearchMenu.setLoading(this.loading);
    if (this.renderer) {
      dropdownSearchMenu.setOptionsRenderer(this.renderer);
    }
    dropdownSearchMenu.setOptions(this.options);
    if (this.manage) {
      dropdownSearchMenu.setManageOptions(this.manage);
    }
    if (this.valueRenderer) {
      dropdownSearchMenu.setValueRenderer(this.valueRenderer);
    }
    dropdownSearchMenu.setSelectedOption(this.value);

    this.unwatchDisabled = this.$watch('disabled', (disabled) => {
      dropdownSearchMenu.setDisabled(disabled);
    });
    this.unwatchLoading = this.$watch('loading', (loading) => {
      dropdownSearchMenu.setLoading(loading);
    });
    this.unwatchOptions = this.$watch('options', (options) => {
      dropdownSearchMenu.setOptions(options);
    });
    this.unwatchValue = this.$watch('value', (value) => {
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
