/*
  Copies the style files from source to destination. Uses SASS to compile the .scss files.
*/

var gulp = require('gulp');
var sass = require('gulp-sass');
var connect = require('gulp-connect');
var config = require('../config');

gulp.task('styles', function() {
  return gulp.src(config.styles.src)
    .pipe(sass({
      style: 'compressed',
      includePaths: config.styles.includePaths
    }))
    .pipe(gulp.dest(config.styles.dest))
    .pipe(connect.reload());
});
