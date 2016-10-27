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

import utils from 'core/utils';

var docs = {};

var token;
var clientToken;

const ENSEMBLE_URL = 'https://ensemble.vmware.com';
const PRODUCT_NAME = 'Admiral';

const DOCS_TOKENS_SEPARATOR = '__Admiral__';

var retrieveTokensFromStorage = function() {
  var docsTokens = localStorage.docsTokens || '';
  var separatorIndex = docsTokens.indexOf(DOCS_TOKENS_SEPARATOR);
  if (separatorIndex !== -1) {
    token = docsTokens.substring(0, separatorIndex);
    clientToken = docsTokens.substring(separatorIndex + DOCS_TOKENS_SEPARATOR.length);
  }
};

var saveTokensToStorage = function() {
  if (token && clientToken) {
    localStorage.docsTokens = token + DOCS_TOKENS_SEPARATOR + clientToken;
  } else {
    localStorage.docsTokens = null;
  }
};

retrieveTokensFromStorage();

var ajax = function(method, url, data) {
  return $.ajax({
    method: method,
    url: url,
    dataType: 'json',
    data: data,
    contentType: 'application/json',
    accepts: {
      json: 'application/json'
    },
    headers: {'X-Client-Token': clientToken}
  });
};

var getUpdateUrl = function() {
  if (token) {
    return ENSEMBLE_URL + '/secondScreen/api/update/' + token;
  }
};


var getToken = function(callback) {
  clientToken = utils.uuid();
  ajax('POST', ENSEMBLE_URL + '/secondScreen/api/token').done((data) => {
    token = data.token;
    saveTokensToStorage();

    callback(token);

    docs.update('/' + hasher.getHash());
  }).fail(() => {
    console.log('Error');
  });
};

var validateToken = function(callback) {
  if (!token) {
    throw new Error('No token');
  }
  ajax('POST', ENSEMBLE_URL + '/secondScreen/api/token/validation/' + token).done((data) => {
    if (data.isValid) {
      callback(token);
    } else {
      token = data.newToken;
      saveTokensToStorage();

      callback(token);

      docs.update('/' + hasher.getHash());
    }
  }).fail(() => {
    console.log('Error');
  });
};

docs.getToken = function(callback) {
  if (token) {
    validateToken(callback);
  } else {
    getToken(callback);
  }
};

docs.getHelpUrlAndImage = function(callback) {
  this.getToken((token) => {
    var result = {
      qrUrl: ENSEMBLE_URL + '/secondScreen/api/qr/' + token,
      helpUrl: ENSEMBLE_URL + '/#/registerTempUser?token=' + token
    };
    callback(result);
  });
};

docs.update = function(id) {
  var updateUrl = getUpdateUrl();
  if (updateUrl) {
    var data = {
      productName: PRODUCT_NAME,
      functionalityId: id
    };
    ajax('POST', updateUrl, JSON.stringify(data));
  }
};

docs.release = function() {
  if (token) {
    ajax('DELETE', ENSEMBLE_URL + '/secondScreen/api/token/' + token);
    token = null;
    clientToken = null;

    saveTokensToStorage();
  }
};

docs.checkIfAvailable = function(callback) {
  $.ajax(ENSEMBLE_URL).done((data, textStatus, jqXHR) => {
    if (jqXHR.status === 200) {
      utils.setDocsAvailable(true);
    }
    callback();
  }).fail(() => {
    callback();
  });
};

export default docs;
