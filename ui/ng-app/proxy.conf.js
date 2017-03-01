
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
  '/messages/*'
];

var configs = {};

ENDPOINTS.forEach((e) => {
  configs[e] = {
    'target': `http://${env.services.ip}:${env.services.port}`,
    'secure': false
  };
});

module.exports = {
  "/tenants/*": {
    "target": `http://${env.services.ip}:${env.services.port}`,
    "secure": false
  },
  "/authn/*": {
    "target": `http://${env.services.ip}:${env.services.port}`,
    "secure": false
  },
  "/mgmt/*": {
    "target": `http://${env.services.ip}:${env.services.port}`,
    "secure": false
  },
  "/query/*": {
    "target": `http://${env.services.ip}:${env.services.port}`,
    "secure": false
  },
  "/core/*": {
    "target": `http://${env.services.ip}:${env.services.port}`,
    "secure": false
  },
  "/provisioning/*": {
    "target": `http://${env.services.ip}:${env.services.port}`,
    "secure": false
  },
  "/resources/*": {
    "target": `http://${env.services.ip}:${env.services.port}`,
    "secure": false
  },
  "/container-image-icons/*": {
    "target": `http://${env.services.ip}:${env.services.port}`,
    "secure": false
  },
  "/messages/*": {
    "target": `http://${env.services.ip}:${env.services.port}`,
    "secure": false
  }
};
