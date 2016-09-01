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
  Entry point of the single view application. Here we make initialization tasks. And then pass on
  the SingleView.js
*/

requirejs.config(
  {
    baseUrl: '',
    waitSeconds: 60
  });

var $loadingEl = $('body > .loading');

requirejs(['js/all', 'template-assets/all', 'template-assets/all-vue'], function() {
  requirejs(['core/initializer'], function(initializer) {
    initializer.init(function() {
      requirejs(['components/SingleView'], function(SingleView) {
        $loadingEl.remove();
        new SingleView($('#main')); //eslint-disable-line
      });
    });
  });
});
