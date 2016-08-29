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

import RegistryRowTemplate from 'RegistryRowTemplate';
import RegistryRowHighlightTemplate from 'RegistryRowHighlightTemplate';

var renderers = {
  render: function(registryObject) {
    return $(RegistryRowTemplate(registryObject));
  },

  renderHighlighted: function(registryObject, $registryRow, isNew, isUpdated) {
    var model = {
      registryRow: $registryRow.html(),
      hostname: registryObject.hostname,
      name: registryObject.name,
      status: registryObject.status,
      isNew: isNew,
      isUpdated: isUpdated
    };
    return $(RegistryRowHighlightTemplate(model));
  }
};

export default renderers;
