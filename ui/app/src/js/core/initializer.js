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

  const DEFAULT_ADAPTERS = {
    'aws': {
      icon: 'image-assets/endpoints/aws.png',
      endpointEditor: 'aws-endpoint-editor',
      computeProfileEditor: 'aws-compute-profile-editor',
      networkProfileEditor: 'aws-network-profile-editor',
      storageProfileEditor: 'aws-storage-profile-editor'
    },
    'azure': {
      icon: 'image-assets/endpoints/azure.png',
      endpointEditor: 'azure-endpoint-editor',
      computeProfileEditor: 'azure-compute-profile-editor',
      networkProfileEditor: 'azure-network-profile-editor',
      storageProfileEditor: 'azure-storage-profile-editor'
    },
    'vsphere': {
      icon: 'image-assets/endpoints/vsphere.png',
      endpointEditor: 'vsphere-endpoint-editor',
      computeProfileEditor: 'vsphere-compute-profile-editor',
      networkProfileEditor: 'vsphere-network-profile-editor',
      storageProfileEditor: 'vsphere-storage-profile-editor'
    }
  };

  var ft = require('core/ft').default;
  var services = require('core/services').default;
  var utils = require('core/utils').default;

  require('components/common/CommonComponentsRegistry').default; //eslint-disable-line

  if (!initProperties) {
    initI18N().then(callback);
    return;
  }

  services.loadConfigurationProperties().then((properties) => {
    var configurationProperties = {};
    for (var prop in properties) {
      if (properties.hasOwnProperty(prop)) {
        configurationProperties[properties[prop].key] = properties[prop].value;
      }
    }

    utils.initializeConfigurationProperties(configurationProperties);

    return Promise.all([
      ft.isExternalPhotonAdaptersEnabled() ? services.loadAdapters() : Promise.resolve([]),

      initI18N()
    ]);

  }).then(([adapters]) => {

    utils.initializeAdapters(Object.values(adapters).sort(utils.templateSortFn).map((adapter) => {
      const customProperties = adapter.customProperties || DEFAULT_ADAPTERS[adapter.id];
      return {
        id: adapter.id,
        name: adapter.name,
        iconSrc: customProperties.icon.replace(/^\//, ''),
        endpointEditor: customProperties.endpointEditor,
        computeProfileEditor: customProperties.computeProfileEditor,
        networkProfileEditor: customProperties.networkProfileEditor,
        storageProfileEditor: customProperties.storageProfileEditor,
        endpointEditorType: customProperties.endpointEditorType
      };
    }));

    return Promise.all(Object.values(adapters)
        .filter((adapter) => adapter.customProperties && adapter.customProperties.uiLink)
        .map((adapter) => services.loadScript(adapter.customProperties.uiLink.replace(/^\//, ''))));

  }).then(callback).catch((err) => {
    console.warn('Error when loading configuration! Error: ', err);
  });
};

export default initializer;
