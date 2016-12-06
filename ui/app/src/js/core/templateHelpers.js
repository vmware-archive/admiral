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
import constants from 'core/constants';
import Handlebars from 'handlebars/runtime';

var templateHelpers = {};
templateHelpers.register = function() {
  Handlebars.registerHelper('i18n', function(i18nKey) {
    i18nKey = Handlebars.Utils.escapeExpression(i18nKey);
    return i18n.t(i18nKey);
  });

  Handlebars.registerHelper('i18n-appmode-aware', function(i18nStandaloneKey, i18nEmbeddedKey) {
    var keyToUse;
    if (utils.isApplicationEmbedded()) {
      keyToUse = i18nEmbeddedKey;
    } else {
      keyToUse = i18nStandaloneKey;
    }

    keyToUse = Handlebars.Utils.escapeExpression(keyToUse);
    return i18n.t(keyToUse);
  });

  /*
    Handlebars helper to allow entering expressions to check if 2 objects are equal.
    Example: {{#ifEq model.propA 'someValue'}}...{{/ifEq}}
  */
  Handlebars.registerHelper('ifEq', function(a, b, options) {
    if (a === b) {
      return options.fn(this);
    }

    return options.inverse(this);
  });

  Handlebars.registerHelper('timestampToDate', function(timestamp) {
    if (timestamp) {
      // Formats based on locale, like August 27, 2015 2:25 PM
      return moment(timestamp).format('LLL');
    }
  });

  Handlebars.registerHelper('containerStatus', utils.containerStatusDisplay);

  Handlebars.registerHelper('formatBytes', utils.formatBytes);

  Handlebars.registerHelper('getDocumentId', utils.getDocumentId);

  /*
    Handlebars helper to display an array of strings to a readable string,
    by separating with comma
  */
  Handlebars.registerHelper('arrayToString', function(array) {
    if ($.isArray(array)) {
      return array.join(', ');
    }
  });

  Handlebars.registerHelper('placementZonePercentageLevel', function(percentage) {
    if (percentage < 50) {
      return 'success';
    } else if (percentage < 80) {
      return 'warning';
    } else {
      return 'danger';
    }
  });

  var portsToString = function(ports) {
    var toReturn = '';
    if (ports) {
      for (var i = 0; i < ports.length; i++) {
        var port = ports[i];
        var portString = '';

        // Used backend's com.vmware.vcac.container.domain.PortBinding.toString() to format the
        // ports string
        if (port.hostIp) {
          portString += port.hostIp + ':';
        }

        if (port.hostPort) {
          portString += port.hostPort;
        }

        if (portString.length > 0) {
          portString += ':';
        }

        portString += port.containerPort;

        if (port.protocol) {
          portString += '/' + port.protocol;
        }

        toReturn += portString + '\n';
      }
    }

    return toReturn;
  };

  /*
    Handlebars helper to display a containers ports object into known string representation used
    in Docker CLI - 127.0.0.1:32777 -> 8080/tcp
  */
  Handlebars.registerHelper('portsToString', portsToString);

  Vue.filter('portsToString', portsToString);
  Vue.filter('momentHumanize', function(durationMs) {
    return moment.duration(durationMs).humanize();
  });
  Vue.filter('get-service', function(link) {
    return link.split(':')[0];
  });
  Vue.filter('get-alias', function(link) {
    return link.split(':')[1];
  });

  /* A filter that makes the object as mutable. Useful when passing to Vue where it should modify
  the object or calling function like sort() on an array*/
  Vue.filter('asMutable', function(obj) {
    return obj ? obj.asMutable({deep: true}) : obj;
  });

  Vue.filter('timestampToDate', function(timestamp) {
    if (timestamp) {
      // Formats based on locale, like August 27, 2015 2:25 PM
      return moment(timestamp).format('LLL');
    }
  });

  Vue.mixin({
    methods: {
      i18n: function(i18nKey) {
        return i18n.t(i18nKey);
      },

      isLoading: function(value) {
        return value === constants.LOADING;
      },

      keys: function(object) {
        return object ? Object.keys(object) : [];
      }
    }
  });

  Vue.transition('slide-and-fade', {
    css: false,
    enter: function(el, done) {
      utils.slideToLeft($(el), false, done);
    },
    enterCancelled: function(el) {
      $(el).stop();
    },
    leave: function(el, done) {
      utils.fadeOut($(el), done);
    },
    leaveCancelled: function(el) {
      $(el).stop();
    }
  });

  Vue.transition('fade', {
    css: false,
    enter: function(el, done) {
      utils.fadeIn($(el), done);
    },
    enterCancelled: function(el) {
      $(el).stop();
    },
    leave: function(el, done) {
      utils.fadeOut($(el), done);
    },
    leaveCancelled: function(el) {
      $(el).stop();
    }
  });

  Vue.transition('fade-in', {
    css: false,
    enter: function(el, done) {
      utils.fadeIn($(el), done);
    },
    enterCancelled: function(el) {
      $(el).stop();
    },
    leave: function(_, done) {
      done();
    },
    leaveCancelled: function() {
    }
  });

  Vue.transition('fade-out', {
    css: false,
    enter: function(_, done) {
      done();
    },
    enterCancelled: function() {
    },
    leave: function(el, done) {
      utils.fadeOut($(el), done);
    },
    leaveCancelled: function(el) {
      $(el).stop();
    }
  });

  Vue.directive('tooltip', {
    update: function(newValue) {
      $(this.el).tooltip({
        title: newValue,
        trigger: 'hover click',
        html: true,
        viewport: this.el.parentElement
      });
    }
  });
};

export default templateHelpers;
