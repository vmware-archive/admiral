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

window.i18n = i18next;

var initializer = {};

initializer.init = function(initProperties, callback) {
  Vue.config.debug = false;
  Vue.config.silent = false;

  templateHelpers.register();

  const initI18N = function() {

    function getLocaleFromCookie() {
      var cookieLocaleValue = '';

      var vraCookieLocaleName = 'VCAC_LOCALE=';
      var standaloneCookieLocaleName = 'i18next=';
      var cookies = document.cookie ? document.cookie.split(';') : [];
      for (var idxCookie = 0; idxCookie < cookies.length; idxCookie++) {
        var cookie = cookies[idxCookie];

        while (cookie.charAt(0) === ' ') {
          cookie = cookie.substring(1);
        }

        if (cookie.indexOf(vraCookieLocaleName) === 0) {
          cookieLocaleValue = cookie.substring(vraCookieLocaleName.length, cookie.length);

        } else if (cookie.indexOf(standaloneCookieLocaleName) === 0) {
          cookieLocaleValue = cookie.substring(standaloneCookieLocaleName.length, cookie.length);
        }
      }

      return cookieLocaleValue;
    }

    return new Promise((resolve) => {

      // language detection options
      var order = ['navigator', 'cookie', 'localStorage', 'htmlTag', 'querystring'];
      var cookieLocaleValue = getLocaleFromCookie();
      if (cookieLocaleValue !== '') {
        localStorage.setItem('i18nextLng', cookieLocaleValue);
        order = ['localStorage', 'navigator', 'cookie', 'htmlTag', 'querystring'];
      }
      var detectionOpts = {
        order: order
      };

      i18next.use(i18nextXHRBackend)
        .use(i18nextBrowserLanguageDetector)
        .init({
          ns: ['admiral'],
          defaultNS: 'admiral',
          fallbackLng: 'en',
          backend: {
            loadPath: 'messages/{{ns}}.{{lng}}.json'
          },
          detection: detectionOpts,
          debug: true
      }, resolve);
    });
  };

  var services = require('core/services').default;
  var utils = require('core/utils').default;

  require('components/common/CommonComponentsRegistry').default; //eslint-disable-line

  if (!initProperties) {
    initI18N().then(callback);
    return;
  }

  console.log('Loading configuration files');
  services.loadConfigurationProperties().then((properties) => {
    console.log('Loaded configuration files');
    var configurationProperties = {};
    for (var prop in properties) {
      if (properties.hasOwnProperty(prop)) {
        configurationProperties[properties[prop].key] = properties[prop].value;
      }
    }

    utils.initializeConfigurationProperties(configurationProperties);

    console.log('Loading i18n');

    return Promise.all([
      Promise.resolve([]),

      initI18N()
    ]);

  }).then(callback).catch((err) => {
    console.warn('Error when loading configuration! Error: ' + JSON.stringify(err));
  });
};

export default initializer;
