const ENDPOINTS = [
  '/tenants/*',
  '/authn/*',
  '/mgmt/*',
  '/query/*',
  '/core/*',
  '/provisioning/*',
  '/resources/*',
  '/container-image-icons/*',
  '/projects/*',
  '/requests/*',
  '/request-status/*',
  '/config/*',
  '/templates/*',
  '/groups/*',
  '/image-assets/*',
  '/images/*',
  '/popular-images/*',
  '/hbr-api/*',
  '/auth/*',
  '/ogui/*',
  '/lib/*',
  '/js/*',
  '/styles/*',
  '/messages/*',
  '/fonts/*',
  '/util/*',
  '/adapter/*'
];

var configure = function(env) {
  var configs = {};

  ENDPOINTS.forEach((e) => {
    configs[e] = {
      'target': `http://${env.services.ip}:${env.services.port}/`,
      'secure': false
    };
  });

  configs['/assets/i18n/base*.json'] = {
    'target': `http://${env.services.ip}:${env.services.port}/`,
    'secure': false
  }

  configs['/login/*'] = {
    'changeOrigin': true,
    'pathRewrite': {'^/login': ''},
    'target': `http://${env.services.ip}:4200/`,
    'secure': false
  }

  // update the proxy configuration in case
  // old UI is running on a debug server
  if (env.services.port === '10082') {
    configs['/ogui/*'] = {
      'changeOrigin': true,
      'pathRewrite': {'^/ogui': ''},
      'target': `http://${env.services.ip}:${env.services.port}/`,
      'secure': false
    }
}

  return configs;
}

var port = process.env.NG_PORT || '8282';
console.log('proxying to localhost:' + port);

var configs = configure({
  services: {
    ip: 'localhost',
    port: port
  }
});

module.exports = configs;
