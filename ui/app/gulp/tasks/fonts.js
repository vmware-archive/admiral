/*
  Copies the fonts from source to destination. No specific manipualtions.
*/

var gulp = require('gulp');
var config = require('../config');

gulp.task('fonts', function() {
  return gulp.src(config.fonts.src)
    .pipe(gulp.dest(config.fonts.dest));
});
