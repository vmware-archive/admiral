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

import { Constants } from './constants';

/**
 * Search utility logic.
 */
export class SearchUtils {

    public static getQueryOptions(searchQueryString: string, occurrenceSelection: string): any {
        let queryOptions = {};

        if (searchQueryString && searchQueryString.trim().length > 0) {
            // occurrence selection
            let occurrenceValue = Constants.SEARCH.OCCURRENCE.ALL;

            if (occurrenceSelection && occurrenceSelection.trim().length > 0) {
                if (occurrenceSelection === Constants.SEARCH.OCCURRENCE.ANY) {
                    occurrenceValue = Constants.SEARCH.OCCURRENCE.ANY;
                }
            }
            queryOptions[Constants.SEARCH.OCCURRENCE.PARAM] = occurrenceValue;

            // search tokens
            let searchTokens = searchQueryString.split(' ');
            for (let idx = 0; idx < searchTokens.length; idx++) {
                let searchToken = searchTokens[idx];
                let tupleKey;
                let tupleValue;

                let idxSeparator = searchToken.indexOf(':');
                if (idxSeparator > -1) {
                    if (searchToken.endsWith(':')) { // key:  value - space after :
                        tupleKey = searchToken.substring(0, searchToken.length - 1);
                        idx++;
                        tupleValue = searchTokens[idx];
                    } else { // key:value - no space after :
                        tupleKey = searchToken.substring(0, idxSeparator);
                        tupleValue = searchToken.substring(idxSeparator + 1, searchToken.length);
                    }
                } else {
                    tupleKey = 'any';
                    tupleValue = searchToken;
                }

                // remove # from tupleValue
                tupleValue = tupleValue.replace(/#/g, '');

                if (queryOptions[tupleKey]) {
                    queryOptions[tupleKey].push(tupleValue);
                } else {
                    queryOptions[tupleKey] = [tupleValue];
                }
            }
        }

        return queryOptions;
    }

    public static getSearchString(queryOptions: any) : string {
        if (!queryOptions) {
            return '';
        }

        let searchString = '';

        for (let key in queryOptions) {
            if (!queryOptions.hasOwnProperty(key)) {
                continue;
            }

            let queryOption = queryOptions[key];
            let currentOptions = [];
            if (Array.isArray(queryOption)) {
                currentOptions = queryOption;
            } else if (queryOption) {
                currentOptions = [queryOption];
            }

            for (let idxOption = 0; idxOption < currentOptions.length; idxOption++) {
                if (key === Constants.SEARCH.CATEGORY
                    || key === Constants.SEARCH.OCCURRENCE.PARAM) {

                    // do nothing
                } else if (key === Constants.SEARCH.OCCURRENCE.ANY) {

                    searchString += ' ' + currentOptions[idxOption];
                } else {

                    searchString += ' ' + key + ': ' + currentOptions[idxOption];
                }
            }
        }

        return searchString.trim();
    }

    public static getOccurrence(queryOptions: any) : string {
        if (!queryOptions || !queryOptions.hasOwnProperty(Constants.SEARCH.OCCURRENCE.PARAM)
            || !queryOptions[Constants.SEARCH.OCCURRENCE.PARAM]) {

            return Constants.SEARCH.OCCURRENCE.ALL;
        }

        return queryOptions[Constants.SEARCH.OCCURRENCE.PARAM];
    }

    public static getCategory(queryOptions: any) : string {
        if (!queryOptions || !queryOptions.hasOwnProperty(Constants.SEARCH.CATEGORY)
            || !queryOptions[Constants.SEARCH.CATEGORY]) {

            return undefined;
        }

        return queryOptions[Constants.SEARCH.CATEGORY];
    }
}
