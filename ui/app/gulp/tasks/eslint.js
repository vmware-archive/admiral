var gulp   = require('gulp');
var eslint = require('gulp-eslint');
var config = require('../config');

gulp.task('eslint', function() {
  return gulp.src(config.processSources.src)
    .pipe(eslint())
    .pipe(eslint.format())
    .pipe(eslint.failAfterError());
});


