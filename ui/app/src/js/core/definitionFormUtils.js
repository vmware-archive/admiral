/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import imageUtils from 'core/imageUtils';
import services from 'core/services';
import utils from 'core/utils';

var definitionFormUtils = {

  prepareMultiInputErrors(elements, callback) {
    var hasError = false;
    var errors = [];
    for (var idx = 0; idx < elements.length; idx++) {
      var error = callback(elements[idx]);
      if (Object.keys(error).length > 0) {
        hasError = true;
      }

      errors.push(error);
    }

    if (hasError) {
      return errors;
    }

    return null;
  },

  validateNameValuePair(properties) {
    return definitionFormUtils.prepareMultiInputErrors(properties, (property) => {
      var error = {};
      if (!property.name || validator.trim(property.name).length === 0) {
        error.name = 'errors.propertyNameRequired';
      }
      return error;
    });
  },

  containerDescriptionConstraints() {
    return {
      image: function(image) {
        if (!image || validator.trim(image).length === 0) {
          return 'errors.required';
        }
      },
      name: function(name) {
        if (!name || validator.trim(name).length === 0) {
          return 'errors.required';
        } else if (!validator.trim(name).match(/^[a-zA-Z0-9][a-zA-Z0-9_.-]+$/)) {
          return 'errors.propertyContainerNameInvalid';
        }
      },
      links: function(links) {
        return definitionFormUtils.prepareMultiInputErrors(links, (link) => {
          var error = {};
          if (!link.container) {
            error.container = 'errors.required';
          }
          return error;
        });
      },
      portBindings: function(portBindings) {
        return definitionFormUtils.prepareMultiInputErrors(portBindings, (portBinding) => {
          var error = {};
          if (portBinding.hostPort && !utils.isValidPort(portBinding.hostPort)) {
            error.hostPort = 'errors.portNumber';
          }
          if (!utils.isValidPort(portBinding.containerPort)) {
            error.containerPort = 'errors.portNumber';
          }
          return error;
        });
      },
      networks: function(networks) {
        return definitionFormUtils.prepareMultiInputErrors(networks, (network) => {
          var error = {};
          if (!network.network) {
            error.network = 'errors.networkRequired';
          }
          return error;
        });
      },
      volumes: function(volumes) {
        return definitionFormUtils.prepareMultiInputErrors(volumes, (volume) => {
          var error = {};
          if (!volume.container) {
            error.container = 'errors.volumeDestinationRequired';
            return error;
          }
          if (!utils.isAbsolutePath(volume.container)) {
            error.container = 'errors.invalidPath';
            return error;
          }
          if (volume.host) {
            var test = utils.isAbsolutePathOrName(volume.host);
            if (!test) {
              error.host = 'errors.invalidPathOrName';
            }
          }
          return error;
        });
      },
      _cluster: function(clusterSize) {
        if (clusterSize && !utils.isPositiveInteger(clusterSize)) {
          return 'errors.positiveNumber';
        }

        return null;
      },
      maximumRetryCount: function(retryCount) {
        if (retryCount && !utils.isNonNegativeInteger(retryCount)) {
          return 'errors.nonNegativeNumber';
        }
        return null;
      },
      cpuShares: function(cpuShares) {
        if (cpuShares && !utils.isNonNegativeInteger(cpuShares)) {
          return 'errors.nonNegativeNumber';
        }
        return null;
      },
      memoryLimit: function(bytes) {
        if (bytes && !utils.isValidContainerMemory(bytes)) {
          return 'errors.containerMemory';
        }
        return null;
      },
      memorySwapLimit: function(memSwapLimit) {
        if (memSwapLimit && !utils.isInteger(memSwapLimit, -1)) {
          return 'errors.nonNegativeNumberAndMinusOne';
        }
        return null;
      },
      affinity: function(affinities) {
        return definitionFormUtils.prepareMultiInputErrors(affinities, (affinity) => {
          var error = {};
          if (affinity.constraint && !affinity.servicename) {
            error.servicename = 'errors.serviceNameRequired';
          }
          return error;
        });
      },
      customProperties: definitionFormUtils.validateNameValuePair,
      env: definitionFormUtils.validateNameValuePair,
      healthConfig: function(healthConfig) {
        if (!healthConfig) {
          return null;
        }

        var error = {};

        var urlPathConfig = {
          require_tld: false,
          allow_underscores: true
        };
        if (healthConfig.protocol === 'HTTP' && healthConfig.urlPath
            && !validator.isURL(healthConfig.urlPath, urlPathConfig)) {
          error.urlPath = 'errors.urlPath';
        }

        if (healthConfig.port && !utils.isValidPort(healthConfig.port)) {
          error.port = 'errors.portNumber';
        }

        if (healthConfig.healthyThreshold
            && !utils.isNonNegativeInteger(healthConfig.healthyThreshold)) {
          error.healthyThreshold = 'errors.nonNegativeNumber';
        }

        if (healthConfig.unhealthyThreshold
            && !utils.isNonNegativeInteger(healthConfig.unhealthyThreshold)) {
          error.unhealthyThreshold = 'errors.nonNegativeNumber';
        }

        if (healthConfig.timeoutMillis
            && !utils.isNonNegativeInteger(healthConfig.timeoutMillis)) {
          error.timeoutMillis = 'errors.nonNegativeNumber';
        }

        if (Object.keys(error).length > 0) {
          return error;
        }
        return null;
      },
      logConfig: function(logConfig) {
        return definitionFormUtils.validateNameValuePair(logConfig.config);
      }
    };
  },

  typeaheadSource($typeaheadHolder) {
    var timeout;
    var lastCallback;
    return (q, syncCallback, asyncCallback) => {
      lastCallback = asyncCallback;
      clearTimeout(timeout);
      if (!q) {
        asyncCallback([]);
        $typeaheadHolder.removeClass('loading');
        return;
      }

      var image = q;
      var tag = imageUtils.getImageTag(q);
      if (tag) {
        image = q.substring(0, q.length - tag.length);
      }

      $typeaheadHolder.addClass('loading');
      timeout = setTimeout(() => {
        services.loadImageIds(image, this.IMAGE_RESULT_LIMIT).then((results) => {
          if (lastCallback === asyncCallback) {
            for (var i = 0; i < results.length; i++) {
              if (tag) {
                results[i] = results[i] + ':' + tag;
              }
            }
            asyncCallback(results);
            $typeaheadHolder.removeClass('loading');
          }
        });
      }, 300);
    };
  },

  tagsMatcher(tags) {
    return (q, syncCallback) => {
      var matches = tags.filter((t) => (t !== undefined && t.indexOf(q) !== -1));
      syncCallback(matches);
    };
  },

  setTagsTypeahead($element, tags) {
    $element.typeahead('destroy');
    $element.typeahead({ minLength: 0 }, {
      name: 'tags',
      limit: tags.length,
      source: this.tagsMatcher(tags)
    });
  },

  typeaheadTagsLoader($tagsHolder) {
    var $this = this;

    return function(event, selection) {
      $this.loadTags($tagsHolder, selection, null);
    };
  },

  loadTags($tagsHolder, selection, defaultTag) {
    var oldSelection = $tagsHolder.data('selection');
    if (selection === oldSelection) {
      return;
    }

    $tagsHolder.data('selection', selection);

    var $tagsInput = $tagsHolder.find('input');
    var $imageTags = $tagsHolder.find('.form-control');

    $tagsHolder.addClass('loading');
    if (!defaultTag) {
      $imageTags.typeahead('val', '');
      $tagsInput.attr('placeholder', i18n.t('app.container.request.inputs.imageTag.loadingHint'));
      // update typeahead since placeholder doesn't hide while typing
      this.setTagsTypeahead($imageTags, [this.DEFAULT_TAG]);
    }

    services.loadImageTags(selection).then((tags) => {
      if (tags.indexOf(this.DEFAULT_TAG) < 0) {
        tags.unshift(this.DEFAULT_TAG);
      }

      $tagsHolder.removeClass('loading');
      if (!defaultTag) {
        $tagsInput.attr('placeholder', i18n.t('app.container.request.inputs.imageTag.searchHint'));
      }

      this.setTagsTypeahead($imageTags, tags);
    }).catch((e) => {
      console.log(e);
      $tagsHolder.removeClass('loading');
      if (!defaultTag) {
        $tagsInput.attr('placeholder', i18n.t('app.container.request.inputs.imageTag.searchHint'));
      }
      this.setTagsTypeahead($imageTags, [this.DEFAULT_TAG]);
    });
  }
};

export default definitionFormUtils;
