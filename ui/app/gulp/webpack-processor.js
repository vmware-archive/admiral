var webpack = require('webpack');
var path = require('path');

function beep() {
  console.log('\u0007');
}

function processWebpack(options, callback) {
  var config = getWebpackConfig(options);

  webpack(config, function(err, stats) {

    if (err && err.error) {
      err = err.error;
    }
    if (!err) {
      if (stats && stats.compilation &&
          stats.compilation.errors && stats.compilation.errors.length) {
        err = stats.compilation.errors.map(function(e) {
          return e.message;
        });
      }
    }

    if (err) {
      console.log('error webpack for: ' + options.filename);
      if (err.length) {
        err.forEach(function(e) {
          console.log(e);
        });
      } else {
        console.log(err);
      }

      beep();
    } else {
      console.log('completed webpack for: ' + options.filename);
    }

    callback(err);
  });
}

function getWebpackConfig(options) {
  var plugins = [
    new webpack.optimize.OccurrenceOrderPlugin()
  ];

  if (options.minify) {
    plugins.push(new webpack.optimize.UglifyJsPlugin({
      compress: {
        warnings: false,
      },

      output: {
        comments: false,
        semicolons: true,
      },

      sourceMap: true
    }));
  }

  return {
    entry: [
      options.filename
    ],
    watch: options.watch,
    bail: !options.watch,
    devtool: options.minify ? 'source-map' : 'source-map',
    plugins: plugins,
    output: {
      path: path.resolve('dist/js/'),
      filename: options.filename,
      publicPath: 'js/'
    },
    resolve: {
      modules: [
        path.resolve('src/js'),
        'node_modules'
      ]
    },
    module: {
      rules: [{
        test: /Template\.html$/,
        loader: 'handlebars-loader!html-minifier-loader',
        include: path.resolve('src/js')
      }, {
        test: /Vue\.html$/,
        loader: 'raw-loader!html-minifier-loader',
        include: path.resolve('src/js')
      }, {
        loader: 'eslint-loader',
        enforce: "pre",
        test: /\.js$/,
        include: path.resolve('src/js')
      }, {
        loader: 'babel-loader?presets[]=es2015,plugins[]=transform-es2015-modules-commonjs,cacheDirectory=true',
        test: /\.js$/,
        include: path.resolve('src/js')
      }],
    }
  };
}

module.exports = processWebpack;
