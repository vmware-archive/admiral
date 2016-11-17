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

import ClosureDescriptionPropertiesVue from
 'components/containers/ClosureDescriptionPropertiesVue.html';
import utils from 'core/utils';

var ClosureDescriptionProperties = Vue.extend({
  template: ClosureDescriptionPropertiesVue,
  props: {
    model: {
      required: true
    }
  },
  computed: {
    resources: function() {
      return JSON.stringify(this.model.resources);
    },
    descriptionId: function() {
      return utils.getDocumentId(this.model.descriptionId);
    }

  }
});

Vue.component('closure-description-properties', ClosureDescriptionProperties);

export default ClosureDescriptionProperties;
