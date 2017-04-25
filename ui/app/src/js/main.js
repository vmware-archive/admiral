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

var updateHeader = function() {
  if (utils.isApplicationEmbedded() ||
    utils.isApplicationSingleView()
    || utils.isNavigationLess()) {
    return;
  }

  if (utils.isVic()) {
    $('body').append($(VICTemplate()));
    $('head title').text('vSphere Integrated Containers');
    $('head').append('<link rel="icon" type="image/x-icon" href="image-assets/vic-favicon.ico" />');
  } else {
    $('body').append($(DefaultTemplate()));
    $('head title').text('Admiral');
    $('head').append('<link rel="icon" type="image/x-icon" href="image-assets/favicon.ico" />');
  }

  var baseRegistryUrl = utils.getHarborTabUrl();
  var redirectUrl = utils.extractHarborRedirectUrl();
  var registryUrl = redirectUrl || baseRegistryUrl;

  if (registryUrl) {
    $('body > .header .registry-link').attr('href', registryUrl).click(function(e) {
      e.preventDefault();

      window.location.href = utils.prepareHarborRedirectUrl(registryUrl);

      return false;
    });
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

  updateHeader();
});
