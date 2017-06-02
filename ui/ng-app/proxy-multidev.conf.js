
let configure = require('./base-proxy.conf.js');

var configs = configure({
  services: {
    ip: 'localhost',
    port: '10082'
  }
});

module.exports = configs;
