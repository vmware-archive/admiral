const searchConstants = require('./searchConstants');

const DEFAULT_LIMIT = 10;

let calculateLimit = function() {
  let averageSize = 250;
  let body = document.body;
  let html = document.body;

  var h = Math.max( body.scrollHeight, body.offsetHeight,
    html.clientHeight, html.scrollHeight, html.offsetHeight );
  var w = Math.max( body.scrollWidth, body.offsetWidth,
    html.clientHeight, html.scrollWidth, html.offsetWidth );

  return Math.ceil(w / averageSize * h / averageSize) || DEFAULT_LIMIT;
};

let encodeQuotes = function(value) {
  return value.replace(/\'/g, '%2527');
};

// Simple Odata query builder. By default it will build 'and' query. If provided OCCURRENCE option,
// then it will use it to build 'and', 'or' query. Based on the option provided, it will use
// comparison like 'eq' or 'ne'
let buildOdataQuery = function(queryOptions) {
  var result = '';
  if (queryOptions) {
    var occurrence = queryOptions[searchConstants.SEARCH_OCCURRENCE.PARAM];
    delete queryOptions[searchConstants.SEARCH_OCCURRENCE.PARAM];

    var operator = occurrence === searchConstants.SEARCH_OCCURRENCE.ANY ? 'or' : 'and';

    for (var key in queryOptions) {
      if (queryOptions.hasOwnProperty(key)) {
        var query = queryOptions[key];
        if (query) {
          for (var i = 0; i < query.length; i++) {
            if (result.length > 0) {
              result += ' ' + operator + ' ';
            }
            result += key + ' ' + query[i].op + ' \'' + encodeQuotes(query[i].val) + '\'';
          }
        }
      }
    }
  }
  return result;
};

var utils = {
    calculateLimit: calculateLimit,
    buildOdataQuery: buildOdataQuery,
    encodeQuotes: encodeQuotes
};

module.exports = utils;