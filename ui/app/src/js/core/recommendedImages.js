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

import constants from 'core/constants';
import imageUtils from 'core/imageUtils';
import services from 'core/services';

const REGISTRY_SCHEME_REG_EXP = /^(https?):\/\//;

var recommendedImages = {
  images: null,
  loadImages: function() {
    if (this.images != null) {
      return Promise.resolve();
    }
    var _this = this;
    return services.loadPopularImages().then((result) => {
      if (result != null) {
        for (var i = 0; i < result.length; i++) {
          var image = result[i];
          var host = image.registry.replace(REGISTRY_SCHEME_REG_EXP, '');
          image.documentId = host + '/' + image.name;
          image.icon = imageUtils.getImageIconLink(image.name);
          image.type = constants.TEMPLATES.TYPES.IMAGE;
        }
        _this.images = result;
      }
    });
  },
  getIcon: function(id) {
    if (this.images != null) {
      for (var i = 0; i < this.images.length; i++) {
        var image = this.images[i];
        if (id === image.documentId) {
          return image.icon;
        }
      }
    }
    return null;
  }
};

export default recommendedImages;
