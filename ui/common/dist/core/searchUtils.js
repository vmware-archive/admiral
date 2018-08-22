/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */
var searchConstants = require('./searchConstants');
var getQueryOptions = function (searchQueryString, occurrenceSelection) {
    var queryOptions = {};
    if (searchQueryString && searchQueryString.trim().length > 0) {
        // occurrence selection
        var occurrenceValue = searchConstants.SEARCH_OCCURRENCE.ALL;
        if (occurrenceSelection && occurrenceSelection.trim().length > 0) {
            if (occurrenceSelection === searchConstants.SEARCH_OCCURRENCE.ANY) {
                occurrenceValue = searchConstants.SEARCH_OCCURRENCE.ANY;
            }
        }
        queryOptions[searchConstants.SEARCH_OCCURRENCE.PARAM] = occurrenceValue;
        // search tokens
        var searchTokens = searchQueryString.split(' ');
        for (var idx = 0; idx < searchTokens.length; idx++) {
            var searchToken = searchTokens[idx];
            var tupleKey = void 0;
            var tupleValue = void 0;
            var idxSeparator = searchToken.indexOf(':');
            if (idxSeparator > -1) {
                if (searchToken.endsWith(':')) {
                    tupleKey = searchToken.substring(0, searchToken.length - 1);
                    idx++;
                    tupleValue = searchTokens[idx];
                }
                else {
                    tupleKey = searchToken.substring(0, idxSeparator);
                    tupleValue = searchToken.substring(idxSeparator + 1, searchToken.length);
                }
            }
            else {
                tupleKey = 'any';
                tupleValue = searchToken;
            }
            // remove # from tupleValue
            tupleValue = tupleValue.replace(/#/g, '');
            if (queryOptions[tupleKey]) {
                queryOptions[tupleKey].push(tupleValue);
            }
            else {
                queryOptions[tupleKey] = [tupleValue];
            }
        }
    }
    return queryOptions;
};
var getSearchString = function (queryOptions) {
    if (!queryOptions) {
        return '';
    }
    var searchString = '';
    for (var key in queryOptions) {
        if (!queryOptions.hasOwnProperty(key)) {
            continue;
        }
        var queryOption = queryOptions[key];
        var currentOptions = [];
        if (Array.isArray(queryOption)) {
            currentOptions = queryOption;
        }
        else if (queryOption) {
            currentOptions = [queryOption];
        }
        for (var idxOption = 0; idxOption < currentOptions.length; idxOption++) {
            if (key === searchConstants.SEARCH_CATEGORY_PARAM
                || key === searchConstants.SEARCH_OCCURRENCE.PARAM) {
                continue;
            }
            else if (key === 'any') {
                searchString += ' ' + currentOptions[idxOption];
            }
            else {
                searchString += ' ' + key + ': ' + currentOptions[idxOption];
            }
        }
    }
    return searchString.trim();
};
var getOccurrence = function (queryOptions) {
    if (!queryOptions || !queryOptions.hasOwnProperty(searchConstants.SEARCH_OCCURRENCE.PARAM)
        || !queryOptions[searchConstants.SEARCH_OCCURRENCE.PARAM]) {
        return searchConstants.SEARCH_OCCURRENCE.ALL;
    }
    return queryOptions[searchConstants.SEARCH_OCCURRENCE.PARAM];
};
var getCategory = function (queryOptions) {
    if (!queryOptions || !queryOptions.hasOwnProperty(searchConstants.SEARCH_CATEGORY_PARAM)
        || !queryOptions[searchConstants.SEARCH_CATEGORY_PARAM]) {
        return undefined;
    }
    return queryOptions[searchConstants.SEARCH_CATEGORY_PARAM];
};
var searchUtils = {
    getQueryOptions: getQueryOptions,
    getSearchString: getSearchString,
    getOccurrence: getOccurrence,
    getCategory: getCategory
};
module.exports = searchUtils;
//# sourceMappingURL=searchUtils.js.map