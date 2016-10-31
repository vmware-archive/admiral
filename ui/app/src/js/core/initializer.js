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

import templateHelpers from 'core/templateHelpers';

var initializer = {};
initializer.init = function(callback) {
  Vue.config.debug = false;
  Vue.config.silent = false;

  templateHelpers.register();

  var initI18N = function() {
    i18n.init({
        ns: {
          namespaces: ['admiral'],
          defaultNs: 'admiral'
        },

        // Will load messages/admiral.en-EN.json for example and fallback to
        // messages/admiral.en.json
        resGetPath: 'messages/__ns__.__lng__.json',
        useCookie: false,
        fallbackLng: 'en',
        debug: true
      }, callback);
  };

  requirejs(['core/services', 'core/utils', 'components/common/CommonComponentsRegistry'],
            function(services, utils) {
    services.loadConfigurationProperties().then((properties) => {
      var configurationProperties = {};
      for (var prop in properties) {
        if (properties.hasOwnProperty(prop)) {
          configurationProperties[properties[prop].key] = properties[prop].value;
        }
      }
      utils.initializeConfigurationProperties(configurationProperties);
    }).catch((err) => {
      console.warn('Error when loading configuration properties! Error: ' + err);
    }).then(initI18N);
  });
};

export default initializer;
