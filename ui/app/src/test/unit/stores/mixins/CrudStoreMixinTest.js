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

import CrudStoreMixin from 'stores/mixins/CrudStoreMixin';

describe("ContainerStore test", function() {
  it("should allow only one operation to be invoked for the same options", function() {
    var store = Reflux.createStore({
      mixins: [CrudStoreMixin]
    });

    var operation = store.requestCancellableOperation('operation');
    expect(operation).toBeDefined();

    operation = store.requestCancellableOperation('operation');
    expect(operation).toBeUndefined();
  });

  it("should cancel previous operation for different options", function() {
    var store = Reflux.createStore({
      mixins: [CrudStoreMixin]
    });

    var oldOperation = store.requestCancellableOperation('operation', {optionA: 'valA'});
    expect(oldOperation).toBeDefined();
    expect(oldOperation.cancelled).toBeUndefined();

    var newOperation = store.requestCancellableOperation('operation', {optionA: 'valB'});
    expect(newOperation).toBeDefined();
    expect(newOperation.cancelled).toBeUndefined();

    expect(oldOperation.cancelled).toBe(true);
  });

  it("should complete by default", function(done) {
    var store = Reflux.createStore({
      mixins: [CrudStoreMixin]
    });

    var operation = store.requestCancellableOperation('operation', {optionA: 'valA'});

    var trigger = {};
    var p = new Promise(function(resolve, reject) {
      trigger.resolve = resolve;
      trigger.reject = reject;
    });

    var succeeded = false;

    operation.forPromise(p).then(function() {
      succeeded = true;
    }).catch(function() {
      completed = true;
      done.fail("shouldn't have failed");
    });

    trigger.resolve();

    setTimeout(function() {
      expect(succeeded).toBe(true);
      done();
    }, 0);
  });

  it("cancelled operation should not complete", function(done) {
    var store = Reflux.createStore({
      mixins: [CrudStoreMixin]
    });

    var operation = store.requestCancellableOperation('operation', {optionA: 'valA'});

    var trigger = {};
    var p = new Promise(function(resolve, reject) {
      trigger.resolve = resolve;
      trigger.reject = reject;
    })

    var completed = false;

    operation.forPromise(p).then(function() {
      completed = true;
      done.fail("shouldn't have succeeded");
    }).catch(function() {
      completed = true;
      done.fail("shouldn't have failed");
    });

    operation.cancel();

    trigger.resolve();

    setTimeout(function() {
      expect(completed).toBe(false);
      done();
    }, 0);
  });
});