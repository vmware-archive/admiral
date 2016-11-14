/*
  This task starts a small webserver to serve the application. It adds watches to files of interest, that when change, will trigger different build tasks and reload the browser. This way one can just run "gulp" from the console, develop and see the changes instantly refreshed in the browser
*/

var gulp = require('gulp');
var connect = require('gulp-connect');
var config = require('../config');
var path = require('path');
var webpackProcessor = require('../webpack-processor');

gulp.task('server', function() {
  var bundler = webpackProcessor({
    watch: true,
    minify: false,
    filename: 'main.js'
  }, function(err) {
    if (!err) {
      gulp.src('dist/js/*').pipe(connect.reload());
    } else if (err.length) {
      err.forEach(function(e) {
        console.log(e);
      });
    } else {
      console.log(err);
    }
  });

  config.server.middleware = function() {
    var result = [];
    return result.concat(config.getDevServerProxies());
  };

  connect.server(config.server);

  gulp.watch(config.styles.src, ['styles']);
  gulp.watch(config.html.src, ['html']);
  gulp.watch(config.i18n.src, ['i18n']);
});