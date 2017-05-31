
let env = {
  services: {
    ip: 'localhost',
    port: '8282'
  }
}

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
  '/harbor/*',
  '/auth/*'
];

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

configs['/index-no-navigation.html*'] = {
  'target': `http://${env.services.ip}:8282/`,
  'secure': false
}

configs['/lib/*'] = {
  'target': `http://${env.services.ip}:8282/`,
  'secure': false
}
configs['/js/*'] = {
  'target': `http://${env.services.ip}:8282/`,
  'secure': false
}
configs['/styles/*'] = {
  'target': `http://${env.services.ip}:8282/`,
  'secure': false
}
configs['/messages/*'] = {
  'target': `http://${env.services.ip}:8282/`,
  'secure': false
}

configs['/fonts/*'] = {
  'target': `http://${env.services.ip}:8282/`,
  'secure': false
}

module.exports = configs;
