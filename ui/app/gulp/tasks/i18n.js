/*
  Copies the i18n files from source to destination. No specific manipualtions.
*/

var gulp = require('gulp');
var connect = require('gulp-connect');
var config = require('../config');

gulp.task('i18n', function() {
  return gulp.src(config.i18n.src)
    .pipe(gulp.dest(config.i18n.dest))
    .pipe(connect.reload());
});
