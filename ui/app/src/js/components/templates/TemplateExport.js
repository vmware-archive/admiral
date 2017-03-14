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

import TemplateExportVue from 'components/templates/TemplateExportVue.html';
import constants from 'core/constants';

var TemplateExport = Vue.extend({
  template: TemplateExportVue,

  props: {
    linkYaml: {
      required: true,
      type: String
    },
    linkDocker: {
      required: true,
      type: String
    }
  },

  data: function() {
    return {
      exportFormat: constants.TEMPLATES.EXPORT_FORMAT.COMPOSITE_BLUEPRINT
    };
  },

  methods: {
    confirmExport: function($event) {
      $event.preventDefault();
      $event.stopPropagation();

      let isSelectedFormatYaml =
        this.exportFormat === constants.TEMPLATES.EXPORT_FORMAT.COMPOSITE_BLUEPRINT;
      // export the template
      window.location.href = isSelectedFormatYaml ? this.linkYaml : this.linkDocker;

      // notify parent
      this.$dispatch('confirm-template-export');
    }
  }
});

Vue.component('template-export', TemplateExport);

export default TemplateExport;
