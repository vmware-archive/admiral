/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the 'License').
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

(function(global) {

  var services;

  requirejs(['core/services'], function(_services) {
    services = _services;
  });

  global.validateApi = function($containerEl) {
    var $reports = $('<div>', {class: 'reports'});
    $containerEl.append($reports);

    var $rpReport = $('<div>', {class: 'report'});
    $reports.append($rpReport);
    validateResourcePools($rpReport);

    var $credentialsReport = $('<div>', {class: 'report'});
    $reports.append($credentialsReport);
    validateCredentials($credentialsReport);

    var $certificatesReport = $('<div>', {class: 'report'});
    $reports.append($certificatesReport);
    validateCertificates($certificatesReport);

    var $registriesReport = $('<div>', {class: 'report'});
    $reports.append($registriesReport);
    validateRegistries($registriesReport);
  };

  var validateResourcePools = function($reportEl) {
    var $reportClass = $('<div>', {class: 'report-class'}).html('Resource pools');
    $reportEl.append($reportClass);

    validateResourcePoolsCreate($reportClass).then(function(createdConfig) {
      return validateResourcePoolsUpdate($reportClass, createdConfig).then(function() {
        return validateResourcePoolsGet($reportClass, createdConfig);
      }).then(function() {
        return validateResourcePoolsList($reportClass, createdConfig);
      }).catch(function(e) {
        console.error(e);
      }).then(function() {
        validateResourcePoolsDelete($reportClass, createdConfig);
      });
    }).catch(function(e) {
      console.error(e);
    });
  };

  var validateResourcePoolsCreate = function($reportEl) {
    var testConfig = {
      resourcePoolState: {
        id: 'rp-api-test',
        name: 'rp-api-test',
        minCpuCount: 1,
        minMemoryBytes: 4
      }
    };

    var actionName = 'Create resource pool';
    return services.createResourcePool(testConfig).then(function(_createdConfig) {
      if (_createdConfig) {
        return _createdConfig;
      } else {
        throw new Error('Failed to create resource pool');
      }
    }).then(function(_createdConfig) {
      appendToReport($reportEl, actionName, 'passed');
      return _createdConfig;
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateResourcePoolsUpdate = function($reportEl, originalConfig) {
    var actionName = 'Update resource pool';
    var patch = {
      documentSelfLink: originalConfig.documentSelfLink,
      resourcePoolState: {
        id: originalConfig.resourcePoolState.id,
        name: 'rp-api-test-updated'
      }
    };

    return services.updateResourcePool(patch).then(function(updatedConfig) {
      if (!updatedConfig && updatedConfig.resourcePoolState.documentSelfLink !==
          originalConfig.resourcePoolState.documentSelfLink &&
          updatedConfig.resourcePoolState.name !== 'rp-api-test-updated') {
        throw new Error('Failed to update resource pool');
      }
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateResourcePoolsGet = function($reportEl, originalConfig) {
    var actionName = 'Get resource pool';
    return services.loadResourcePool(
        originalConfig.documentSelfLink).then(function(retrievedConfig) {
      if (!retrievedConfig || retrievedConfig.resourcePoolState.documentSelfLink !==
          originalConfig.resourcePoolState.documentSelfLink) {
        throw new Error('Failed to get resource pool');
      }
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateResourcePoolsList = function($reportEl, originalConfig) {
    var actionName = 'List resource pools';
    return services.loadResourcePools().then(function(configs) {
      var currentConfig = configs[originalConfig.documentSelfLink];
      if (!currentConfig || currentConfig.resourcePoolState.documentSelfLink !==
          originalConfig.documentSelfLink) {
        throw new Error('Failed to list resource pools');
      }
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateResourcePoolsDelete = function($reportEl, originalConfig) {
    var actionName = 'Delete resource pool';
    return services.deleteResourcePool(originalConfig).then(function() {
      return services.loadResourcePool(originalConfig.documentSelfLink).catch(function() {
        // expected to fail
      });
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateCredentials = function($reportEl) {
    var $reportClass = $('<div>', {class: 'report-class'}).html('Credentials');
    $reportEl.append($reportClass);

    validateCredentialsCreate($reportClass).then(function(createdCredential) {
      return validateCredentialsUpdate($reportClass, createdCredential).then(function() {
        return validateCredentialsGet($reportClass, createdCredential);
      }).then(function() {
        return validateCredentialsList($reportClass, createdCredential);
      }).catch(function(e) {
        console.error(e);
      }).then(function() {
        validateCredentialsDelete($reportClass, createdCredential);
      });
    }).catch(function(e) {
      console.error(e);
    });
  };

  var validateCredentialsCreate = function($reportEl) {
    var testCredential = {
      documentSelfLink: 'credential-api-test',
      userEmail: 'username',
      privateKey: 'password'
    };

    var actionName = 'Create credential';
    return services.createCredential(testCredential).then(function(_createdCredential) {
      if (_createdCredential &&
          _createdCredential.documentSelfLink === '/core/auth/credentials/credential-api-test') {
        return _createdCredential;
      } else {
        throw new Error('Failed to create credential');
      }
    }).then(function(_createdCredential) {
      appendToReport($reportEl, actionName, 'passed');
      return _createdCredential;
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateCredentialsUpdate = function($reportEl, originalCredential) {
    var actionName = 'Update credential';
    var patch = {
      documentSelfLink: originalCredential.documentSelfLink,
      userEmail: 'username-updated'
    };

    return services.updateCredential(patch).then(function(updatedCredential) {
      if (!updatedCredential || updatedCredential.documentSelfLink !==
          originalCredential.documentSelfLink ||
          updatedCredential.userEmail !== 'username-updated') {
        throw new Error('Failed to update credential');
      }
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      console.error(e);
    });
  };

  var validateCredentialsGet = function($reportEl, originalCredential) {
    var id = originalCredential.documentSelfLink.replace('/core/auth/credentials/', '');
    var actionName = 'Get credential';
    return services.loadCredential(id).then(function(retrievedCredential) {
      if (!retrievedCredential ||
          retrievedCredential.documentSelfLink !== originalCredential.documentSelfLink) {
        throw new Error('Failed to get credential');
      }
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateCredentialsList = function($reportEl, originalCredential) {
    var actionName = 'List credentials';
    return services.loadCredentials().then(function(credentials) {
      var currentCredential = credentials[originalCredential.documentSelfLink];
      if (!currentCredential ||
          currentCredential.documentSelfLink !== originalCredential.documentSelfLink) {
        throw new Error('Failed to list credentials');
      }
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateCredentialsDelete = function($reportEl, originalCredential) {
    var id = originalCredential.documentSelfLink.replace('/core/auth/credentials/', '');
    var actionName = 'Delete credential';
    return services.deleteCredential(originalCredential).then(function() {
      return services.loadCredential(id).catch(function() {
        // Expected to fail
      });
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateCertificates = function($reportEl) {
    var $reportClass = $('<div>', {class: 'report-class'}).html('Certificates');
    $reportEl.append($reportClass);

    validateCertificatesCreate($reportClass).then(function(createdCertificate) {
      return validateCertificatesUpdate($reportClass, createdCertificate).then(function() {
        return validateCertificatesGet($reportClass, createdCertificate);
      }).then(function() {
        return validateCertificatesList($reportClass, createdCertificate);
      }).catch(function(e) {
        console.error(e);
      }).then(function() {
        validateCertificatesDelete($reportClass, createdCertificate);
      });
    }).catch(function(e) {
      console.error(e);
    });
  };

  var validateCertificatesCreate = function($reportEl) {
    // took from test-integration/src/test/resources/client_cert_coreos_vra
    var sampleCertificate = '-----BEGIN CERTIFICATE-----\n' +
    'MIIDFTCCAf2gAwIBAgIEVqdxmTANBgkqhkiG9w0BAQUFADBvMQswCQYDVQQGEwJV\n' +
    'UzETMBEGA1UECAwKQ2FsaWZvcm5pYTESMBAGA1UEBwwJUGFsbyBBbHRvMRUwEwYD\n' +
    'VQQKDAxWTXdhcmUsIEluYy4xDDAKBgNVBAsMA1ImRDESMBAGA1UEAwwJbG9jYWxo\n' +
    'b3N0MB4XDTE2MDEyNjEzMTYzNVoXDTI0MDEyNjEzMTYzNVowETEPMA0GA1UEAwwG\n' +
    'Y2xpZW50MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA7CZ88TxxqMd7\n' +
    'DM8dsrE8U//FKHbznA6SkmduuZ8d3FcYIc0ahyS8RHqMEBN4vP1nO5Pu6oUybF72\n' +
    '2h/b2nggqQq0tETRHWTBwKJ2VkwzIbXymmdXZQoTXiI9iCt3GImG+JM6H5CqR428\n' +
    'IM80vSYEj2e9fzP74hhaYcV2bi4YkjPjcfQGrvR8pL5Dp8tXzi3bviPHM4KFxAtJ\n' +
    'BHUth2qstaudt/HBe7q7irS5Tp8ky9gt0eFBg6VdSBSqBkP+Wa94l+kG9JMsCni2\n' +
    'bVDvpODGyxw0Ug+woWVQ83GDjv1wynL17YmqpkdO8lNz3P26jcXgsibKP7H2E2Xi\n' +
    'JWurRQN16QIDAQABoxcwFTATBgNVHSUEDDAKBggrBgEFBQcDAjANBgkqhkiG9w0B\n' +
    'AQUFAAOCAQEAYAuNRsSZ9Nw/Kk0tyolzk2b/AFaGOCeR9wgk59Njmc0HhXyGOUj+\n' +
    'ih4c58FLXvJodLDDBxeS2RgTCvkBEU5Pf2hdpPfLjnJDOiFPiAt04fWQNN5BtA0Y\n' +
    'gVAo/TOripv6yagkWeaUF4XOqRc1KXiKvhZwyb/OSlqTkx7N2XfDlyQU+OabRqGM\n' +
    'V5t0A6KCTr3lr33THqhEsYT2wU5wdosnZUfIzYLX6ttWK8xCCXoLN6lhd0w3VOR8\n' +
    'XOS7D0eKTw8yqsKJtnyGOUL3maFEJgkry8kZIHArsLlhvNJlXW1DagTHxRc1uAuM\n' +
    '7N/ha/Ii6qQekQupQ7LgUyksu6sVK8xyuQ\u003d\u003d\n' +
    '-----END CERTIFICATE-----';

    var testCertificate = {
      certificate: sampleCertificate
    };

    var actionName = 'Create certificate';
    return services.createCertificate(testCertificate).then(function(_createdCertificate) {
      if (_createdCertificate && _createdCertificate.certificate === sampleCertificate) {
        return _createdCertificate;
      } else {
        throw new Error('Failed to create certificate');
      }
    }).then(function(_createdCertificate) {
      appendToReport($reportEl, actionName, 'passed');
      return _createdCertificate;
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateCertificatesUpdate = function($reportEl, originalCertificate) {
    var actionName = 'Update certificate';
    // Nothing specific to update
    var patch = {
      documentSelfLink: originalCertificate.documentSelfLink,
      certificate: originalCertificate.certificate
    };

    return services.updateCertificate(patch).then(function(updatedCertificate) {
      if (!updatedCertificate || updatedCertificate.documentSelfLink !==
          originalCertificate.documentSelfLink ||
          updatedCertificate.certificate !== originalCertificate.certificate) {
        throw new Error('Failed to update certificate');
      }
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateCertificatesGet = function($reportEl, originalCertificate) {
    var actionName = 'Get certificate';
    return services.loadCertificate(
        originalCertificate.documentSelfLink).then(function(retrievedCertificate) {
      if (!retrievedCertificate || retrievedCertificate.documentSelfLink !==
          originalCertificate.documentSelfLink) {
        throw new Error('Failed to get certificate');
      }
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateCertificatesList = function($reportEl, originalCertificate) {
    var actionName = 'List certificates';
    return services.loadCertificates().then(function(certificates) {
      var currentCertificate = certificates[originalCertificate.documentSelfLink];
      if (!currentCertificate ||
          currentCertificate.documentSelfLink !== originalCertificate.documentSelfLink) {
        throw new Error('Failed to list certificates');
      }
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateCertificatesDelete = function($reportEl, originalCertificate) {
    var actionName = 'Delete certificate';
    return services.deleteCertificate(originalCertificate).then(function() {
      return services.loadCertificate(originalCertificate.documentSelfLink).catch(function() {
        // expected to fail
      });
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateRegistries = function($reportEl) {
    var $reportClass = $('<div>', {class: 'report-class'}).html('Registries');
    $reportEl.append($reportClass);

    validateRegistriesCreate($reportClass).then(function(createdRegistry) {
      return validateRegistriesUpdate($reportClass, createdRegistry).then(function() {
        return validateRegistriesGet($reportClass, createdRegistry);
      }).then(function() {
        return validateRegistriesList($reportClass, createdRegistry);
      }).catch(function(e) {
        console.error(e);
      }).then(function() {
        validateRegistriesDelete($reportClass, createdRegistry);
      });
    }).catch(function(e) {
      console.error(e);
    });
  };

  var validateRegistriesCreate = function($reportEl) {
    var testRegistry = {
      documentSelfLink: 'registry-api-test',
      address: 'registry-address',
      name: 'registry-name'
    };

    var actionName = 'Create registry';
    return services.createOrUpdateRegistry(testRegistry).then(function(_createdRegistry) {
      if (_createdRegistry &&
          _createdRegistry.documentSelfLink === '/config/registries/registry-api-test') {
        return _createdRegistry;
      } else {
        throw new Error('Failed to create registry');
      }
    }).then(function(_createdRegistry) {
      appendToReport($reportEl, actionName, 'passed');
      return _createdRegistry;
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateRegistriesUpdate = function($reportEl, originalRegistry) {
    var actionName = 'Update registry';
    var patch = {
      documentSelfLink: originalRegistry.documentSelfLink,
      address: 'registry-address-updated'
    };

    return services.updateRegistry(patch).then(function(updatedRegistry) {
      if (!updatedRegistry ||
          updatedRegistry.documentSelfLink !== originalRegistry.documentSelfLink ||
          updatedRegistry.address !== 'registry-address-updated') {
        throw new Error('Failed to update registry');
      }
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      console.error(e);
    });
  };

  var validateRegistriesGet = function($reportEl, originalRegistry) {
    var actionName = 'Get registry';
    return services.loadRegistry(
        originalRegistry.documentSelfLink).then(function(retrievedRegistry) {
      if (!retrievedRegistry ||
          retrievedRegistry.documentSelfLink !== originalRegistry.documentSelfLink) {
        throw new Error('Failed to get registry');
      }
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateRegistriesList = function($reportEl, originalRegistry) {
    var actionName = 'List registries';
    return services.loadRegistries().then(function(registries) {
      var currentRegistry = registries[originalRegistry.documentSelfLink];
      if (!currentRegistry ||
          currentRegistry.documentSelfLink !== originalRegistry.documentSelfLink) {
        throw new Error('Failed to list registries');
      }
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateRegistriesDelete = function($reportEl, originalRegistry) {
    var actionName = 'Delete registry';
    return services.deleteRegistry(originalRegistry).then(function() {
      return services.loadRegistry(originalRegistry.documentSelfLink).catch(function() {
        // Expected to fail
      });
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var appendToReport = function($reportEl, message, state) {
    $reportEl.append($('<div>', {class: state}).html(message));
  };
})(this);
