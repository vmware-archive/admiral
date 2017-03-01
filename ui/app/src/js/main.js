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

/*
  Entry point of the application. Here we make initialization tasks. And then pass on the App.js
*/

import initializer from 'core/initializer';
import utils from 'core/utils';
import DefaultTemplate from 'components/headers/DefaultTemplate.html';
import VICTemplate from 'components/headers/VICTemplate.html';

var $loadingEl = $('body > .loading');

var appInitializer = function(App, Store) {
  var app = new App($('#main'));
  Store.listen(function(data) {
    if ($loadingEl) {
      $loadingEl.remove();
      $loadingEl = null;
    }

    app.setData(data);
  });
};

var updateHeaderLink = function(registryUrl) {
  if (utils.isApplicationEmbedded() ||
      utils.isApplicationSingleView()) {
      return;
  }
  if (registryUrl) {
    $('body').append($(VICTemplate()));
    $('body > .header .registry-link').attr('href', registryUrl);
    $('body > .header #search_input').change(function(e) {
      window.location.hash = '#/templates?any=' + this.value;
      e.preventDefault();
      e.stopImmediatePropagation();
    });
    $('body > .header #search_input').keydown(function(event) {
      if (event.keyCode === 13) {
        window.location.hash = '#/templates?any=' + this.value;
        event.preventDefault();
        return false;
      }
    });
    $('head title').html('VIC');
  } else {
    $('body').append($(DefaultTemplate()));
    $('head title').html('Admiral');
  }
};

initializer.init(() => {
  var locationSearch = window.location.search || '';
  if (locationSearch.indexOf('compute') !== -1) {
    appInitializer(require('components/AppCompute').default,
                   require('stores/AppComputeStore').default);
  } else {
    appInitializer(require('components/App').default,
                     require('stores/AppStore').default);
  }

  updateHeaderLink(utils.getConfigurationProperty('vic.registry.tab.url'));
});
