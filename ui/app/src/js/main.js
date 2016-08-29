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

requirejs.config({baseUrl: ''});

var $loadingEl = $('body > .loading');

requirejs(['js/all', 'template-assets/all', 'template-assets/all-vue'], function() {
  requirejs(['core/initializer'], function(initializer) {
    initializer.init(function() {
      var applicationRequirements = ['components/App', 'stores/AppStore'];

      var locationSearch = window.location.search || '';
      if (locationSearch.indexOf('compute') !== -1) {
        applicationRequirements = ['components/AppCompute', 'stores/AppComputeStore'];
      }
      requirejs(applicationRequirements, function(App, Store) {
        var app = new App($('#main'));
        Store.listen(function(data) {
          if ($loadingEl) {
            $loadingEl.remove();
            $loadingEl = null;
          }

          app.setData(data);
        });
      });
    });
  });
});
