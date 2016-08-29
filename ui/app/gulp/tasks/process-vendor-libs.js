/*
  This task will copy and concatenate the required components from bower_components to the project's destination folder so that we can include them in our app.
*/

var gulp = require('gulp');
var concat = require('gulp-concat');
var fs = require('fs');
var config = require('../config');

gulp.task('process-vendor-libs', function () {

  validateFilesExistence(config.processVendorLibs.jsToCopy);

  return gulp.src(config.processVendorLibs.jsToCopy)
    .pipe(concat('vendor.js'))
    .pipe(gulp.dest(config.processVendorLibs.dest));
});

function validateFilesExistence(files) {
  for (var i = 0; i < files.length; i++) {
    if(!fs.existsSync(files[i])) {
       throw new Error("File " + files[i] + " does not exist.");
    }
  }
}