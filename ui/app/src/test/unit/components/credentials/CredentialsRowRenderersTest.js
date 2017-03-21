/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import CertificatesStore from 'components/credentials/CredentialsRowRenderers';
import Handlebars from 'handlebars/runtime';
import constants from 'core/constants';

describe('CredentialsRowRenderers test', function() {
  describe('displayableCredentials', function() {
    it('should escape html strings', function() {
      var credentials = Handlebars.helpers.displayableCredentials({
        type: constants.CREDENTIALS_TYPE.PASSWORD,
        username: '<span>user</span>',
        password: '<span>pass</span>',
      });
      expect(credentials).toEqual('&lt;span&gt;user&lt;/span&gt; / *****************');

      credentials = Handlebars.helpers.displayableCredentials({
        type: constants.CREDENTIALS_TYPE.PRIVATE_KEY,
        username: '<span>user</span>',
        privateKey: '<span>private</span>',
      });
      expect(credentials).toEqual('&lt;span&gt;user&lt;/span&gt; / <div class="truncateText">&lt;span&gt;private&lt;/span&gt;</div>');

      credentials = Handlebars.helpers.displayableCredentials({
        type: constants.CREDENTIALS_TYPE.PRIVATE_KEY,
        username: '<span>user</span>',
        privateKey: 's2enc~encrypted',
      });
      expect(credentials).toEqual('&lt;span&gt;user&lt;/span&gt; / <div class="truncateText">****************</div>');

      credentials = Handlebars.helpers.displayableCredentials({
        type: constants.CREDENTIALS_TYPE.PUBLIC_KEY,
        publicKey: '<span>public</span>',
        privateKey: '<span>private</span>',
      });
      expect(credentials).toEqual(
        '<div class="truncateText">&lt;span&gt;public&lt;/span&gt;</div>' +
        '<div class="truncateText">&lt;span&gt;private&lt;/span&gt;</div>');

      credentials = Handlebars.helpers.displayableCredentials({
        type: constants.CREDENTIALS_TYPE.PUBLIC_KEY,
        publicKey: '<span>public</span>',
        privateKey: 's2enc~encrypted',
      });
      expect(credentials).toEqual(
        '<div class="truncateText">&lt;span&gt;public&lt;/span&gt;</div>' +
        '<div class="truncateText">****************</div>');

      credentials = Handlebars.helpers.displayableCredentials({
        type: constants.CREDENTIALS_TYPE.PUBLIC,
        publicKey: '<span>public</span>',
      });
      expect(credentials).toEqual(
        '<div class="truncateText">&lt;span&gt;public&lt;/span&gt;</div>');

      credentials = Handlebars.helpers.displayableCredentials({
        type: '<span>unknown</span>'
      });
      expect(credentials).toEqual('Unknown [&lt;span&gt;unknown&lt;/span&gt;]');
    });
  });
});