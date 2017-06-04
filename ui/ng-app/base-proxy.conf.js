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
  '/config/*',
  '/templates/*',
  '/groups/*',
  '/image-assets/*',
  '/user-session/*',
  '/popular-images/*',
  '/hbr-api/*',
  '/auth/*',
  '/index-no-navigation.html*',
  '/lib/*',
  '/js/*',
  '/styles/*',
  '/messages/*',
  '/fonts/*',
  '/util/*'
];

var configure = function(env) {
  var configs = {};

  ENDPOINTS.forEach((e) => {
    configs[e] = {
      'target': `http://${env.services.ip}:${env.services.port}`,
      'secure': false
    };
  });

  configs['/assets/i18n/base*.json'] = {
    'target': `http://${env.services.ip}:${env.services.port}/ng`,
    'secure': false
  }

  return configs;
}


module.exports = configure;
