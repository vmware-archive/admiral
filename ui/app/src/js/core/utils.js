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

import constants from 'core/constants';
import links from 'core/links';

const URL_PROTOCOL_PART = /^(https?):\/\//;
const URL_DEFAULT_PROTOCOL = 'http://';
const URL_PORT_SEPARATOR = ':';

var isEmbedded = window.isEmbedded;
var isSingleView = window.isSingleView;

var isDocsAvailable = false;

var byteUnits = ['Bytes', 'kB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];

var configurationProperties = null;

var isInteger = function(integer, min, max) {
  var range = {};
  range.min = min !== undefined ? min : -2147483648;
  range.max = max !== undefined ? max : 2147483647;
  return validator.isInt(integer, range);
};

var utils = {
  initializeConfigurationProperties: function(props) {
    if (configurationProperties) {
      throw new Error('Properties already set');
    }
    configurationProperties = props;
  },
  getConfigurationProperty: function(property) {
    return configurationProperties && configurationProperties[property];
  },
  getConfigurationPropertyBoolean: function(property) {
    return configurationProperties && configurationProperties[property] === 'true';
  },
  setDocsAvailable: function(value) {
    isDocsAvailable = value;
  },
  existsConfigurationProperty: function(property) {
    return configurationProperties.hasOwnProperty(property);
  },
  isNetworkingAvailable: function() {
    return this.getConfigurationPropertyBoolean('allow.ft.network');
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

  deleteElementFromList(customProperties, key) {
    return $.grep(customProperties, function(e) {
      return e.name !== key;
    });
  },

  isValidPort: function(port) {
    return validator.isInt(port, {
        min: 0,
        max: 65535
      }) || validator.trim(port).match(/__null/g);
  },

  isValidContainerMemory: function(mem) {
    if (mem && mem === '0') {
      return true;
    }

    let range = {
      min: parseInt(this.getConfigurationProperty('docker.container.min.memory'), 10) || 0,
      max: 9007199254740991 // Number.MAX_SAFE_INTEGER
    };

    return validator.isInt(mem, range);
  },

  isInteger: isInteger,

  isNonNegativeInteger: function(integer) {
    return isInteger(integer, 0);
  },

  isPositiveInteger: function(integer) {
    return isInteger(integer, 1);
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

  // Formats a numeric bytes value to the most appropriate (close) string metric
  // http://stackoverflow.com/a/18650828
  formatBytes: function(bytes) {
    var size = utils.fromBytes(bytes);
    return size.value + ' ' + size.unit;
  },

  toBytes: function(value, unit) {
    var k = 1000;
    var i = byteUnits.indexOf(unit);
    return value * Math.pow(k, i);
  },

  fromBytes: function(bytes) {
    if (bytes === 0) {
      return {
        value: 0,
        unit: byteUnits[0]
      };
    }

    var k = 1000;
    var i = Math.floor(Math.log(bytes) / Math.log(k));

    var value = (bytes / Math.pow(k, i));
    if (Math.round(value) !== value) {
      value = value.toFixed(2);
    }

    return {
      value: value,
      unit: byteUnits[i]
    };
  },

  containerStatusDisplay: function(state, timestamp) {
    var stateString = '';
    if (state) {
      stateString = i18n.t('app.container.state.' + state);
      if (state === constants.CONTAINERS.STATES.RUNNING && !!timestamp) {
        var now = moment.utc();
        var stateMoment = moment.utc(timestamp);

        return i18n.t('app.container.state.continuousStateSince', {
          state: stateString,
          duration: moment.duration(now.diff(stateMoment)).humanize()
        });
      }
    }

    return stateString;
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

  operationSupported: function(op, container) {
    if (this.isSystemContainer(container)) {
      return false;

    } else if (container.type === constants.CONTAINERS.TYPES.COMPOSITE
                || container.type === constants.CONTAINERS.TYPES.CLUSTER) {
      if (op === constants.CONTAINERS.OPERATION.REMOVE) {
        return true;
      }

      let items;
      if (container.containers) {
        items = container.containers;
      } else if (container.listView) {
        items = container.listView.items;
      }

      return this.operationSupportedMulti(op, items);

    } else if (op === constants.CONTAINERS.OPERATION.STOP) {
      return (!this.isApplicationSingleView())
                && this.isContainerStatusActive(container.powerState);

    } else if (op === constants.CONTAINERS.OPERATION.START) {
      return (!this.isApplicationSingleView())
                && this.isContainerStatusInactive(container.powerState);

    } else if (op === constants.CONTAINERS.OPERATION.REMOVE) {
      return !this.isApplicationSingleView();

    } else if (op === constants.CONTAINERS.OPERATION.CLUSTERING) {
      return !this.isApplicationSingleView()
                && this.isContainerStatusOk(container.powerState);

    } else if (op === constants.CONTAINERS.OPERATION.SHELL) {
      return this.getConfigurationPropertyBoolean('allow.browser.ssh.console')
                && !this.isApplicationSingleView()
                && this.isContainerStatusOk(container.powerState);
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

  isApplicationEmbedded: function() {
    return isEmbedded;
  },

  isApplicationSingleView: function() {
    return isSingleView;
  },

  isApplicationCompute: function() {
    var locationSearch = window.location.search || '';
    return locationSearch.indexOf('compute') !== -1;
  },

  getBuildNumber: function() {
    return this.getConfigurationProperty('__build.number');
  },

  isContextAwareHelpEnabled: function() {
   return this.getConfigurationPropertyBoolean('allow.ensemble.help');
  },

  isContextAwareHelpAvailable: function() {
   return this.isContextAwareHelpEnabled() && isDocsAvailable;
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

    if (!urlParts.port) {
      if (urlParts.scheme.toLowerCase() === 'https') {
        urlParts.port = 443;
      }
      if (urlParts.scheme.toLowerCase() === 'http') {
        urlParts.port = 80;
      }
    }

    return utils.mergeURLParts(urlParts);
  },

  getPortsDisplayTexts: function(hostAddress, ports) {
    let portsDisplayTexts = [];

    if (ports) {
      hostAddress = this.formatHostAddress(hostAddress);

      for (let i = 0; i < ports.length; i++) {
        portsDisplayTexts[i] = this.getPortLinkDisplayText(hostAddress, ports[i]);
      }
    }

    return portsDisplayTexts;
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

  extractHostId: function(hostId) {
    var hostSeparator = '::';
    var id = hostId;
    var idx = id.indexOf(hostSeparator);
    if (idx !== -1) {
      id = id.substring(idx + hostSeparator.length);
    }
    return id;
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

    let customProps = host.customProperties;

    if (customProps) {
      let hostAlias = this.getCustomPropertyValue(customProps, '__hostAlias') ;

      if (hostAlias) {
        return hostAlias;
      }

      if (host.id.indexOf(host.address) > -1) {
        // id and address have the same data
        let name = this.getCustomPropertyValue(customProps, '__Name');

        if (name) {
          return name;
        }
      }
    }

    return this.extractHostId(host.id);
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

  getGroup: function(tenantLinks) {
    if (!tenantLinks) {
      return null;
    }

    let groupId = tenantLinks.find((tenantLink) => {
      return tenantLink.indexOf('/groups/') > -1;
    });

    return groupId ? groupId : tenantLinks[tenantLinks.length - 1];
  },

  getErrorMessage: function(e) {
    let errorMessage = e;

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
          errorMessage = errorResponse.errors[0].systemMessage;
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
