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

(function(global){

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

    validateResourcePoolsCreate($reportClass).then(function(createdResourcePool) {
      return validateResourcePoolsUpdate($reportClass, createdResourcePool).then(function() {
        return validateResourcePoolsGet($reportClass, createdResourcePool);
      }).then(function() {
        return validateResourcePoolsList($reportClass, createdResourcePool);
      }).catch(function(e) {
        console.error(e);
      }).then(function() {
        validateResourcePoolsDelete($reportClass, createdResourcePool);
      });
    }).catch(function(e) {
      console.error(e);
    });
  };

  var validateResourcePoolsCreate = function($reportEl) {
      var testResourcePool = {
      id: 'rp-api-test',
      name: 'rp-api-test',
      minCpuCount: 1,
      minMemoryBytes: 4
    };

    var actionName = 'Create resource pool';
    return services.createResourcePool(testResourcePool).then(function(_createdResourcePool) {
      if (_createdResourcePool && _createdResourcePool.documentSelfLink == '/resources/pools/rp-api-test') {
        return _createdResourcePool;
      } else {
        throw new Error("Failed to create resource pool");
      }
    }).then(function(_createdResourcePool) {
      appendToReport($reportEl, actionName, 'passed');
      return _createdResourcePool;
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateResourcePoolsUpdate = function($reportEl, originalResourcePool) {
    var actionName = 'Update resource pool';
    var patch = {
      documentSelfLink: originalResourcePool.documentSelfLink,
      id: originalResourcePool.id,
      name: 'rp-api-test-updated'
    };

    return services.updateResourcePool(patch).then(function(updatedResourcePool) {
      if (!updatedResourcePool && updatedResourcePool.documentSelfLink != originalResourcePool.documentSelfLink &&
          updatedResourcePool.name != 'rp-api-test-updated') {
        throw new Error("Failed to update resource pool");
      }
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateResourcePoolsGet = function($reportEl, originalResourcePool) {
    var actionName = 'Get resource pool';
    return services.loadResourcePool(originalResourcePool.id).then(function(retrievedResourcePool) {
      if (!retrievedResourcePool || retrievedResourcePool.documentSelfLink != originalResourcePool.documentSelfLink) {
        throw new Error("Failed to get resource pool");
      }
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateResourcePoolsList = function($reportEl, originalResourcePool) {
    var actionName = 'List resource pools';
    return services.loadResourcePools().then(function(resourcePools) {
      var currentResourcePool = resourcePools[originalResourcePool.documentSelfLink];
      if (!currentResourcePool || currentResourcePool.documentSelfLink != originalResourcePool.documentSelfLink) {
        throw new Error("Failed to list resource pools");
      }
    }).then(function() {
      appendToReport($reportEl, actionName, 'passed');
    }).catch(function(e) {
      appendToReport($reportEl, actionName, 'failed');
      throw e;
    });
  };

  var validateResourcePoolsDelete = function($reportEl, originalResourcePool) {
    var actionName = 'Delete resource pool';
    return services.deleteResourcePool(originalResourcePool).then(function() {
      return services.loadResourcePool(originalResourcePool.id).catch(function() {
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
      userEmail: "username",
      privateKey: "password"
    };

    var actionName = 'Create credential';
    return services.createCredential(testCredential).then(function(_createdCredential) {
      if (_createdCredential && _createdCredential.documentSelfLink == '/core/auth/credentials/credential-api-test') {
        return _createdCredential;
      } else {
        throw new Error("Failed to create credential");
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
      userEmail: "username-updated"
    };

    return services.updateCredential(patch).then(function(updatedCredential) {
      if (!updatedCredential || updatedCredential.documentSelfLink != originalCredential.documentSelfLink ||
          updatedCredential.userEmail != 'username-updated') {
        throw new Error("Failed to update credential");
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
      if (!retrievedCredential || retrievedCredential.documentSelfLink != originalCredential.documentSelfLink) {
        throw new Error("Failed to get credential");
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
      if (!currentCredential || currentCredential.documentSelfLink != originalCredential.documentSelfLink) {
        throw new Error("Failed to list credentials");
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
    var sampleCertificate = "-----BEGIN CERTIFICATE-----\nMIIDFTCCAf2gAwIBAgIEVqdxmTANBgkqhkiG9w0BAQUFADBvMQswCQYDVQQGEwJV\nUzETMBEGA1UECAwKQ2FsaWZvcm5pYTESMBAGA1UEBwwJUGFsbyBBbHRvMRUwEwYD\nVQQKDAxWTXdhcmUsIEluYy4xDDAKBgNVBAsMA1ImRDESMBAGA1UEAwwJbG9jYWxo\nb3N0MB4XDTE2MDEyNjEzMTYzNVoXDTI0MDEyNjEzMTYzNVowETEPMA0GA1UEAwwG\nY2xpZW50MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA7CZ88TxxqMd7\nDM8dsrE8U//FKHbznA6SkmduuZ8d3FcYIc0ahyS8RHqMEBN4vP1nO5Pu6oUybF72\n2h/b2nggqQq0tETRHWTBwKJ2VkwzIbXymmdXZQoTXiI9iCt3GImG+JM6H5CqR428\nIM80vSYEj2e9fzP74hhaYcV2bi4YkjPjcfQGrvR8pL5Dp8tXzi3bviPHM4KFxAtJ\nBHUth2qstaudt/HBe7q7irS5Tp8ky9gt0eFBg6VdSBSqBkP+Wa94l+kG9JMsCni2\nbVDvpODGyxw0Ug+woWVQ83GDjv1wynL17YmqpkdO8lNz3P26jcXgsibKP7H2E2Xi\nJWurRQN16QIDAQABoxcwFTATBgNVHSUEDDAKBggrBgEFBQcDAjANBgkqhkiG9w0B\nAQUFAAOCAQEAYAuNRsSZ9Nw/Kk0tyolzk2b/AFaGOCeR9wgk59Njmc0HhXyGOUj+\nih4c58FLXvJodLDDBxeS2RgTCvkBEU5Pf2hdpPfLjnJDOiFPiAt04fWQNN5BtA0Y\ngVAo/TOripv6yagkWeaUF4XOqRc1KXiKvhZwyb/OSlqTkx7N2XfDlyQU+OabRqGM\nV5t0A6KCTr3lr33THqhEsYT2wU5wdosnZUfIzYLX6ttWK8xCCXoLN6lhd0w3VOR8\nXOS7D0eKTw8yqsKJtnyGOUL3maFEJgkry8kZIHArsLlhvNJlXW1DagTHxRc1uAuM\n7N/ha/Ii6qQekQupQ7LgUyksu6sVK8xyuQ\u003d\u003d\n-----END CERTIFICATE-----";

    var testCertificate = {
      certificate: sampleCertificate
    };

    var actionName = 'Create certificate';
    return services.createCertificate(testCertificate).then(function(_createdCertificate) {
      if (_createdCertificate && _createdCertificate.certificate == sampleCertificate) {
        return _createdCertificate;
      } else {
        throw new Error("Failed to create certificate");
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
      if (!updatedCertificate || updatedCertificate.documentSelfLink != originalCertificate.documentSelfLink ||
          updatedCertificate.certificate != originalCertificate.certificate) {
        throw new Error("Failed to update certificate");
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
    return services.loadCertificate(originalCertificate.documentSelfLink).then(function(retrievedCertificate) {
      if (!retrievedCertificate || retrievedCertificate.documentSelfLink != originalCertificate.documentSelfLink) {
        throw new Error("Failed to get certificate");
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
      if (!currentCertificate || currentCertificate.documentSelfLink != originalCertificate.documentSelfLink) {
        throw new Error("Failed to list certificates");
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
      name: "registry-name"
    };

    var actionName = 'Create registry';
    return services.createOrUpdateRegistry(testRegistry).then(function(_createdRegistry) {
      if (_createdRegistry && _createdRegistry.documentSelfLink == '/config/registries/registry-api-test') {
        return _createdRegistry;
      } else {
        throw new Error("Failed to create registry");
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
      address: "registry-address-updated"
    };

    return services.updateRegistry(patch).then(function(updatedRegistry) {
      if (!updatedRegistry || updatedRegistry.documentSelfLink != originalRegistry.documentSelfLink ||
          updatedRegistry.address != 'registry-address-updated') {
        throw new Error("Failed to update registry");
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
    return services.loadRegistry(originalRegistry.documentSelfLink).then(function(retrievedRegistry) {
      if (!retrievedRegistry || retrievedRegistry.documentSelfLink != originalRegistry.documentSelfLink) {
        throw new Error("Failed to get registry");
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
      if (!currentRegistry || currentRegistry.documentSelfLink != originalRegistry.documentSelfLink) {
        throw new Error("Failed to list registries");
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