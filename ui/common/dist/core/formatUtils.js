/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */
var $ = require('jquery');
var byteUnits = ['Bytes', 'kB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];
var configurationAdapters = null;
var configurationProperties = null;
var isInteger = function (integer, min, max) {
    var range = {};
    range.min = min !== undefined ? min : -2147483648;
    range.max = max !== undefined ? max : 2147483647;
    return validator.isInt(integer, range);
};
var utils = {
    // Formats a numeric bytes value to the most appropriate (close) string metric
    // http://stackoverflow.com/a/18650828
    formatBytes: function (bytes) {
        var size = utils.fromBytes(bytes);
        return size.value + ' ' + size.unit;
    },
    toBytes: function (value, unit) {
        var k = 1024;
        var i = byteUnits.indexOf(unit);
        return value * Math.pow(k, i);
    },
    fromBytes: function (bytes) {
        if (bytes === 0) {
            return {
                value: 0,
                unit: byteUnits[0]
            };
        }
        var k = 1024;
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
    calculateMemorySize: function (bytes) {
        var size = utils.fromBytes(bytes);
        // KB is the smallest unit shown in the UI
        if (size.unit === byteUnits[0]) {
            var k = 1024;
            var value = (size.value / k);
            if (Math.round(value) !== value) {
                value = value.toFixed();
            }
            size.unit = byteUnits[1]; // kB
        }
        return size;
    },
    escapeHtml: function (htmlString) {
        return $('<div>').text(htmlString).html();
    }
};
module.exports = utils;
//# sourceMappingURL=formatUtils.js.map