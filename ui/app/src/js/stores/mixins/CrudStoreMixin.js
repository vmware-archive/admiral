/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import utils from 'core/utils';

var CrudStoreMixin = {
  init: function() {
    this.pendingOperations = {};
    this.data = Immutable({});
  },

  setInData(path, value) {
    this.data = utils.setIn(this.data, path, value);
  },

  selectFromData(basePath) {
    var _this = this;
    return {
      get() {
        if (basePath.length === 0) {
          return _this.data;
        }
        return utils.getIn(_this.data, basePath);
      },

      parent() {
        if (basePath.length === 0) {
          throw new 'Cannot move up further';
        }
        return _this.selectFromData(basePath.slice(0, basePath.length - 1));
      },

      setIn(path, value) {
        path = basePath.concat(path);
        _this.data = utils.setIn(_this.data, path, value);
        return this;
      },

      getIn(path) {
        path = basePath.concat(path);
        return utils.getIn(_this.data, path);
      },

      select(path) {
        path = basePath.concat(path);
        return _this.selectFromData(path);
      },

      merge(value) {
        var merged;
        var current = utils.getIn(_this.data, basePath);
        if (current) {
          merged = current.merge(value);
        } else {
          merged = value;
        }
        _this.data = utils.setIn(_this.data, basePath, merged);
      },

      clear() {
        if (this.get()) {
          _this.data = utils.setIn(_this.data, basePath, null);
        }
      }
    };
  },

  emitChange: function() {
    this.trigger(this.data);
  },

  getData: function() {
    return this.data;
  },

  /**
   * Requests an instance of cancellable operation for an operation id and operation options.
   * This is useful when invoking something multiple times, should result in one completion.
   * It works like so:
   * 1. If there is already a pending operation with the same id and operation options, an instance
   * will not be returned as it is already in progress and wouldn't be necessary to invoke the same
   * operaiton again.
   * 2. If there is a pending operation with the same id, but for different options, it will be
   * cancelled, so that when it completes, it won't invoke the completion handler (Promise.then or
   * Promise.catch). A new operation for the current id and options will be returned, so that
   * an asynchronous request may be created.
   * 3. If there is no operation pending with the same id, then new operation for the current id
   * and options will be returned, so that an asynchronous request may be created.
   */
   requestCancellableOperation: function(operationId, operationOptions) {
    var pendingOperation = this.pendingOperations[operationId];
    if (pendingOperation) {
      if (utils.equals(pendingOperation.operationOptions, operationOptions)) {
        // No need
        return;
      } else {
        pendingOperation.cancel();
      }
    }

    var operation = new CancellableOperation(operationOptions, () => {
      this.pendingOperations[operationId] = null;
    });

    this.pendingOperations[operationId] = operation;

    return operation;
  },

  cancelOperation: function(operationId) {
    if (this.pendingOperations[operationId]) {
      this.pendingOperations[operationId].cancel();
      this.pendingOperations[operationId] = null;
    }
  },

  cancelOperations: function(...operationsIds) {
    for (var i = 0; i < operationsIds.length; i++) {
      this.cancelOperation(operationsIds[i]);
    }
  },

  cancelAllOperations: function() {
    for (var operationId in this.pendingOperations) {
      if (this.pendingOperations.hasOwnProperty(operationId)) {
        this.cancelOperation(operationId);
      }
    }
  }
};

class CancellableOperation {
  constructor(options, completeCallback) {
    this.options = options;
    this.completeCallback = completeCallback;
    this.promises = 0;
  }

  forPromise(promise) {
    if (this.completed) {
      throw new Error('Already completed');
    }
    this.promises++;
    return new Promise((resolve, reject) => {
      promise.then((...args) => {
        if (!this.cancelled && resolve) {
          resolve.apply(this, args);
        }
        this._promiseCountDown();
      }).catch((...args) => {
        if (!this.cancelled && reject) {
          reject.apply(this, args);
        }
        this._promiseCountDown();
      });
    });
  }

  _promiseCountDown() {
    this.promises--;
    if (this.promises === 0) {
      this.completeCallback();
    }
  }

  cancel() {
    this.cancelled = true;
  }
}

export default CrudStoreMixin;
