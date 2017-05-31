var dest = './dist';
var src = './src';
var gutil = require('gulp-util');

var url = require('url');
var proxyMiddleware = require('proxy-middleware');

var jsLibsToCopy = [
  './node_modules/jquery/dist/jquery.js',
  './node_modules/es5-shim/es5-shim.js',
  './node_modules/babel-polyfill/dist/polyfill.js',
  './node_modules/i18next/i18next.js',
  './node_modules/i18next-browser-languagedetector/i18nextBrowserLanguageDetector.js',
  './node_modules/i18next-xhr-backend/i18nextXHRBackend.js',
  './node_modules/tether/dist/js/tether.js',
  './node_modules/bootstrap/dist/js/bootstrap.js',
  './node_modules/reflux/dist/reflux.js',
  './node_modules/signals/dist/signals.min.js',
  './node_modules/crossroads/dist/crossroads.js',
  './node_modules/hasher/dist/js/hasher.js',
  './node_modules/seamless-immutable/seamless-immutable.development.js',
  './node_modules/validator/validator.js',
  './node_modules/wamda-typeahead/dist/typeahead.jquery.js',
  './node_modules/bootstrap-tokenfield/dist/bootstrap-tokenfield.js',
  './node_modules/vue/dist/vue.js',
  './node_modules/vue-infinite-scroll/vue-infinite-scroll.js',
  './node_modules/jsplumb/dist/js/jsPlumb-2.1.5.js',
  './node_modules/moment/min/moment-with-locales.js',
  './node_modules/ace-builds/src-noconflict/ace.js',
  './node_modules/ace-builds/src-noconflict/ext-language_tools.js',
  './node_modules/ace-builds/src-noconflict/mode-javascript.js',
  './node_modules/ace-builds/src-noconflict/mode-python.js',
  './node_modules/ace-builds/src-noconflict/snippets/javascript.js',
  './node_modules/ace-builds/src-noconflict/snippets/python.js',
  './node_modules/ace-builds/src-noconflict/mode-json.js',
  './node_modules/ace-builds/src-noconflict/mode-text.js',
  './node_modules/tablesort/src/tablesort.js',
  './node_modules/tablesort/src/sorts/tablesort.date.js',
  './node_modules/tablesort/src/sorts/tablesort.numeric.js'
];

var jsLibsToCopyMinified = [
  './node_modules/jquery/dist/jquery.min.js',
  './node_modules/es5-shim/es5-shim.min.js',
  './node_modules/babel-polyfill/dist/polyfill.min.js',
  './node_modules/i18next/i18next.min.js',
  './node_modules/i18next-browser-languagedetector/i18nextBrowserLanguageDetector.min.js',
  './node_modules/i18next-xhr-backend/i18nextXHRBackend.min.js',
  './node_modules/tether/dist/js/tether.min.js',
  './node_modules/bootstrap/dist/js/bootstrap.min.js',
  './node_modules/reflux/dist/reflux.min.js',
  './node_modules/signals/dist/signals.min.js',
  './node_modules/crossroads/dist/crossroads.min.js',
  './node_modules/hasher/dist/js/hasher.min.js',
  './node_modules/seamless-immutable/seamless-immutable.production.min.js',
  './node_modules/validator/validator.min.js',
  './node_modules/wamda-typeahead/dist/typeahead.jquery.min.js',
  './node_modules/bootstrap-tokenfield/dist/bootstrap-tokenfield.min.js',
  './node_modules/vue/dist/vue.min.js',
  './node_modules/vue-infinite-scroll/vue-infinite-scroll.js',
  './node_modules/jsplumb/dist/js/jsPlumb-2.1.5-min.js',
  './node_modules/moment/min/moment-with-locales.min.js',
  './node_modules/ace-builds/src-min-noconflict/ace.js',
  './node_modules/ace-builds/src-min-noconflict/ext-language_tools.js',
  './node_modules/ace-builds/src-min-noconflict/mode-javascript.js',
  './node_modules/ace-builds/src-min-noconflict/mode-python.js',
  './node_modules/ace-builds/src-min-noconflict/snippets/javascript.js',
  './node_modules/ace-builds/src-min-noconflict/snippets/python.js',
  './node_modules/ace-builds/src-min-noconflict/mode-json.js',
  './node_modules/ace-builds/src-min-noconflict/mode-text.js',
  './node_modules/tablesort/tablesort.min.js',
  './node_modules/tablesort/src/sorts/tablesort.date.js',
  './node_modules/tablesort/src/sorts/tablesort.numeric.js'
];

var TEST_ENV = gutil.env.test || {};

var DEFAULT_PROPERTIES_FILE = '../../test-integration/src/test/resources/integration-test.properties';
var propertiesFile = TEST_ENV.integration && TEST_ENV.integration.properties;
if (!propertiesFile || propertiesFile === "${test.integration.properties}") {
  propertiesFile = DEFAULT_PROPERTIES_FILE;
}

