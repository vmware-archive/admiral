/*
  This task will do the following:
  1. Go through all .js files in our src/js
  2. Transform them from es6 to es5 using babel. This is needed order to use es6 syntax and still being browser compatible.
  3. Concatenate them in one file. So that we have one distributable of our .js. The vendor files are separate.
  4. If we are building for production, i.e. "gulp --type production", we will also minify the files using uglifyjs.
  5. Create a sourcemap of the files, so that even if they are concatenated and uglified, we can browse and debug them in the browser's developer console
  6. Publish all of the above into dist/
*/

var gulp = require('gulp');
var gutil = require('gulp-util');
var config = require('../config');
var webpackProcessor = require('../webpack-processor');
var fs = require('fs');
var path = require('path');

var srcPath = 'src/js/';
var jsFile = '.js';

gulp.task('process-sources', function (callback) {
  var srcFiles = fs.readdirSync(srcPath).filter(function(file) {
    return (file.indexOf(jsFile) + jsFile.length === file.length) &&
      !fs.statSync(path.join(srcPath, file)).isDirectory();
  });

  var trackCallbacks = srcFiles.length;
  var firstError;

  var localCallback = function(err) {
    if (err && !firstError) {
      firstError = err;
    }
    if (--trackCallbacks === 0) {
      callback(firstError);
    }
  };

  srcFiles.forEach(function (file) {
    webpackProcessor({
      watch: false,
      minify: !!config.production,
      filename: file
    }, localCallback);
  });
});