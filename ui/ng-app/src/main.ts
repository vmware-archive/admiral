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

I18n.use(I18nXhrBackend)
  .use(I18nLanguageDetector)
  .init({
    ns: ['admiral', 'kubernetes', 'base'],
    defaultNS: 'admiral',
    fallbackLng: 'en',
    backend: {
      loadPath: '/assets/i18n/{{ns}}.{{lng}}.json'
    }
  },() => {
    var xhr = new XMLHttpRequest();
    xhr.onreadystatechange = function() {
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
    xhr.open('GET', Links.CONFIG_PROPS + '?expand=true', true);
    xhr.send(null);
  });