/* Parses the properties files for the Java integration tests */
var readProperties = function(propertiesFile) {
  gutil.log("Reading Admiral interation test properties from '" + propertiesFile + "'...");
  var fs = require('fs');
  var propertiesParser = require('properties-parser');

  var propertiesContent = fs.readFileSync(propertiesFile);
  return propertiesParser.parse(propertiesContent);
};

var INTEGRATION_TEST_PROPERTIES = readProperties(propertiesFile);

var proxy = function(path) {
  var options = url.parse(ADMIRAL_URL + path);
  options.route = path;
  return proxyMiddleware(options);
};

var getTestDcpUrl = function() {
  var url =  TEST_ENV.dcp && TEST_ENV.dcp.url;

  // When running maven and the "test.dcp.url" variable is not set it will be evaluated to "${test.dcp.url}"
  if (!url || url == "${test.dcp.url}") {
    var providedPropertiesFiles = TEST_ENV.integration && TEST_ENV.integration.properties;
    var propertiesFile = providedPropertiesFiles || '../../test-integration/src/test/resources/integration-test.properties';

    url = INTEGRATION_TEST_PROPERTIES['test.dcp.url'];
  }

  gutil.log('Using Admiral URL: ' + url);

  return url;
};

var ADMIRAL_URL = getTestDcpUrl();

var NG_URL = 'http://127.0.0.1:4200/';
if (TEST_ENV.dcp && TEST_ENV.dcp.ngurl) {
  NG_URL = TEST_ENV.dcp.ngurl;
}

gutil.log('Using NG URL: ' + NG_URL);

var pathsToProxy = ["/adapter", "/config", "/core", "/images", "/popular-images", "/requests", "/request-graph", "/delete-tasks", "/request-status", "/resources", "/provisioning", "/templates", "/user-session", "/container-image-icons", "/rp", "/projects", "/util"];

/* Utilities to proxy calls from "/path" to "ADMIRAL/path".
The Karma proxies are needed for integration tests where tests are run on a built in karma server, but are making REST calls to "ADMIRAL".
Also, useful for development where the UI needs to make actual calls to the backend, while still having the benefit of fast build/reload cycles. */
var getDevServerProxies = function() {
  var proxies = [];

  for (var i = 0; i < pathsToProxy.length; i++) {
    var path = pathsToProxy[i];

    var options = url.parse(ADMIRAL_URL + path);
    options.route = path;
    proxies.push(proxyMiddleware(options));
  }

  var ngoptions = url.parse(NG_URL);
  ngoptions.route = '/ng';
  proxies.push(proxyMiddleware(ngoptions));

  return proxies;
};

var getKarmaServerProxies = function() {
  var proxies = {};

  for (var i = 0; i < pathsToProxy.length; i++) {
    var path = pathsToProxy[i];
    proxies[path] = ADMIRAL_URL + path;
  }

  return proxies;
};

module.exports = {
  processVendorLibs: {
    jsToCopy: gutil.env.type === 'production' ? jsLibsToCopyMinified : jsLibsToCopy,
    dest: dest + '/lib'
  },
  html: {
    src: ['src/index*.html', 'src/login.html'],
    dest: dest
  },
  images: {
    src: src + '/image-assets/**/*.*',
    dest: dest + '/image-assets'
  },
  fonts: {
    src: './node_modules/font-awesome/fonts/**/*.*',
    dest: dest + '/fonts'
  },
  styles: {
    src: [
      src + '/styles/**/*.{sass,scss,css}',
      './node_modules/bootstrap-tokenfield/dist/css/*.css',
      './node_modules/clarity-ui/*.css',
      './node_modules/font-awesome/css/*.css',
      './node_modules/admiral-ui-common/css/*.css'
    ],
    dest: dest + '/styles',
    includePaths: [
      './node_modules/bootstrap-sass/assets/stylesheets',
      './node_modules/clarity-ui/src'
    ]
  },
  i18n: {
    src: src + '/messages/**/*.json',
    dest: dest + '/messages'
  },
  server: {
    root: dest,
    host: 'localhost',
    port: 10082,
    livereload: {
      port: 35930
    }
  },
  tests: {
    browser: TEST_ENV.browser || 'PhantomJS',
    continious: TEST_ENV.continious || false,
    unit: {
      src: [
        dest + '/lib/vendor.js',
        './node_modules/jasmine-ajax/lib/mock-ajax.js',
        src + '/test/common/helpers/includeGlobals.js',
        {pattern: src + '/test/unit/all-tests.js'}
      ],
      reportOutputFile: 'target/surefire-reports/TEST-results.xml'
    },
    it: {
      src: [
        dest + '/lib/vendor.js',
        './node_modules/jasmine-ajax/lib/mock-ajax.js',
        src + '/test/common/helpers/includeGlobals.js',
        {pattern: src + '/test/it/all-tests.js'}
      ],
      reportOutputFile: 'target/failsafe-reports/TEST-results.xml'
    },
    proxies: getKarmaServerProxies()
  },
  src: src,
  dest: dest,
  production: gutil.env.type === 'production',
  INTEGRATION_TEST_PROPERTIES: INTEGRATION_TEST_PROPERTIES,
  getDevServerProxies: getDevServerProxies
};