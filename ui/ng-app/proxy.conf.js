
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
  '/groups/*'
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

configs['/image-assets/*'] = {
  'target': `http://${env.services.ip}:${env.services.port}/`,
  'secure': false
}

configs['/former/*'] = {
  'target': `http://${env.services.ip}:8282/`,
  'secure': false
}


module.exports = configs;
