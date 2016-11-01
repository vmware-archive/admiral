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

import CredentialsRowTemplate from 'CredentialsRowTemplate';
import CredentialsRowHighlightTemplate from 'CredentialsRowHighlightTemplate';
import constants from 'core/constants';
import utils from 'core/utils';

const MAX_TITLE_KEY_LENGTH = 40;

var renderers = {
  render: function(credentialObject) {
    return $(CredentialsRowTemplate(credentialObject));
  },

  renderHighlighted: function(credentialObject, $credentialsRow, isNew, isUpdated,
      validationErrors) {
    var model = {
      credentialsRow: $credentialsRow.html(),
      name: credentialObject.name,
      type: credentialObject.type,
      credentials: credentialObject.credentials,
      isNew: isNew,
      isUpdated: isUpdated,
      validationErrors: validationErrors
    };
    return $(CredentialsRowHighlightTemplate(model));
  }
};

var truncateContent = function(content) {
  content = content || '';

  if (content.length > MAX_TITLE_KEY_LENGTH) {
    content = content.substring(0, MAX_TITLE_KEY_LENGTH) + '...';
  }

  return content;
};

Handlebars.registerHelper('displayableCredentials', function(credentialObject) {

  if (credentialObject.type === constants.CREDENTIALS_TYPE.PASSWORD) {
    return getUsernamePasswordString(credentialObject.username, credentialObject.password);

  } else if (credentialObject.type === constants.CREDENTIALS_TYPE.PRIVATE_KEY) {
    return credentialObject.username + ' / '
      + '<div class="truncateText">'
            + utils.maskValueIfEncrypted(credentialObject.privateKey) + '</div>';

  } else if (credentialObject.type === constants.CREDENTIALS_TYPE.PUBLIC_KEY) {
    return '<div class="truncateText">' + credentialObject.publicKey + '</div>' +
      '<div class="truncateText">'
            + utils.maskValueIfEncrypted(credentialObject.privateKey) + '</div>';

  } else {
    return 'Unknown [' + credentialObject.type + ']';
  }
});

Handlebars.registerHelper('displayableTitleCredentials', function(credentialObject) {

  if (credentialObject.type === constants.CREDENTIALS_TYPE.PASSWORD) {
    return getUsernamePasswordString(credentialObject.username, credentialObject.password);

  } else if (credentialObject.type === constants.CREDENTIALS_TYPE.PRIVATE_KEY) {
    let privateKey = truncateContent(utils.maskValueIfEncrypted(credentialObject.privateKey));

    return credentialObject.username + '\n' + privateKey;

  } else if (credentialObject.type === constants.CREDENTIALS_TYPE.PUBLIC_KEY) {
    let privateKey = truncateContent(utils.maskValueIfEncrypted(credentialObject.privateKey));
    let publicKey = truncateContent(credentialObject.publicKey);

    return privateKey + '\n' + publicKey;

  } else {
    return 'Unknown [' + credentialObject.type + ']';
  }
});

Handlebars.registerHelper('displayableCredentialsType', function(credentialObjectType) {

  if (credentialObjectType === constants.CREDENTIALS_TYPE.PASSWORD
    || credentialObjectType === constants.CREDENTIALS_TYPE.PRIVATE_KEY) {
    return i18n.t('app.credential.edit.usernamePasswordTitle');

  } else if (credentialObjectType === constants.CREDENTIALS_TYPE.PUBLIC_KEY) {
    return i18n.t('app.credential.edit.certificateTitle');

  } else {
    return 'Unknown [' + credentialObjectType + ']';
  }
});

var getUsernamePasswordString = function(username, password) {
  var passwordText = '******';
  if (password) {
    // Replaces every characted with *, keeping the length of the actual password
    passwordText = password.replace(/./g, '*');
  }

  return username + ' / ' + passwordText;
};

export default renderers;
