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

import modal from 'core/modal';
import constants from 'core/constants';
import TemplateExportFormatTemplate from 'TemplateExportFormatTemplate';

var showExportDialog = function(exportToYamlLink, exportToDockerComposeLink) {
    var templateFormatChooser = $(TemplateExportFormatTemplate());
    modal.show(templateFormatChooser);

    templateFormatChooser.find('.confirmCancel').click(function(e) {
      e.preventDefault();
      modal.hide();
    });

    templateFormatChooser.find('.confirmExport').attr('href', exportToYamlLink);

    templateFormatChooser.find('.export-template-options')
        .on('change', 'input:radio[name="format"]', function() {

      var formatType = templateFormatChooser
        .find('.export-template-options input:radio[name="format"]:checked').val();
      if (formatType === constants.TEMPLATES.EXPORT_FORMAT.DOCKER_COMPOSE) {
        templateFormatChooser.find('.confirmExport').attr('href', exportToDockerComposeLink);
      } else {
        templateFormatChooser.find('.confirmExport').attr('href', exportToYamlLink);
      }
    });

    templateFormatChooser.find('.confirmExport').click(function() {
      modal.hide();
    });
};

export default {
  showExportDialog: showExportDialog
};
