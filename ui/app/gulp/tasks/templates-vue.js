var gulp = require('gulp');
var stripComments = require('gulp-strip-comments');
var defineModule = require('gulp-define-module');
var jsEscape = require('gulp-js-escape');
var concat = require('gulp-concat');
var connect = require('gulp-connect');
var config = require('../config');

gulp.task('templates-vue', function(){
  return gulp.src(config.templatesVue.src)
    .pipe(stripComments())
    .pipe(jsEscape())
    .pipe(defineModule('plain', {
        wrapper: 'define("<%= name %>", function() { return <%= contents  %>; })'
    }))
    .pipe(concat(config.templatesVue.concat))
    .pipe(gulp.dest(config.templatesVue.dest))
    .pipe(connect.reload());
});