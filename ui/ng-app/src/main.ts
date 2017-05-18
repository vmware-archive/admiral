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

import './polyfills.ts';

import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { enableProdMode, ReflectiveInjector } from '@angular/core';
import { environment } from './environments/environment';
import { AppModule } from './app/app.module';
import { Utils } from './app/utils/utils';
import { Links } from './app/utils/links';
import * as I18n from 'i18next';
import * as I18nXhrBackend from 'i18next-xhr-backend';
import * as I18nLanguageDetector from 'i18next-browser-languagedetector';

if (environment.production) {
  enableProdMode();
}

// Initialize the application
function initApp() {
  // Initialize the internationalization
  I18n.use(I18nXhrBackend)
    .use(I18nLanguageDetector)
    .init({
        ns: ['admiral', 'kubernetes', 'base'],
        defaultNS: 'admiral',
        fallbackLng: 'en',
        backend: {
            loadPath: 'assets/i18n/{{ns}}.{{lng}}.json'
        }
    }, () => {
      // Load configuration
      var xhr = new XMLHttpRequest();

      xhr.onreadystatechange = function () {
        if (xhr.readyState == XMLHttpRequest.DONE) {
          let properties = JSON.parse(xhr.responseText).documents;

          var configurationProperties = {};
          for (var prop in properties) {
            if (properties.hasOwnProperty(prop)) {

              configurationProperties[properties[prop].key] = properties[prop].value;
            }
          }

          Utils.initializeConfigurationProperties(configurationProperties);
          platformBrowserDynamic().bootstrapModule(AppModule);
        }
      };

      let configPropsUrl = Links.CONFIG_PROPS;
      if (window['getBaseServiceUrl']) {
        configPropsUrl = window['getBaseServiceUrl'](configPropsUrl);
      }

      xhr.open('GET', configPropsUrl + '?expand=true&documentType=true', true);
      xhr.setRequestHeader('Accept', 'application/json');
      xhr.send(null);
  });
}

// Load main script asynchronously so that the ones that embed can inject things onload
function loadScript(retries) {

  if (window['getBaseServiceUrl'] ||  retries === 0) {
    initApp();

  } else {

    setTimeout(function() {
      loadScript(retries - 1);
    }, 50);
  }
}

if (window.parent) {
  // The application has been embedded - wait until loaded before initializing
  loadScript(50);
} else {
  // Standalone - initialize immediately
  initApp();
}
