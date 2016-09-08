var dest = './dist';
var src = './src';
var gutil = require('gulp-util');

var url = require('url');
var proxyMiddleware = require('proxy-middleware');

var jsLibsToCopy = [
  './node_modules/jquery/dist/jquery.js',
  './node_modules/es5-shim/es5-shim.js',
  './node_modules/babel-core/browser-polyfill.js',
  './node_modules/handlebars/dist/handlebars.js',
  './node_modules/i18next-client/i18next.js',
  './node_modules/bootstrap-sass/assets/javascripts/bootstrap.js',
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
  './node_modules/d3/d3.js',
  './node_modules/moment/min/moment-with-locales.js',
  './node_modules/requirejs/require.js',
  './node_modules/tablesort/src/tablesort.js',
  './node_modules/tablesort/src/sorts/tablesort.date.js',
  './node_modules/tablesort/src/sorts/tablesort.numeric.js'
];

var jsLibsToCopyMinified = [
  './node_modules/jquery/dist/jquery.min.js',
  './node_modules/es5-shim/es5-shim.min.js',
  './node_modules/babel-core/browser-polyfill.min.js',
  './node_modules/handlebars/dist/handlebars.min.js',
  './node_modules/i18next-client/i18next.min.js',
  './node_modules/bootstrap-sass/assets/javascripts/bootstrap.min.js',
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
  './node_modules/d3/d3.min.js',
  './node_modules/moment/min/moment-with-locales.min.js',
  './node_modules/requirejs/require.js',
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

var pathsToProxy = ["/config", "/core", "/images", "/popular-images", "/requests", "/delete-tasks", "/request-status", "/resources", "/provisioning", "/templates", "/user-session", "/container-image-icons", "/rp"];

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
  processSources: {
    src: src + '/js/**/*.js',
    dest: dest + '/js',
    indexSrc: src + '/js/main*.js',
    indexDest: dest + '/js'
  },
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
    dest: dest + '/image-assets',
  },
  fonts: {
    src: './node_modules/font-awesome/fonts/**/*.*',
    dest: dest + '/fonts',
  },
  styles: {
    src: [src + '/styles/**/*.{sass,scss,css}', './node_modules/bootstrap-tokenfield/dist/css/*.css'],
    dest: dest + '/styles',
    includePaths:  ['./node_modules/bootstrap-sass/assets/stylesheets', './node_modules/font-awesome/scss']
  },
  i18n: {
    src: src + '/messages/**/*.json',
    dest: dest + '/messages'
  },
  templates: {
    src: src + '/**/*Template.html',
    dest: dest + '/template-assets',
    concat: 'all.js'
  },
  templatesVue: {
    src: src + '/**/*Vue.html',
    dest: dest + '/template-assets',
    concat: 'all-vue.js'
  },
  server: {
    root: dest,
    host: 'localhost',
    port: 10082,
    livereload: {
      port: 35930
    },
    middleware: function(connect, o) {
      return getDevServerProxies();
    }
  },
  tests: {
    browser: TEST_ENV.browser || 'PhantomJS',
    continious: TEST_ENV.continious || false,
    unit: {
      src: [
        dest + '/lib/vendor.js',
        dest + '/js/all.js',
        {pattern: dest + '/js/all.js.map', included: false},
        './node_modules/jasmine-ajax/lib/mock-ajax.js',
        'node_modules/karma-requirejs/lib/adapter.js',
        {pattern: src + '/test/unit/**/*Test.js', included: false},
        src + '/test/common/helpers/**/*.js',
        src + '/test/unit/helpers/**/*.js',
        {pattern: src + '/test/*.js.map', included: false}
      ],
      reportOutputFile: 'target/surefire-reports/TEST-results.xml'
    },
    it: {
      src: [
        dest + '/lib/vendor.js',
        dest + '/js/all.js',
        dest + '/template-assets/all.js',
        dest + '/template-assets/all-vue.js',
        {pattern: dest + '/js/all.js.map', included: false},
        './node_modules/jasmine-ajax/lib/mock-ajax.js',
        'node_modules/karma-requirejs/lib/adapter.js',
        {pattern: src + '/test/it/**/*IT.js', included: false},
        src + '/test/common/helpers/**/*.js',
        src + '/test/it/helpers/**/*.js',
        {pattern: src + '/test/*.js.map', included: false}
      ],
      reportOutputFile: 'target/failsafe-reports/TEST-results.xml'
    },
    proxies: getKarmaServerProxies()
  },
  copyApiTests: {
    src:  src + '/test/api/**/*.js',
    dest: dest + '/apiTests'
  },
  src: src,
  dest: dest,
  production: gutil.env.type === 'production',
  INTEGRATION_TEST_PROPERTIES: INTEGRATION_TEST_PROPERTIES
};