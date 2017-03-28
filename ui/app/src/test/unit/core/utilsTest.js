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

describe("utils test", function() {
  describe("setIn", function() {
    it("should set value of a nested immutable object", function() {
      var obj = {
        innerA: {
          innerA1: {
            prop1 : "value1"
          }
        }
      };

      var immutableObj = Immutable(obj);
      var actual = utils.setIn(immutableObj, ['innerA', 'innerA1', 'prop2'], "value2").asMutable();

      var expected = {
        innerA: {
          innerA1: {
            prop1 : "value1",
            prop2 : "value2"
          }
        }
      };

      // Using the string representation to check for equality in nested JSONs
      expect(JSON.stringify(actual)).toEqual(JSON.stringify(expected));
    });

    it("should construct missng objects from path and set value of a nested immutable", function() {
      var obj = {
        innerA: {
        }
      };

      var immutableObj = Immutable(obj);
      var actual = utils.setIn(immutableObj, ['innerA', 'innerA1', 'prop1'], "value1").asMutable();

      var expected = {
        innerA: {
          innerA1: {
            prop1 : "value1"
          }
        }
      };

      // Using the string representation to check for equality in nested JSONs
      expect(JSON.stringify(actual)).toEqual(JSON.stringify(expected));
    });

    it("should override a value of a nested immutable object", function() {
      var obj = {
        innerA: {
          innerA1: {
            prop1 : "value1"
          }
        }
      };

      var immutableObj = Immutable(obj);
      var innerA1New = {
        prop2: "value2"
      };
      var actual = utils.setIn(immutableObj, ['innerA', 'innerA1'], innerA1New).asMutable();

      var expected = {
        innerA: {
          innerA1: {
            prop2 : "value2"
          }
        }
      };

      // Using the string representation to check for equality in nested JSONs
      expect(JSON.stringify(actual)).toEqual(JSON.stringify(expected));
    });
  });

  describe("uriToParams", function() {
    it("should return empty object on empty uri", function() {
      var params = utils.uriToParams("")
      expect(Object.keys(params).length).toEqual(0);
    });
    it("should return one entry", function() {
      var params = utils.uriToParams("varA=valA")
      expect(Object.keys(params).length).toEqual(1);
      expect(params.varA).toEqual("valA");
    });
    it("should return one entry without value", function() {
      var params = utils.uriToParams("varA")
      expect(Object.keys(params).length).toEqual(1);
      expect(params.varA).toBeNull();
    });
    it("should return multiple entries", function() {
      var params = utils.uriToParams("varA=valA&varB=valB")
      expect(Object.keys(params).length).toEqual(2);
      expect(params.varA).toEqual("valA");
      expect(params.varB).toEqual("valB");
    });
  });

  describe("getURLParts", function() {
    it("should return URL parts", function() {
      var parts = utils.getURLParts("192.168.1.1");
      expect(parts.host).toEqual("192.168.1.1");
      expect(parts.port).toBeFalsy();

      parts = utils.getURLParts("192.168.1.1:3000");
      expect(parts.host).toEqual("192.168.1.1");
      expect(parts.port).toEqual("3000");

      parts = utils.getURLParts("http://192.168.1.1:3000");
      expect(parts.scheme).toEqual("http");
      expect(parts.host).toEqual("192.168.1.1");
      expect(parts.port).toEqual("3000");

      parts = utils.getURLParts("https://192.168.1.1:3000/path?query");
      expect(parts.scheme).toEqual("https");
      expect(parts.host).toEqual("192.168.1.1");
      expect(parts.port).toEqual("3000");
      expect(parts.path).toEqual("/path");
      expect(parts.query).toEqual("query");
    });

    it("should return hostname", function() {
      expect("192.168.1.1").toEqual(utils.getURLParts("192.168.1.1:3000").host);
      expect("192.168.1.1").toEqual(utils.getURLParts("http://192.168.1.1:3000").host);
      expect("192.168.1.1").toEqual(utils.getURLParts("192.168.1.1").host);
    });
  });

  describe("mergeURLParts", function() {
    it("should concatenate URL parts", function() {
      var parts = utils.getURLParts("192.168.1.1");
      var mergedParts = utils.mergeURLParts(parts);
      expect(mergedParts).toEqual("192.168.1.1");

      /* Do not concatenate '/' at the end */
      var parts = utils.getURLParts("192.168.1.1:3000/");
      var mergedParts = utils.mergeURLParts(parts);
      expect(mergedParts).toEqual("192.168.1.1:3000");

      var parts = utils.getURLParts("https://192.168.1.1:3000/path?query");
      var mergedParts = utils.mergeURLParts(parts);
      expect(mergedParts).toEqual("https://192.168.1.1:3000/path?query");
    });
  });

  describe("populateDefaultSchemeAndPort", function() {
    it("alter url with default scheme and port if missing", function() {
      /* if no hostname, return the same string */
      var uri = utils.populateDefaultSchemeAndPort("       ");
      expect(uri).toEqual("       ");

      var uri = utils.populateDefaultSchemeAndPort("192.168.1.1/api");
      expect(uri).toEqual("https://192.168.1.1:443/api");

      var uri = utils.populateDefaultSchemeAndPort("192.168.1.1:5000/api");
      expect(uri).toEqual("https://192.168.1.1:5000/api");

      var uri = utils.populateDefaultSchemeAndPort("https://192.168.1.1/api");
      expect(uri).toEqual("https://192.168.1.1:443/api");

      var uri = utils.populateDefaultSchemeAndPort("http://192.168.1.1/api");
      expect(uri).toEqual("http://192.168.1.1:80/api");

      var uri = utils.populateDefaultSchemeAndPort("https://192.168.1.1:2376/api");
      expect(uri).toEqual("https://192.168.1.1:2376/api");
    });
  });

  describe('getVersionNumber', function() {
    it('should return the extracted version number from the build number', function() {
      var props = {};
      utils.initializeConfigurationProperties(props);

      props['__build.number'] = '1';
      expect(utils.getVersionNumber()).toEqual('1');

      props['__build.number'] = '1.9';
      expect(utils.getVersionNumber()).toEqual('1.9');

      props['__build.number'] = '1.9.1';
      expect(utils.getVersionNumber()).toEqual('1.9.1');

      props['__build.number'] = '1.9.10';
      expect(utils.getVersionNumber()).toEqual('1.9.10');

      props['__build.number'] = '1.9.10 (65)';
      expect(utils.getVersionNumber()).toEqual('1.9.10');

      props['__build.number'] = '1.9.10-65';
      expect(utils.getVersionNumber()).toEqual('1.9.10');

      props['__build.number'] = '1.9.10 65';
      expect(utils.getVersionNumber()).toEqual('1.9.10');

      props['__build.number'] = 'N1.9.10-65';
      expect(utils.getVersionNumber()).toBeFalsy();

      props['__build.number'] = '';
      expect(utils.getVersionNumber()).toBeFalsy();
    });
  });

  describe('extractHarborRedirectUrl', function() {
    it('should extract and decode harbor redirect url', function() {
      var redirectUrl = 'http://my-harbor.com/sample';
      var urlEncoded = encodeURIComponent(redirectUrl);
      var newUrl = location.protocol + '//' + location.host + '/?harbor_redirect_url=' + urlEncoded + '#/containers';
      window.history.replaceState({}, document.title, newUrl);
      var actualRedirectUrl = utils.extractHarborRedirectUrl();
      expect(actualRedirectUrl).toEqual(redirectUrl);
      expect(window.location.hash).toEqual('#/containers');


      newUrl = location.protocol + '//' + location.host + '/?harbor_redirect_url=' + urlEncoded + '#/containers?name=docker';
      window.history.replaceState({}, document.title, newUrl);
      actualRedirectUrl = utils.extractHarborRedirectUrl();
      expect(actualRedirectUrl).toEqual(redirectUrl);
      expect(window.location.hash).toEqual('#/containers?name=docker');
    });
  });

  describe('prepareHarborRedirectUrl', function() {
    it('should prepare and encode harbor redirect url of admiral', function() {
      window.location.hash = '#/containers';
      var actualRedirectUrl = utils.prepareHarborRedirectUrl('http://my-harbor.com/sample');
      expect(actualRedirectUrl)
        .toEqual('http://my-harbor.com/sample?admiral_redirect_url=' +
                 encodeURIComponent(window.location.href));

      actualRedirectUrl = utils.prepareHarborRedirectUrl('http://my-harbor.com/sample?name=test');
      expect(actualRedirectUrl)
        .toEqual('http://my-harbor.com/sample?name=test&admiral_redirect_url=' +
                 encodeURIComponent(window.location.href));
    });
  });
});