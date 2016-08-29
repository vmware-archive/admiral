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
var sourcemaps = require('gulp-sourcemaps');
var babel = require('gulp-babel');
var concat = require('gulp-concat');
var uglify = require('gulp-uglify');
var gulpif = require('gulp-if');
var connect = require('gulp-connect');
var config = require('../config');

gulp.task('process-sources', ['eslint'], function () {
  gulp.src(config.processSources.indexSrc)
    .pipe(gulp.dest(config.processSources.indexDest));

  return gulp.src([config.processSources.src, '!' + config.processSources.indexSrc])
    .pipe(sourcemaps.init())
    .pipe(babel({
      modules: 'amd',
      moduleIds: true
    }))
    .pipe(concat('all.js'))
    .pipe(gulpif(config.production, uglify()))
    .pipe(sourcemaps.write('.'))
    .pipe(gulp.dest(config.processSources.dest))
    .pipe(connect.reload());
});