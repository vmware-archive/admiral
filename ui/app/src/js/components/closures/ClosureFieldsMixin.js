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

import CustomDropdownSearchMenu from 'components/common/CustomDropdownSearchMenu';
import constants from 'core/constants';

var ClosureFieldsMixin = {
  methods: {
    initializeClosureFields: function() {
      // Resource pool input
      var elemResourcePool = $(this.$el).find('.resourcePool .form-control');
      this.resourcePoolInput = new CustomDropdownSearchMenu(elemResourcePool, {
        title: i18n.t('dropdownSearchMenu.title', {
          entity: i18n.t('app.resourcePool.entity')
        }),
        searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
          entity: i18n.t('app.resourcePool.entity')
        })
      });

      var _this = this;
      this.resourcePoolInput.setOptionSelectCallback(function(option) {
        _this.resourcePool = option;
      });

      this.unwatchResourcePools = this.$watch('model.resourcePools', () => {
        if (this.model.resourcePools === constants.LOADING) {
          this.resourcePoolInput.setLoading(true);
        } else {
          this.resourcePoolInput.setLoading(false);
          this.resourcePoolInput.setOptions(
          (this.model.resourcePools || []).map((config) => config.resourcePoolState));
        }
      }, {immediate: true});

      this.unwatchResourcePool = this.$watch('model.resourcePool', () => {
        if (this.model.resourcePool) {
          this.resourcePoolInput.setSelectedOption(this.model.resourcePool);
        }
      }, {immediate: true});

    }
  }
};

export default ClosureFieldsMixin;
