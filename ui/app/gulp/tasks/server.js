/*
  This task starts a small webserver to serve the application. It adds watches to files of interest, that when change, will trigger different build tasks and reload the browser. This way one can just run "gulp" from the console, develop and see the changes instantly refreshed in the browser
*/

var gulp = require('gulp');
var connect = require('gulp-connect');
var config = require('../config');

gulp.task('server', function() {
  connect.server(config.server);

  gulp.watch(config.processSources.src, ['process-sources']);
  gulp.watch(config.styles.src, ['styles']);
  gulp.watch(config.html.src, ['html']);
  gulp.watch(config.i18n.src, ['i18n']);
  gulp.watch(config.templates.src, ['templates']);
  gulp.watch(config.templatesVue.src, ['templates-vue']);
});