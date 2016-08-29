/*
  This task takes all "*Template.html" files, passes them through Handlebar's compiler and creates a module for them, to be used in other components.
  Components will only need to do:

  import template from "HomeTemplate";

  or

  require('HomeTemplate', function(template) {
  });

  and call "template()" to get the html of the template. If the template expects data, it may also be provided.
*/

var gulp = require('gulp');
var stripComments = require('gulp-strip-comments');
var handlebars = require('gulp-handlebars');
var defineModule = require('gulp-define-module');
var concat = require('gulp-concat');
var connect = require('gulp-connect');
var config = require('../config');

gulp.task('templates', function(){
  return gulp.src(config.templates.src)
    .pipe(stripComments())
    .pipe(handlebars())
    .pipe(defineModule('plain', {
        wrapper: 'define("<%= name %>", function() { return <%= handlebars %>; })'
    }))
    .pipe(concat(config.templates.concat))
    .pipe(gulp.dest(config.templates.dest))
    .pipe(connect.reload());
});