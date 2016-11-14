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

import Handlebars from 'handlebars/runtime';

Handlebars.registerHelper('i18n', function(i18n_key) {
  return i18n_key;
});

Handlebars.registerHelper('timestampToDate', function(timestamp) {
  return timestamp;
});

Handlebars.registerHelper('resourcePoolPercentageLevel', function(percentage) {
  return 'level-' + percentage;
});

var pendindRequests = [];
var completedRequests = [];

$.ajaxSetup({
  beforeSend: function(xhr, settings) {
    pendindRequests.push(xhr);
    xhr.jasmineURL = settings.url;
    xhr.jasmineMethod = settings.method;
  },
  complete: function(xhr) {
    var pendindIndex = pendindRequests.indexOf(xhr);
    if (pendindIndex > -1) {
      pendindRequests.splice(pendindIndex, 1);
      completedRequests.push(xhr);
    }
  }
});

var printAjaxRequests = function() {
  console.log("[AJAX Failure trace] Start printing all requests that were made during the spec execution");
  for (var p = 0; p < pendindRequests.length; p++) {
    var request = pendindRequests[p];
    console.log("   " + request.jasmineMethod + " to [" + request.jasmineURL + "] did not complete.");
  }

  for (var c = 0; c < completedRequests.length; c++) {
    var request = completedRequests[c];
    console.log("   " + request.jasmineMethod + " to [" +  request.jasmineURL + "] completed with statusCode and statusText [" + request.status + ", " + request.statusText + "].");
  }

  console.log("[AJAX Failure trace] Finnished printing all requests");
};

var failureAjaxReporter = {
  specStarted: function(result) {
    pendindRequests = [];
    completedRequests = [];
  },
  specDone: function(result) {
    if (result.status == 'failed') {
      printAjaxRequests();
    }
  }
};

jasmine.getEnv().addReporter(failureAjaxReporter);

/**
 * Uncomment this, when debugging issues and want to trace the callers of actions. As by default
 * they are asynchronous, this will make them synchronous and you can observe the call stack.
 * Not enabled by default, as the standalone application works with asynchronous actions, so it is
 * nice to simulate real behavior.
 */
//Reflux.nextTick(function(callback) {
//  callback();
//});
