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

(function(global) {
  var CHECK_INTERVAL = 20;
  // Very close to the jasmine default timeout, so that it fails (if timesout) before the test timeout.
  jasmine.DEFAULT_TIMEOUT_INTERVAL = 60000;
  var SMALL_TIMEOUT_INTERVAL = global.jasmine.DEFAULT_TIMEOUT_INTERVAL - 200;
  var TIMES_TO_CHECK = Math.floor(SMALL_TIMEOUT_INTERVAL / CHECK_INTERVAL);

  var getFnBody = function(fn) {
    var entire = fn.toString();
    return entire.substring(entire.indexOf("{") + 1, entire.lastIndexOf("}"));
  };

  var getErrorMessage = function(conditionMet) {
    var conditionString = conditionMet ? " [" + getFnBody(conditionMet) + "] " : " ";

    return "Condition" + conditionString + "was not met within the " + SMALL_TIMEOUT_INTERVAL + " ms timeout";
  };

  var _doWaitFor = function(conditionMet, resolve, reject, checkIndex) {
    var conditionMetResult = false;
    try {
      conditionMetResult = conditionMet();
    } catch (e) {
      // For easier check, sometimes may throw error, we consider this as that the condition is not met.
    }
    if (conditionMetResult) {
      resolve();
    } else if (checkIndex >= TIMES_TO_CHECK) {
      var errorMsg = getErrorMessage(conditionMet);
      console.error(errorMsg);
      reject(new Error(errorMsg));
    } else {
      setTimeout(function() {
        _doWaitFor(conditionMet, resolve, reject, checkIndex + 1);
      }, CHECK_INTERVAL);
    }
  };

  var testUtils = global.testUtils = {};

  testUtils.waitFor = function(conditionMet) {
    return new Promise(function(resolve, reject) {
      _doWaitFor(conditionMet, resolve, reject, 0);
    });
  };

  testUtils.waitForListenable = function(listenable, conditionMet) {
    return new Promise(function(resolve, reject) {
      var conditionMetResult = false;

      var removeListener = listenable.listen(function() {
        var args = Array.prototype.slice.call(arguments);
        try {
          conditionMetResult = conditionMet ? conditionMet.apply(null, args) : true;
        } catch (e) {
          // For easier check, sometimes may throw error, we consider this as that the condition is not met.
        }
        if (conditionMetResult) {
          removeListener();
          resolve.apply(null, args);
        }
      });

      setTimeout(function() {
        if (!conditionMetResult) {
          var errorMsg = getErrorMessage(conditionMet);
          console.error(errorMsg);
          reject(new Error(errorMsg));
        }
      }, SMALL_TIMEOUT_INTERVAL);
    });
  };

})(window);
