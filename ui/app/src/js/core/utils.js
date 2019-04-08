/*
 * Copyright (c) 2016-2019 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

import constants from 'core/constants';
import links from 'core/links';
import ft from 'core/ft';

const URL_PROTOCOL_PART = /^(https?):\/\//;
const URL_DEFAULT_PROTOCOL = 'http://';
const URL_PORT_SEPARATOR = ':';

const RX_NAME = '[a-zA-Z0-9_.-]+';
const RX_UNIX_ABS_PATH = '/[a-zA-Z0-9_./ -]*';
const RE_UNIX_ABS_PATH = new RegExp('^' + RX_UNIX_ABS_PATH + '$');
const RE_UNIX_ABS_PATH_OR_NAME = new RegExp('^((' + RX_NAME + ')|(' + RX_UNIX_ABS_PATH + '))$');

const VERSION_REG_EX = /^(\*|\d+(\.\d+){0,2}(\.\*)?)/g;

const OFFICIAL_REGISTRY_LIST = ['registry.hub.docker.com', 'docker.io'];

var isSingleView = window.isSingleView;
var isNavigationLess = window.isNavigationLess;

var configurationProperties = null;

var isInteger = function(integer, min, max) {
  var range = {};
  range.min = min !== undefined ? min : -2147483648;
  range.max = max !== undefined ? max : 2147483647;
  return validator.isInt(integer + '', range);
};

var isNgViewFromViews = function(views, viewName, hasNgParent) {
  if (views) {
    for (var v in views) {
      if (!views.hasOwnProperty(v)) {
        continue;
      }
      var view = views[v];
      if (view.name === viewName) {
        return hasNgParent || view.ng;
      }
      if (isNgViewFromViews(view.VIEWS, viewName, hasNgParent || view.ng)) {
        return true;
      }
    }
  }
  return false;
};

var utils = {
  initializeConfigurationProperties: function(props) {
    if (configurationProperties) {
      throw new Error('Properties already set');
    }
    configurationProperties = props;
    // shell in a box feature was removed due to security issues
    configurationProperties['allow.browser.ssh.console'] = 'false';
  },

  getConfigurationProperty: function(property) {
    return configurationProperties && configurationProperties[property];
  },

  getConfigurationPropertyBoolean: function(property) {
    return configurationProperties && configurationProperties[property] === 'true';
  },

  existsConfigurationProperty: function(property) {
    return configurationProperties.hasOwnProperty(property);
  },

  getHarborTabUrl: function() {
    return this.getConfigurationProperty('harbor.tab.url');
  },

  showResourcesView: function(viewName) {
    if (viewName === constants.VIEWS.RESOURCES.name) {
        return true;
    }

    for (var view in constants.VIEWS.RESOURCES.VIEWS) {
        if (viewName === constants.VIEWS.RESOURCES.VIEWS[view].name) {
            return true;
        }
    }
    return false;
  },

  isVic: function() {
    return this.getConfigurationPropertyBoolean('vic');
  },

  isNgView: function(viewName) {
    return isNgViewFromViews(constants.VIEWS, viewName);
  },

  validate: function(model, constraints) {
    var validationErrors = {};
    var hasError;
    for (var key in model) {
      if (model.hasOwnProperty(key)) {
        var validationFunction = constraints[key];
        if (validationFunction) {
          var error = validationFunction.call(null, model[key]);
          if (error) {
            validationErrors[key] = error;
            hasError = true;
          }
        }
      }
    }

    if (hasError) {
      return validationErrors;
    }

    return null;
  },

  isAbsolutePath: function(item) {
    return RE_UNIX_ABS_PATH.test(item);
  },

  isAbsolutePathOrName: function(item) {
    return RE_UNIX_ABS_PATH_OR_NAME.test(item);
  },

  isFromCatalog: function(item) {
    return !!(item && item.customProperties && item.customProperties.subTenantId);
  },

  deleteElementFromList(customProperties, key) {
    return $.grep(customProperties, function(e) {
      return e.name !== key;
    });
  },

  isValidPort: function(port) {
    return validator.isInt(port + '', {
        min: 0,
        max: 65535
      }) || validator.trim(port + '').match(/__null/g);
  },

  isValidContainerMemory: function(mem) {
    if (mem && mem === '0') {
      return true;
    }

    let range = {
      min: parseInt(this.getConfigurationProperty('docker.container.min.memory'), 10) || 0,
      max: 9007199254740991 // Number.MAX_SAFE_INTEGER
    };

    return validator.isInt(mem + '', range);
  },

  isInteger: isInteger,

  isNonNegativeInteger: function(integer) {
    return isInteger(integer, 0);
  },

  isPositiveInteger: function(integer) {
    return isInteger(integer, 1);
  },

  pushNoDuplicates: function(array, element) {
    if (array && array.indexOf(element) === -1) {
      array.push(element);
    }
  },

  uniqueArray: function(duplicatesArray) {
    return duplicatesArray.filter(function(elem, pos) {
      return duplicatesArray.indexOf(elem) === pos;
    });
  },

  applyValidationError: function($el, error) {
    if (error) {
      $el.addClass('has-error');
      $el.find('.help-block').html(i18n.t(error));
    } else {
      $el.removeClass('has-error');
      $el.find('.help-block').empty();
    }
  },

  applyMultilineValidationError: function($el, errors) {
    $el.find('.multicolumn-input').each(function(idx, input) {
      $(input).find('.has-error').removeClass('has-error');
      $(input).find('.help-block').empty();
      var error = errors && errors[idx];
      if (error) {
        for (var propertyName in error) {
          if (error.hasOwnProperty(propertyName)) {
            var $propertyInput = $(input).find('.inline-input-holder[name="' + propertyName + '"]');
            utils.applyValidationError($propertyInput, error[propertyName]);
          }
        }
      }
    });
  },

  shouldHideCustomProperty: function(property) {
    return (property.name.substring(0, 2) === constants.PROPERTIES.VISIBILITY_HIDDEN_PREFIX ||
           property.value === null);
  },

  setIn: function(immutableObject, path, value) {
    if (path.length === 0) {
      throw new Error('Unexpected empty path');
    }

    var toMerge = {};
    var key = path[0];

    if (path.length === 1) {
      toMerge[key] = value;
    } else {
      var innerObject = immutableObject[key];
      if (!innerObject) {
        innerObject = Immutable({});
      }

      var otherPath = path.slice(1);

      toMerge[key] = this.setIn(innerObject, otherPath, value);
    }

    return immutableObject.merge(toMerge);
  },

  getIn: function(immutableObject, path) {
    if (path.length === 0) {
      throw new Error('Unexpected empty path');
    }

    if (!immutableObject) {
      return undefined;
    }

    var key = path[0];
    if (path.length === 1) {
      return immutableObject[key];
    } else {
      var otherPath = path.slice(1);
      return this.getIn(immutableObject[key], otherPath);
    }
  },

  /**
   * Update the provided data items array with the specified item.
   * In case it is found in the items array by the id key and its respective value - the item will
   * be updated, otherwise it will appended to the array.
   *
   * @returns the updated items array.
   */
  updateItems: function(items, item, itemIdKey, id) {
    if (Array.isArray(items)) {
      let matchedItemIndex = items.findIndex((item) => {
        return item[itemIdKey] === id;
      });

      items = items.asMutable();
      if (matchedItemIndex > -1) {
        items[matchedItemIndex] = item;
      } else {
        items.push(item);
      }
    }

    return items;
  },

  slideToLeft: function($el, bySelfWidth, callback) {
    var parentWidth = $el.parent().width();

    var defaultLeft = parseFloat($el.css('left'));
    var defaultRight = parseFloat($el.css('right'));
    var defaultWidth = parseFloat($el.css('width'));

    if (bySelfWidth) {
      $el.css({
        right: defaultRight - defaultWidth
      }).animate({
        right: defaultRight
      }, 'fast', function() {
        $el.css({
          right: ''
        });
        if (callback) {
          callback();
        }
      });
    } else {
      $el.css({
        left: parentWidth,
        right: -parentWidth
      }).animate({
        left: defaultLeft,
        right: defaultRight
      }, 'fast', function() {
        $el.css({
          left: '',
          right: ''
        });
        if (callback) {
          callback();
        }
      });
    }
  },

  fadeOut: function($el, callback) {
    $el.animate({
      opacity: 0
    }, 'fast').promise().done(callback);
  },

  fadeIn: function($el, callback) {
    $el.css({
      opacity: 0
    });
    $el.animate({
      opacity: 1
    }, 'fast').promise().done(callback);
  },

  diagonalEmerge: function($el, callback) {
    var parentWidth = $el.parent().width();
    var parentHeight = $el.parent().height();

    var defaultLeft = parseFloat($el.css('left'));
    var defaultRight = parseFloat($el.css('right'));
    var defaultTop = parseFloat($el.css('top'));
    var defaultBottom = parseFloat($el.css('bottom'));

    $el.css({
        left: parentWidth,
        right: -parentWidth,
        top: -parentHeight,
        bottom: parentHeight
      }).animate({
        left: defaultLeft,
        right: defaultRight,
        top: defaultTop,
        bottom: defaultBottom
      },
      'fast',
      function() {
        $el.css({
          left: '',
          right: '',
          top: '',
          bottom: ''
        });

        if (callback) {
          callback();
        }
      }
    );
  },

  hasExpandedContextView: function(view) {
    return !!(view && view.contextView && view.contextView.expanded);
  },

  getIntValue: function(value) {
    return $.isNumeric(value) ? parseInt(value, 10) : null;
  },

  isValidNonNegativeIntValue: function(value) {
    let intValue = this.getIntValue(value);
    if (intValue === null || intValue === undefined) {
      return false;
    }

    let limitValueRange = {
      min: 0,
      max: 9007199254740991 // Number.MAX_SAFE_INTEGER
    };

    return validator.isInt(intValue + '', limitValueRange);
  },

  containerStatusDisplay: function(state, timestamp, status) {
    var stateString = '';
    if (state) {
      stateString = i18n.t('app.container.state.' + state);
      if (state === constants.CONTAINERS.STATES.RUNNING && !!timestamp) {
        var now = moment.utc();
        var stateMoment = moment.utc(timestamp);
        moment.locale(i18n.language);
        return i18n.t('app.container.state.continuousStateSince', {
          state: stateString,
          duration: moment.duration(now.diff(stateMoment)).humanize()
        });
      } else if (state === constants.CONTAINERS.STATES.ERROR && status) {
        var statusString = status;
        if (status === constants.CONTAINERS.STATUS.UNHEALTHY) {
          statusString = i18n.t('app.container.status.' + status);
        }
        return i18n.t('app.container.state.errorStatus', {
          state: stateString,
          status: statusString
        });
      }
    }

    return stateString;
  },

  isContainerOnVchHost(containerState) {
    return containerState && containerState.isOnVchHost;
  },

  networkStatusDisplay: function(state) {
    var stateString = '';
    if (state) {
      stateString = i18n.t('app.resource.list.network.state.' + state);
    }

    return stateString;
  },

  isRetiredNetwork: function(network) {
    return network.powerState
      && network.powerState === constants.RESOURCES.NETWORKS.STATES.RETIRED;
  },

  isBuiltinNetwork: function(networkName) {
    return networkName === constants.BUILT_IN_NETWORKS.NONE
      || networkName === constants.BUILT_IN_NETWORKS.HOST
      || networkName === constants.BUILT_IN_NETWORKS.BRIDGE
      || networkName === constants.BUILT_IN_NETWORKS.GWBRIDGE;
  },

  isNetworkRemovalPossible: function(network) {
    // a network can be removed only if there are no containers
    // connected to it or if the network is in RETIRED state
    return utils.isRetiredNetwork(network)
      || !network.connectedContainersCount;
  },

  canRemove: function(entity) {
    return this.isRetired(entity) || !entity.connectedContainersCount;
  },

  isRetired: function(entity) {
    return entity.powerState && entity.powerState === 'RETIRED';
  },

  isSystemContainer: function(container) {
    return container.system;
  },

  isContainerStatusActive: function(containerPowerState) {
    return (containerPowerState === constants.CONTAINERS.STATES.RUNNING) ||
      (containerPowerState === constants.CONTAINERS.STATES.REBOOTING);
  },

  isContainerStatusInactive: function(containerPowerState) {
    return (containerPowerState === constants.CONTAINERS.STATES.STOPPED);
  },

  isContainerStatusError: function(containerPowerState) {
    return (containerPowerState === constants.CONTAINERS.STATES.ERROR);
  },

  isContainerStatusRetired: function(containerPowerState) {
    return (containerPowerState === constants.CONTAINERS.STATES.RETIRED);
  },

  isContainerStatusUnknown: function(containerPowerState) {
    return (containerPowerState === constants.CONTAINERS.STATES.UNKNOWN);
  },

  isContainerStatusProvisioning: function(containerPowerState) {
    return (containerPowerState === constants.CONTAINERS.STATES.PROVISIONING);
  },

  isContainerStatusOk: function(containerPowerState) {
    return !this.isContainerStatusError(containerPowerState)
            && !this.isContainerStatusRetired(containerPowerState)
            && !this.isContainerStatusUnknown(containerPowerState)
            && !this.isContainerStatusProvisioning(containerPowerState);
  },

  operationSupportedMulti: function(op, items) {
    if (items) {
      for (let i in items) {
        if (this.operationSupported(op, items[i])) {

          return true;
        }
      }
    }

    return false;
  },

  operationSupported: function(op, resource) {
    if (this.isSystemContainer(resource)) {
      return false;
    } else if (resource.type === constants.CONTAINERS.TYPES.COMPOSITE
                || resource.type === constants.CONTAINERS.TYPES.CLUSTER) {

      let items = [];
      if (resource.containers) {
        items = resource.containers;

      } else if (resource.listView) {
        items = items.concat(resource.listView.items);

        if (op === 'REMOVE') {
          if (resource.listView.networks) {
            items = items.concat(resource.listView.networks);
          }

          if (resource.listView.volumes) {
            items = items.concat(resource.listView.volumes);
          }
        }
      }

      return this.operationSupportedMulti(op, items);

    } else if (op === constants.CONTAINERS.OPERATION.MANAGE) {
      return (!this.isApplicationSingleView() && this.isFromCatalog(resource));
    } else if (op === constants.CONTAINERS.OPERATION.CREATE_TEMPLATE) {
      return !this.isApplicationSingleView();
    } else if (op === constants.CONTAINERS.OPERATION.OPEN_TEMPLATE) {
      return !this.isApplicationSingleView();
    } else if (resource.type === constants.RESOURCES.TYPES.NETWORK) {
      return (op === constants.RESOURCES.NETWORKS.OPERATION.REMOVE
            && !this.isFromCatalog(resource));

    } else if (op === constants.CONTAINERS.OPERATION.STOP) {
      return (!this.isApplicationSingleView())
                && this.isContainerStatusActive(resource.powerState)
                && !this.isFromCatalog(resource);

    } else if (op === constants.CONTAINERS.OPERATION.START) {
      return (!this.isApplicationSingleView())
                && this.isContainerStatusInactive(resource.powerState)
                && !this.isFromCatalog(resource);

    } else if (op === constants.CONTAINERS.OPERATION.REMOVE) {
      return (!this.isApplicationSingleView()
                && !this.isFromCatalog(resource));

    } else if (op === constants.CONTAINERS.OPERATION.CLUSTERING) {
      return (!this.isApplicationSingleView()
                && this.isContainerStatusOk(resource.powerState)
                && !this.isFromCatalog(resource));

    } else if (op === constants.CONTAINERS.OPERATION.SHELL) {
      return this.getConfigurationPropertyBoolean('allow.browser.ssh.console')
                && !this.isVic() // Container shell access temporarily disabled in VIC!
                && !this.isApplicationEmbedded() // and in embedded mode also!
                && !this.isApplicationSingleView()
                && this.isContainerStatusOk(resource.powerState)
                && !this.isContainerStatusInactive(resource.powerState)
                && !resource.isOnVchHost;
    }

    return true;
  },

  operationSupportedHost: function(op, host) {
    if (op === constants.HOSTS.OPERATION.ENABLE) {

      return host.powerState === constants.STATES.SUSPEND ||
        host.powerState === constants.STATES.OFF;
    } else if (op === constants.HOSTS.OPERATION.DISABLE) {

      return host.powerState !== constants.STATES.SUSPEND &&
        host.powerState !== constants.STATES.OFF;
    }

    return true;
  },

  operationSupportedTemplate: function(op) {
    if (op === constants.TEMPLATES.OPERATION.PUBLISH) {
      return !!this.isApplicationEmbedded();
    }

    return true;
  },

  operationSupportedDataCollect: function(op) {
    if (op === constants.HOSTS.OPERATION.DATACOLLECT) {
      return !!this.isApplicationEmbedded();
    }

    return true;
  },

  getDocumentId: function(documentSelfLink) {
    if (documentSelfLink) {
      return documentSelfLink.substring(documentSelfLink.lastIndexOf('/') + 1);
    }
  },

  isDebugModeEnabled: function() {
    var query = location.search.substr(1);
    var params = this.uriToParams(query);
    return !!params.debug;
  },

  getDebugSlowModeTimeout: function() {
    var query = location.search.substr(1);
    var params = this.uriToParams(query);
    if (params.hasOwnProperty('debug-slow')) {
      return params['debug-slow'] || 1000;
    }
  },

  extractHarborRedirectUrl: function() {
    var queryIndex = location.search.indexOf('?');
    if (queryIndex !== -1) {
      let query = location.search.substring(queryIndex + 1);
      let params = this.uriToParams(query);
      let redirectUrl = params.harbor_redirect_url;
      if (redirectUrl) {
        delete params.harbor_redirect_url;
        let newQuery = this.paramsToURI(params);

        let newSearch = location.search.substring(0, queryIndex);
        if (newQuery) {
          newSearch += '?' + newQuery;
        }

        let newHash = location.hash || '';

        var newUrl = location.protocol + '//' + location.host + '/' + newSearch + newHash;

        if (window.history.replaceState) {
           window.history.replaceState({}, document.title, newUrl);
        }

        return decodeURIComponent(redirectUrl);
      }
    }
    return null;
  },

  prepareHarborRedirectUrl: function(baseHarborUrl) {
    var selfRedirectUrl = window.location.href;
    selfRedirectUrl = encodeURIComponent(selfRedirectUrl);
    var query = 'admiral_redirect_url=' + selfRedirectUrl;
    var harborUrl = baseHarborUrl;
    if (harborUrl.indexOf('?') !== -1) {
      harborUrl += '&' + query;
    } else {
      harborUrl += '?' + query;
    }

    return harborUrl;
  },

  isApplicationEmbedded: function() {
    return this.getConfigurationPropertyBoolean('embedded');
  },

  isVca: function() {
    return this.getConfigurationPropertyBoolean('vca');
  },

  isApplicationSingleView: function() {
    return isSingleView;
  },

  isNavigationLess: function() {
    return isNavigationLess;
  },

  /**
   * The build number consists of the release version + the number of last
   * succesful build of this version. For example: 0.9.1 (1423)
   */
  getBuildNumber: function() {
    return this.getConfigurationProperty('__build.number');
  },

  /**
   * Returns the version number, extracted from the build number.
   * For example if the build number is 0.9.1 (1423), the result of this call
   * will be 0.9.1
   */
  getVersionNumber: function() {
    var buildNumber = this.getBuildNumber();
    var match = buildNumber && buildNumber.match(VERSION_REG_EX);
    return match && match[0];
  },

  isRequestRunning: function(request) {
    var stage = request.taskInfo.stage;
    const STAGES = constants.REQUESTS.STAGES;
    return stage === STAGES.CREATED || stage === STAGES.STARTED;
  },

  isRequestFailed: function(request) {
    var stage = request.taskInfo.stage;
    const STAGES = constants.REQUESTS.STAGES;
    return stage === STAGES.FAILED || stage === STAGES.CANCELLED;
  },

  isRequestFinished: function(request) {
    var stage = request.taskInfo.stage;
    const STAGES = constants.REQUESTS.STAGES;
    return stage === STAGES.FINISHED;
  },

  isEventLogTypeWarning(event) {
    return event.eventLogType === constants.EVENTLOG.TYPE.WARNING;
  },

  isEventLogTypeError(event) {
    return event.eventLogType === constants.EVENTLOG.TYPE.ERROR;
  },

  humanizeTimeFromNow(timestampMicros) {
    var toSeconds = timestampMicros / 1000000;
    return moment.unix(toSeconds).fromNow();
  },

  getExportLinkForTemplate(templateSelfLink, format) {
    var exportLink = links.COMPOSITE_DESCRIPTIONS_CONTENT + '?selfLink=' + templateSelfLink;
    if (format === constants.TEMPLATES.EXPORT_FORMAT.DOCKER_COMPOSE) {
      exportLink += '&format=docker';
    }

    return this.serviceUrl(exportLink);
  },

  toArray(obj) {
    if (obj && !$.isArray(obj)) {
      var array = [];
      for (var key in obj) {
        if (obj.hasOwnProperty(key)) {
          array.push(obj[key]);
        }
      }
      return array;
    }

    return obj;
  },

  hasChanged: function(newValue, oldValue) {
    // returns true when the new value does not equal the old value
    // AND any of the new or old values are non-null and defined
    return (newValue || oldValue) && (newValue !== oldValue);
  },

  paramsToURI: function(params) {
    var str = [];
    for (var p in params) {
      if (params.hasOwnProperty(p)) {
        var v = params[p];
        var encodedKey = encodeURI(p);

        if ($.isArray(v)) {
          for (var i in v) {
            if (v.hasOwnProperty(i)) {
              str.push(encodedKey + '=' + encodeURI(v[i]));
            }
          }
        } else {
          str.push(encodedKey + '=' + encodeURI(v));
        }
      }
    }

    return str.join('&');
  },

  uriToParams: function(uri) {
    var result = {};
    uri.split('&').forEach(function(part) {
      if (part) {
        var item = part.split('=');
        result[decodeURIComponent(item[0])] = item[1] ? decodeURIComponent(item[1]) : null;
      }
    });
    return result;
  },

  getURLParts: function(url) {
    var noProtocol = false;
    if (url.search(/.*:\/\//) !== 0) {
      url = URL_DEFAULT_PROTOCOL + url;
      noProtocol = true;
    }

    var parser = document.createElement('a');
    parser.href = url;

    var protocol = noProtocol ? '' : parser.protocol.replace(URL_PORT_SEPARATOR, '');
    var search = parser.search.replace('?', '');

    var port = parser.port;
    if (port === '0') {
      port = undefined;
    }

    return {
      scheme: protocol,
      host: parser.hostname,
      port: port,
      path: parser.pathname,
      query: search,
      fragment: parser.hash
    };
  },

  mergeURLParts: function(urlParts) {
    var mergedUri = '';
    if (urlParts.scheme) {
      mergedUri += urlParts.scheme + '://';
    }
    mergedUri += urlParts.host;
    if (urlParts.port) {
      mergedUri += ':' + urlParts.port;
    }
    if (urlParts.path) {
      mergedUri += urlParts.path;
    }
    if (urlParts.query) {
      mergedUri += '?' + urlParts.query;
    }
    if (mergedUri.endsWith('/')) {
      mergedUri = mergedUri.slice(0, -1);
    }
    return mergedUri;
  },

  populateDefaultSchemeAndPort: function(uri) {
    var urlParts = this.getURLParts(uri);

    if (!urlParts.host) {
      return uri;
    }

    if (!urlParts.scheme) {
      urlParts.scheme = 'https';
    }

    var isDefaultDockerRegistry = OFFICIAL_REGISTRY_LIST.indexOf(urlParts.host) !== -1;

    if (!urlParts.port) {
      if (urlParts.scheme.toLowerCase() === 'https' && !isDefaultDockerRegistry) {
        urlParts.port = 443;
      }
      if (urlParts.scheme.toLowerCase() === 'http' && !isDefaultDockerRegistry) {
        urlParts.port = 80;
      }
    }

    return utils.mergeURLParts(urlParts);
  },

  getPortLinks: function(hostAddress, ports) {
    var portLinks = [];

    if (ports) {
      hostAddress = this.formatHostAddress(hostAddress);

      for (let i = 0; i < ports.length; i++) {
        let port = ports[i];

        let linkDisplayName = this.getPortLinkDisplayText(hostAddress, port);

        let linkAddress = hostAddress ? (hostAddress + URL_PORT_SEPARATOR + port.hostPort) : null;

        portLinks[i] = {
          link: linkAddress,
          name: linkDisplayName
        };
      }
    }

    return portLinks;
  },

  formatHostAddress: function(hostAddress) {
    if (!hostAddress) {
      return '';
    }

    let portIndex = hostAddress.lastIndexOf(URL_PORT_SEPARATOR);
    if (portIndex > -1) {
      // strip port if any
      hostAddress = hostAddress.substring(0, portIndex);
    }

    hostAddress = hostAddress.replace(URL_PROTOCOL_PART, '');
    // we don't know if the container uses https or not, so we use default.
    hostAddress = URL_DEFAULT_PROTOCOL + hostAddress;

    return hostAddress;
  },

  getPortLinkDisplayText: function(hostAddress, port) {
    let linkDisplayName = '';

    // Used backend's com.vmware.vcac.container.domain.PortBinding.toString() to format the
    // ports string
    if (hostAddress) {
      linkDisplayName += hostAddress;
    }

    if (port.hostPort) {
      if (linkDisplayName.length > 0) {
        linkDisplayName += URL_PORT_SEPARATOR;
      }
      linkDisplayName += port.hostPort;
    }

    if (linkDisplayName.length > 0) {
      linkDisplayName += URL_PORT_SEPARATOR;
    }
    linkDisplayName += port.containerPort;

    if (port.protocol) {
      linkDisplayName += '/' + port.protocol;
    }

    return linkDisplayName;
  },

  joinString: function(stringArray) {
    if (!stringArray) {
      return '';
    }

    if (!$.isArray(stringArray)) {
      return stringArray;
    }

    return stringArray.join(', ');
  },

  // Checks for deep equality for objects and primitives
  // http://stackoverflow.com/questions/1068834/object-comparison-in-javascript/6713782#6713782
  equals: function(x, y) {
    // if both x and y are null or undefined and exactly the same
    if (x === y) {
      return true;
    }

    // if they are not strictly equal, they both need to be Objects
    if (!(x instanceof Object) || !(y instanceof Object)) {
      return false;
    }

    // they must have the exact same prototype chain, the closest we can do is
    // test there constructor.
    if (x.constructor !== y.constructor) {
      return false;
    }

    for (var p in x) {
      // other properties were tested using x.constructor === y.constructor
      if (!x.hasOwnProperty(p)) {
        continue;
      }

      // allows to compare x[ p ] and y[ p ] when set to undefined
      if (!y.hasOwnProperty(p)) {
        return false;
      }

      // if they have the same strict value or identity then they are equal
      if (x[p] === y[p]) {
        continue;
      }

      // Numbers, Strings, Functions, Booleans must be strictly equal
      if (typeof(x[p]) !== 'object') {
        return false;
      }

      // Objects and Arrays must be tested recursively
      if (!utils.equals(x[p], y[p])) {
        return false;
      }
    }

    for (p in y) {
      // allows x[ p ] to be set to undefined
      if (y.hasOwnProperty(p) && !x.hasOwnProperty(p)) {
        return false;
      }
    }
    return true;
  },

  xor: function(a, b) {
    return !a !== !b;
  },

  arrayToObject: function(customProperties) {
    if (!customProperties) {
      return null;
    }

    var result = {};
    for (var i = 0; i < customProperties.length; i++) {
      var prop = customProperties[i];
      result[prop.name] = prop.value;
    }

    return result;
  },

  objectToArray: function(customProperties) {
    if (!customProperties) {
      return null;
    }

    var result = [];
    for (var key in customProperties) {
      if (customProperties.hasOwnProperty(key)) {
        result.push({
          name: key,
          value: customProperties[key]
        });
      }
    }

    return result;
  },

  resultToArray: function(resultObject) {
    if (!resultObject) {
      return null;
    }

    if ($.isArray(resultObject)) {
      return resultObject;
    }

    var result = [];
    for (var key in resultObject) {
      if (resultObject.hasOwnProperty(key)) {
        result.push(resultObject[key]);
      }
    }

    return result;
  },

  objectToKeyArray: function(object) {
    if (!object) {
      return [];
    }

    var result = [];
    for (var key in object) {
      if (object.hasOwnProperty(key)) {
        result.push(key);
      }
    }

    return result;
  },


  /** Xenon services return object, where CAFE returns Array, so convert all to object  */
  iterableToObject: function(iterable, transformFn) {
    var result = {};
    if (!transformFn) {
      transformFn = function(item) {
        return item;
      };
    }

    for (var key in iterable) {
      if (iterable.hasOwnProperty(key)) {
        var item = iterable[key];
        result[item.documentSelfLink] = transformFn(item);
      }
    }
    return result;
  },

  propertiesToArray: function(properties) {
    var array = [];

    if (properties) {
      for (var key in properties) {
        if (properties.hasOwnProperty(key)) {
          var value = properties[key];
          var keyValuePair = {
            'key': key,
            'value': value
          };
          array.push(keyValuePair);
        }
      }
    }

    return array;
  },

  arrayToProperties: function(array) {
    var properties;

    if (array && array.length) {
      properties = {};

      for (var i = 0; i < array.length; i++) {
       properties[array[i].key] = array[i].value;
      }
    }

    return properties;
  },

  getObjectPropertyValue: function(obj, propertyName) {
    let value;

    if (obj && propertyName && obj.hasOwnProperty(propertyName)) {
       value = obj[propertyName];
    }

    return value;
  },

  getCustomPropertyValue: function(customProperties, name) {
    if (!customProperties) {
      return null;
    }

    let value = null;
    if ($.isArray(customProperties)) {
      let customProperty = customProperties.find((customProperty) => {
        return customProperty.name === name;
      });
      value = customProperty ? customProperty.value : null;
    } else {
      value = this.getObjectPropertyValue(customProperties, name);
    }

    return (value === '') ? null : value;
  },

  getHostName: function(host) {
    if (!host) {
      return null;
    }

    if (host.name) {
      return host.name;
    }

    let customProps = host.customProperties;

    if (customProps) {
      let hostAlias = this.getCustomPropertyValue(customProps, '__hostAlias') ;

      if (hostAlias) {
        return hostAlias;
      }

      let name = this.getCustomPropertyValue(customProps, '__Name');

      if (name) {
        return name;
      }
    }

    var urlParts = this.getURLParts(host.address);
    return urlParts.host;
  },

  getDisplayableCustomProperties: function(customProperties) {
    if (!customProperties) {
      return {};
    }

    let isArray = $.isArray(customProperties);
    let customPropertiesArr = isArray ? customProperties
                                : this.objectToArray(customProperties);

    let displayableCustomProperties = customPropertiesArr.filter((customProperty) => {
      return !customProperty.name.startsWith('__');
    });

    return isArray ? displayableCustomProperties : this.arrayToObject(displayableCustomProperties);
  },

  getSystemCustomProperties: function(customProperties) {
    if (!customProperties) {
      return {};
    }

    let isArray = $.isArray(customProperties);
    let customPropertiesArr = isArray ? customProperties
                                : this.objectToArray(customProperties);

    let systemCustomProperties = customPropertiesArr.filter((customProperty) => {
      return customProperty.name.startsWith('__');
    });

    return isArray ? systemCustomProperties : this.arrayToObject(systemCustomProperties);
  },

  getGroup: function(tenantLinks) {
    if (!tenantLinks) {
      return null;
    }

    if (ft.showProjectsInNavigation()) {
      let projectId = tenantLinks.find((tenantLink) => {
        return tenantLink.indexOf('/projects/') > -1;
      });

      return projectId;
    }

    let groupId = tenantLinks.find((tenantLink) => {
      return tenantLink.indexOf('/groups/') > -1;
    });

    return groupId;
  },

  getErrorMessage: function(e) {
    let errorMessage;

    if (e.status === constants.ERRORS.NOT_FOUND) {
      errorMessage = i18n.t('errors.itemNotFound');
    } else {

      let errorResponse;
      try {
        errorResponse = e.responseText && JSON.parse(e.responseText);
      } catch (ex) {
        // do nothing
      }

      if (errorResponse) {
        if (errorResponse.errors && errorResponse.errors.length > 0) {
          if (errorResponse.errors[0].message) {
            errorMessage = errorResponse.errors[0].message;
          } else {
            errorMessage = errorResponse.errors[0].systemMessage;
          }
        } else if (errorResponse.message) {
          errorMessage = errorResponse.message;
        }
      }

      if (!errorMessage) {
        errorMessage = (e.responseJSON && e.responseJSON.message)
                         || e.message || e.statusText || e.responseText ;
      }
    }

    return {
      _generic: errorMessage
    };
  },

  getValidationErrors: function(e) {
    return this.getErrorMessage(e);
  },

  maskValueIfEncrypted: function(value) {
    if (value && value.indexOf(constants.CREDENTIALS_PASSWORD_ENCRYPTED) === 0) {
      return '****************';
    }

    return value;
  },
  unmaskValueIfEncrypted: function(value, initialValue) {
    if (value && value.indexOf('****************') > -1) {
      return initialValue;
    }

    return value;
  },

  uuid: function() {
    var d = new Date().getTime();
    if (window.performance && typeof window.performance.now === 'function') {
        d += performance.now(); //use high-precision timer if available
    }
    var uuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        var r = (d + Math.random() * 16) % 16 | 0;
        d = Math.floor(d / 16);
        return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
    });
    return uuid;
  },

  templateSortFn: function(a, b) {
    var aStars = a.star_count || 0;
    var bStars = b.star_count || 0;
    if (aStars === bStars) {
      var aName = a.name || '';
      var bName = b.name || '';
      return aName.localeCompare(bName);
    } else if (aStars < bStars) {
      return 1;
    } else {
      return -1;
    }
  },

  mergeDocuments: function(items1, items2, prop = 'documentSelfLink') {
    return items1.concat(items2).filter((item, index, self) =>
        self.findIndex((c) => c[prop] === item[prop]) === index);
  },

  calculatePercentageOfTotal: function(min, max, current) {
    var percentage = 100;
    if (!max) {
      percentage = 0;
    } else if (max - min > 0) {
      percentage = ((current - min) / (max - min)) * 100;
    }

    return Math.round(percentage * 100) / 100;
  },

  getClosureIcon: function(runtime) {
    let runtimeIcon = 'image-assets/closure-unknown.png';
    if (runtime) {
      if (runtime.startsWith('nodejs')) {
        runtimeIcon = 'image-assets/closure-nodejs.png';
      } else if (runtime.startsWith('python')) {
        runtimeIcon = 'image-assets/closure-python.png';
      } else if (runtime.startsWith('powershell')) {
        runtimeIcon = 'image-assets/closure-powershell.png';
      } else if (runtime.startsWith('java')) {
        runtimeIcon = 'image-assets/closure-java.png';
      }
    }
    return runtimeIcon;
  },

  getClosureRuntimeName: function(runtime) {
    let runtimeName = 'Unknown';
    if (runtime) {
      runtimeName = runtime.replace('_', ' ');
      runtimeName = runtimeName.charAt(0).toUpperCase() + runtimeName.slice(1);
    }

    return runtimeName;
  },

  findVolume: function(containerVolumeString, volumes) {
    let foundVolume;

    if (volumes) {

      let idxContainerVolNameEnd = containerVolumeString.indexOf(':');

      let containerVolumeName = (idxContainerVolNameEnd > -1)
        && containerVolumeString.substring(0, idxContainerVolNameEnd);

      foundVolume = utils.findBestMatch(containerVolumeName, volumes);
    }

    return foundVolume;
  },

  findBestMatch: function(stringToBeMatched, allValues) {
    if (!stringToBeMatched || !allValues) {
      return null;
    }

    let searchIn = allValues.filter(a => a.name.indexOf(stringToBeMatched) > -1);

    if (!searchIn || searchIn.length === 0) {
      return null;
    }

    // returns the one that matches the best
    return searchIn.reduce((a, b) => a.name.length <= b.name.length ? a : b);
  },

  sortByName: function(stringToBeMatched, allValues) {
    let sortedResults = [];
    let coppiedValues = allValues.slice();

    //on each iteration find the one whose name is most close to the searched string
    //if no good match is found the rest of the result are simply concatenated
    while (coppiedValues.length !== 0) {
      let exact = utils.findBestMatch(stringToBeMatched, coppiedValues);
      if (!exact) {
        sortedResults = sortedResults.concat(coppiedValues);
        break;
      }
      sortedResults.push(exact);
      coppiedValues.splice(coppiedValues.indexOf(exact), 1);
    }

    return sortedResults;
  },

  createTagAssignmentRequest: function(resourceLink, originalTags, newTags) {
    var tagInArray = function(tag, array) {
      return array.find(item => item.key === tag.key && item.value === tag.value) !== undefined;
    };
    var stripExtraFields = function(tag) {
      return {
        external: false,
        key: tag.key,
        value: tag.value
      };
    };
    var tagsToUnassign = originalTags
        .filter(tag => !tagInArray(tag, newTags))
        .map(stripExtraFields);
    var tagsToAssign = newTags.filter(tag => !tagInArray(tag, originalTags));

    if (tagsToUnassign.length === 0 && tagsToAssign.length === 0) {
        return null;
    }

    let request = {
      resourceLink: resourceLink,
      tagsToUnassign: tagsToUnassign,
      tagsToAssign: tagsToAssign
    };
    return request;
  },

  processTagsForDisplay: function(modelTags) {
    let tagsInputValue = '';

    if (modelTags) {
      modelTags.forEach((item) => {
        tagsInputValue += item.key + ':' + item.value + ' ';
      });
    }

    return tagsInputValue.trim();
  },

  processTagsForSave: function(tagsData) {
    let tagsInputValue = tagsData || '';

    let tags = tagsInputValue.trim().split(' ');

    // remove duplicate tags, transform data
    tags = tags.reduce((prev, curr) => {
      let pair = curr.split(':');
      let item = {
        key: pair[0],
        value: pair[1] || ''
      };

      if (prev.find((tag) => tag.key === item.key && tag.value === item.value)) {
        return prev;
      }

      return [...prev, item];
    }, []);

    return tags;
  },

  getDocumentArray(documentResponse) {
    return documentResponse.documentLinks.map((documentLink) => {
      return documentResponse.documents[documentLink];
    });
  },

  getSelectedProject() {
    try {
      return JSON.parse(localStorage.getItem('selectedProject'));
    } catch (e) {
      console.log('Failed to retrieve selectedProject entry from local storage.');
    }
  },

  convertToGigabytes(bytes) {
    return (bytes / 1073741824).toFixed(2);
  },

  getUnifiedState(cs) {
    if (!cs) {
      return null;
    }

    var state = cs.lifecycleState;
    if (state === 'RETIRED' || state === 'PROVISIONING') {
      return state;
    }
    return cs.powerState;
  },

  getContainersRefreshInterval() {
    if (this.existsConfigurationProperty('eventual.containers.refresh.interval.ms')) {

      return parseInt(this.getConfigurationProperty('eventual.containers.refresh.interval.ms'), 10)
              || constants.CONTAINERS.DEFAULT_REFRESH_INTERVAL;
    }

    return constants.CONTAINERS.DEFAULT_REFRESH_INTERVAL;
  },

  actionAllowed(roles) {
    return window.isAccessAllowed(window.authSession, null,
        roles);
  },

  isContainerDeveloper() {
    return window.isContainerDeveloper(window.authSession);
  },

  isContainersTabOpened() {
    if (!this.isApplicationEmbedded()) {
      return true;
    }

    return window.frameElement.topLocation &&
        window.frameElement.topLocation.startsWith('#/home/');
  }
};

var defaultServiceUrl = function(path) {
  return path;
};

// It could be set on App level to send calls to a different base.
utils.serviceUrl = function(path) {
 if (window.getBaseServiceUrl) {
   return window.getBaseServiceUrl(path);
 }
 return defaultServiceUrl(path);
};

export default utils;
