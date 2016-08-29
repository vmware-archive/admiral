/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

var http = require('http');
var util = require('util');
var httpProxy = require('http-proxy');

if (process.argv.length != 3) {
  console.error('Expected target address to be provided');
  return 1;
}

var targetAddress = process.argv[2];

var proxy = httpProxy.createProxyServer({});

function skipLog(req) {
  return false;
}

proxy.on('proxyRes', function (proxyRes, req, res) {

  var body = '';
  proxyRes.on('data', function(chunk) {
    body += chunk;
  });

  proxyRes.on('end', function() {
    if (!skipLog(req)) {
      console.log("Response:")
      console.log(req.method + ' ' + req.url);
      console.log("statusCode: ", proxyRes.statusCode);
      console.log('  Headers:');
      console.log(util.inspect(proxyRes.headers))
      console.log('  Body:');
      try {
        body = JSON.parse(body);
        body = JSON.stringify(body);
      } catch (e) {
        console.log('Could not parse body');
      }
      console.log(util.inspect(body))
    }
  });

});

var server = http.createServer(function(req, res) {
  var body = '';
  req.on('data', function (chunk) {
      body += chunk;
  });

  req.on('end', function() {
    if (!skipLog(req)) {

      console.log("\n \nRequest:")
      console.log(req.method + ' ' + req.url);
      console.log('  Headers:');
      console.log (util.inspect(req.headers))
      console.log('  Body:');
       try {
        body = JSON.parse(body);
        body = JSON.stringify(body);
      } catch (e) {
        console.log('Could not parse body');
      }
      console.log(util.inspect(body));
    }
  });

  proxy.web(req, res, { target: targetAddress });
});

var PORT = 6443;
console.log("listening on port " + PORT)
console.log("proxy target set to " + targetAddress)
server.listen(PORT);