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

import HostsStore from 'stores/HostsStore';
import { HostActions } from 'actions/Actions';
import services from 'core/services';

describe("HostsStore test", function() {

  var waitForData = function(conditionFn) {
    return testUtils.waitForListenable(HostsStore, conditionFn);
  };

  describe("add host", function() {

    it("should give validation errors on empty fields", function(done) {
      var emptyHostModel = {
        address: "",
        resourcePool: null,
        credential: null,
        connectionType: null
      };

      waitForData((data) => {
        return !!data.hostAddView.validationErrors;
      }).then(function(data) {
        var validationErrors = data.hostAddView.validationErrors;

        expect(validationErrors).toBeDefined();
        expect(validationErrors).not.toBe(null);

        expect(validationErrors.address).toBe('errors.required');
        expect(validationErrors.resourcePool).toBe('errors.required');

        var onlyAddressEmptyHostModel = {
          address: "",
          resourcePool: {documentSelfLink: "someResourcePoolRef"},
          credential: {documentSelfLink: "someCredentialRef"},
          connectionType: "someConnectionType"
        };

        var p = waitForData();
        HostActions.addHost(onlyAddressEmptyHostModel);

        return p;
      }).then(function(data) {
        var validationErrors = data.hostAddView.validationErrors;

        expect(validationErrors).toBeDefined();
        expect(validationErrors).not.toBe(null);

        expect(validationErrors.address).toBe('errors.required');
        expect(validationErrors.resourcePool).toBeUndefined();
        expect(validationErrors.credential).toBeUndefined();
        expect(validationErrors.connectionType).toBeUndefined();
      }).then(done);

      HostActions.addHost(emptyHostModel);
    });

    it("should give validation error on incorrect address", function(done) {
      var incorrectAddressHostModel = {
        resourcePool: {documentSelfLink: "someResourcePoolRef"},
        credential: {documentSelfLink: "someCredentialRef"},
        connectionType: "someConnectionType"
      };

      waitForData().then(function(data) {
        var validationErrors = data.hostAddView.validationErrors;
        expect(validationErrors.address).toBe('errors.hostIp');
      }).then(function() {
        incorrectAddressHostModel.address = "incorrect address";

        var p = waitForData().then(function(data) {
          var validationErrors = data.hostAddView.validationErrors;
          expect(validationErrors.address).toBe('errors.hostIp');
        });

        HostActions.addHost(incorrectAddressHostModel);
        return p;
      }).then(function() {
        incorrectAddressHostModel.address = "addres:101010101";

        var p = waitForData().then(function(data) {
          var validationErrors = data.hostAddView.validationErrors;
          expect(validationErrors.address).toBe('errors.hostIp');
        });

        HostActions.addHost(incorrectAddressHostModel);
        return p;
      }).then(function() {
        incorrectAddressHostModel.address = "addres:101010101";

        var p = waitForData().then(function(data) {
          var validationErrors = data.hostAddView.validationErrors;
          expect(validationErrors.address).toBe('errors.hostIp');
        });

        HostActions.addHost(incorrectAddressHostModel);
        return p;
      }).then(function() {
        incorrectAddressHostModel.address = "httpsX://test.com";

        var p = waitForData().then(function(data) {
          var validationErrors = data.hostAddView.validationErrors;
          expect(validationErrors.address).toBe('errors.hostIp');
        });

        HostActions.addHost(incorrectAddressHostModel);
        return p;
      }).then(done);

      incorrectAddressHostModel.address = "incorrect,address";
      HostActions.addHost(incorrectAddressHostModel);
    });

    it("should prompt user to accept self-signed certificate", function(done) {
      var hostModel = {
        address: "my-docker.com",
        resourcePool: {documentSelfLink: "someResourcePoolRef"},
        credential: {documentSelfLink: "someCredentialRef"},
        connectionType: "someConnectionType",
        customProperties: []
      };

      var CERT = "-----BEGIN CERTIFICATE-----"; // for testing purposes, not a real PEM certificate.
      var certificateHolder = {
        commonName: "my-docker.com",
        issuerName: "my-docker.com",
        certificate: CERT
      };

      spyOn(services, 'addHost').and.callFake(function(params) {
        return new Promise(function(resolve, reject){
          resolve(certificateHolder);
        });
      });

      waitForData((data) => {
        return !!data.hostAddView.shouldAcceptCertificate;
      }).then(function(data) {
        var shouldAcceptCertificate = data.hostAddView.shouldAcceptCertificate;
        expect(shouldAcceptCertificate).toBeDefined();
        expect(shouldAcceptCertificate).not.toBe(null);

        expect(shouldAcceptCertificate.certificateHolder).toEqual(certificateHolder);
      }).then(done);

      HostActions.addHost(hostModel);
    });

    it("should NOT prompt user to accept known certificate and add host", function(done) {
      var hostModel = {
        address: "docker.com",
        resourcePool: {documentSelfLink: "someResourcePool"},
        credential: {documentSelfLink: "someCredentialRef"},
        descriptionLink: "someDescLink",
        connectionType: "API",
        customProperties: []
      };

      var expectedDtoHost = {
        hostState: {
          id: hostModel.address,
          address: hostModel.address,
          resourcePoolLink: hostModel.resourcePoolLink,
          descriptionLink: "someDescLink",
          customProperties: {
            __authCredentialsLink: hostModel.credential.documentSelfLink,
            __adapterDockerType: hostModel.connectionType
          },
          powerState: hostModel.powerState
        }
      };

      var addHostParams = null;
      spyOn(services, 'addHost').and.callFake(function(params) {
        return new Promise(function(resolve, reject){
          addHostParams = params;
          resolve();
        });
      });

      testUtils.waitFor(function() {
        return addHostParams != null;
      }).then(function() {
        expect(addHostParams).toEqual(expectedDtoHost);
        done();
      });

      HostActions.addHost(hostModel);
    });

    it("should add host when accept certificate", function(done) {
      var hostModel = {
        address: "my-docker.com",
        resourcePool: {documentSelfLink: "someResourcePoolRef"},
        credential: {documentSelfLink: "someCredentialRef"},
        descriptionLink: "someDescLink",
        connectionType: "API",
        customProperties: []
      };

      var CERT = "-----BEGIN CERTIFICATE-----"; // for testing purposes, not a real PEM certificate.
       var certificateHolder = {
        commonName: "my-docker.com",
        issuerName: "my-docker.com",
        certificate: CERT
      };

      spyOn(services, 'createCertificate').and.callFake(function(params) {
        return new Promise(function(resolve, reject){
          resolve();
        });
      });
      spyOn(services, 'addHost').and.callFake(function(params) {
        return new Promise(function(resolve, reject){
          resolve();
        });
      });

      var expectedDtoHost = {
        hostState: {
          id: hostModel.address,
          address: hostModel.address,
          resourcePoolLink: hostModel.resourcePoolLink,
          descriptionLink: "someDescLink",
          customProperties: {
            __authCredentialsLink: hostModel.credential.documentSelfLink,
            __adapterDockerType: hostModel.connectionType
          },
          powerState: hostModel.powerState
        },
        sslTrust: certificateHolder
      };

      testUtils.waitFor(function() {
        return services.createCertificate.calls.count() > 0 && services.addHost.calls.count() > 0;
      }).then(function() {
        expect(services.createCertificate).toHaveBeenCalledWith(certificateHolder);
        expect(services.addHost).toHaveBeenCalledWith(expectedDtoHost);
        done();
      });

      HostActions.acceptCertificateAndAddHost(certificateHolder, hostModel);
    });

  });
});