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

var TemplatesMockData = {};

TemplatesMockData.TemplateWithNetworking = {
  "name": "wordPressWithMySql",
  "descriptionLinks": [
    "/resources/container-descriptions/wordpress",
    "/resources/container-descriptions/mysql",
    "/resources/container-descriptions/haproxy",
    "/resources/container-network-descriptions/mynet"
  ],
  "documentSelfLink": "/resources/composite-descriptions/wordPressWithMySql"
};

TemplatesMockData.WordpressCD = {
  "image": "wordpress",
  "_cluster": 2,
  "networkMode": "bridge",
  "name": "wordpress",
  "documentSelfLink": "/resources/container-descriptions/wordpress"
};

TemplatesMockData.MysqlCD = {
  "image": "mysql",
  "networks": {
    "mynet": {}
  },
  "networkMode": "bridge",
  "name": "mysql",
  "documentSelfLink": "/resources/container-descriptions/mysql"
};

TemplatesMockData.HaproxyCD = {
  "image": "haproxy",
  "networks": {
    "mynet": {}
  },
  "name": "haproxy",
  "documentSelfLink": "/resources/container-descriptions/haproxy"
};

TemplatesMockData.Mynet = {
  "driver": "overlay",
  "name": "mynet",
  "documentSelfLink": "/resources/container-network-descriptions/mynet"
};
