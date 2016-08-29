/*
  Copies the html files from source to destination. No specific manipualtions.
*/

var gulp = require('gulp');
var connect = require('gulp-connect');
var config = require('../config');

gulp.task('html', function() {
  return gulp.src(config.html.src)
    .pipe(gulp.dest(config.html.dest))
    .pipe(connect.reload());
});
