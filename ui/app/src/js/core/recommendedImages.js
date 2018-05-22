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
import ft from 'core/ft';

const REGISTRY_SCHEME_REG_EXP = /^(https?):\/\//;

var processImages = function(context, images) {
  images.forEach((i) => {
    processImage(i);
  });
  context.images = images;
};

var processImage = function(popularImage) {
  var host = popularImage.registry.replace(REGISTRY_SCHEME_REG_EXP, '');
  popularImage.documentId = host + '/' + popularImage.name;
  popularImage.icon = imageUtils.getImageIconLink(popularImage.name);
  popularImage.type = constants.TEMPLATES.TYPES.IMAGE;
  popularImage.isFavorite = true;
};

var recommendedImages = {
  images: null,
  loadImages: function() {
    var _this = this;
    if (ft.areFavoriteImagesEnabled()) {
      return services.loadPopularImages().then((result) => {
        if (result != null) {
          var images = Object.values(result.documents);
          processImages(_this, images);
        }
      });
    } else {
      if (this.images != null) {
        return Promise.resolve();
      }
      return services.loadPopularImages().then((result) => {
        if (result != null) {
          processImages(_this, result);
        }
      });
    }
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
