/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import InstanceTypeSearch from 'components/profiles/instance-types/InstanceTypeSearch';

import utils from 'core/utils';

export default Vue.component('aws-instance-type-search', {
  mixins: [InstanceTypeSearch],

  methods: {
    renderer: function(context) {
      let display = context.name;
      let query = context._query || '';
      let index = query ? display.toLowerCase().indexOf(query.toLowerCase()) : -1;
      if (index >= 0) {
        display = utils.escapeHtml(display.substring(0, index))
            + '<strong>'
            + utils.escapeHtml(display.substring(index, index + query.length))
            + '</strong>'
            + utils.escapeHtml(display.substring(index + query.length));
      } else {
        display = utils.escapeHtml(display);
      }

      let descriptionText = i18n.t('app.profile.edit.instanceTypeMappingDisplayAWS', context);

      return '<div>' +
            '   <div class="host-picker-item-primary">' +
            '      ' + display +
            '   </div>' +
            '   <div class="host-picker-item-secondary truncateText">' +
            '      ' + descriptionText +
            '   </div>' +
            '</div>';
    }
  }
});
