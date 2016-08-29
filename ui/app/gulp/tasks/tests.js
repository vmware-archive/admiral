/*
  Set of tasks for testing. There is task for executing unit tests as well as integration. All test are run inside a browser environment, by default phantomJS.
*/

var gulp = require('gulp');
var karma = require('karma').server;
var config = require('../config');

var UNIT_TEST_PORT = 9876;
var UNIT_TEST_SUITE = 'com.vmware.admiral.ui';
var IT_TEST_PORT = 9877;
var IT_TEST_SUITE = 'com.vmware.admiral.ui.integration';

var getPreporcessorts = function() {
  var preprocessors = {};
  preprocessors[config.dest + '/**/*.js'] = ['sourcemap'];
  preprocessors[config.src + '/test/unit/**/*Test.js'] = ['babel', 'sourcemap', 'global'];
  preprocessors[config.src + '/test/it/**/*IT.js'] = ['babel', 'sourcemap', 'global'];
  return preprocessors;
};

var createCommonTestsConfig =  function(files, reporters, reportOutputFile, singleRun, port, suite) {
  return {
    basePath: '',
    frameworks: ['jasmine'],
    reporters: reporters,
    junitReporter : {
      suite: suite,
      outputFile: reportOutputFile
    },
    files: files,
    preprocessors: getPreporcessorts(),
    babelPreprocessor: {
      options: {
        modules: 'amd',
        sourceMap: 'inline'
      }
    },
    port: port,
    colors: false,
    logLevel: config.LOG_INFO,
    autoWatch: true,
    browsers: [
      config.tests.browser
    ],
    proxies: config.tests.proxies,
    singleRun: singleRun,
    globals: config.INTEGRATION_TEST_PROPERTIES
  }
};

/* Runs unit and integration tests. Called during development and when doing mvn test */
gulp.task('tests', ['unit-tests', 'it-tests']);

gulp.task('unit-tests', function (done) {
  var reporters = ['spec'];
  if (!config.tests.continious) {
    reporters.push('junit');
  }
  if (config.tests.browser !== 'PhantomJS') {
    reporters.push('html');
  }

  var testConfig = createCommonTestsConfig(config.tests.unit.src, reporters, config.tests.unit.reportOutputFile, !config.tests.continious, UNIT_TEST_PORT, UNIT_TEST_SUITE);

  karma.start(testConfig, function(exitStatus) {
    done(exitStatus ? "There are failing unit tests" : undefined);
  });
});

gulp.task('it-tests', function (done) {
  var reporters = ['spec'];
  if (!config.tests.continious) {
    reporters.push('junit');
  }
  if (config.tests.browser !== 'PhantomJS') {
    reporters.push('html');
  }

  var testConfig = createCommonTestsConfig(config.tests.it.src, reporters, config.tests.it.reportOutputFile, !config.tests.continious, IT_TEST_PORT, IT_TEST_SUITE);

  karma.start(testConfig, function(exitStatus) {
    done(exitStatus ? "There are failing integration tests" : undefined);
  });
});

/* Runs only unit tests. Expected to be run as part of continious development run - "gulp", on every file change. Unit tests should be simple and fast and MUST NOT manipulate DOM. */
gulp.task('unit-tests-continious', function (done) {
  var testConfig = createCommonTestsConfig(config.tests.unit.src, ['spec'], null, false, UNIT_TEST_PORT);

  karma.start(testConfig, function(exitStatus) {
    done(exitStatus ? "There are failing tests" : undefined);
  });
});