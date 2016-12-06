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
      var elemPlacementZone = $(this.$el).find('.placementZone .form-control');
      this.placementZoneInput = new CustomDropdownSearchMenu(elemPlacementZone, {
        title: i18n.t('dropdownSearchMenu.title', {
          entity: i18n.t('app.placementZone.entity')
        }),
        searchPlaceholder: i18n.t('dropdownSearchMenu.searchPlaceholder', {
          entity: i18n.t('app.placementZone.entity')
        })
      });

      var _this = this;
      this.placementZoneInput.setOptionSelectCallback(function(option) {
        _this.placementZone = option;
      });

      this.unwatchPlacementZones = this.$watch('model.placementZones', () => {
        if (this.model.placementZones === constants.LOADING) {
          this.placementZoneInput.setLoading(true);
        } else {
          this.placementZoneInput.setLoading(false);
          this.placementZoneInput.setOptions(
          (this.model.placementZones || []).map((config) => config.resourcePoolState));
        }
      }, {immediate: true});

      this.unwatchPlacementZone = this.$watch('model.placementZone', () => {
        if (this.model.placementZone) {
          this.placementZoneInput.setSelectedOption(this.model.placementZone);
        }
      }, {immediate: true});

    }
  }
};

export default ClosureFieldsMixin;
