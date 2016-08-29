/*
  This is the default task that is executed after running "gulp". The second parameter are the dependencies, so everything in the array, will be exectuded and completed before this task. Since this task will not have any specific logic, we will only assign different tasks to be executed by default.
*/

var gulp = require('gulp');
gulp.task('default', ['build', 'server'], function() {
  gulp.start('unit-tests-continious');
});