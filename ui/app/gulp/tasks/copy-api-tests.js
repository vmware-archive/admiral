/* Task that will copy the required files to load and run CAFE API tests, to verify the Admiral CAFE proxy covers all UI REST calls. */


var gulp = require('gulp');
var config = require('../config');

gulp.task('copy-api-tests', [], function () {
  return gulp.src(config.copyApiTests.src)
    .pipe(gulp.dest(config.copyApiTests.dest));
});