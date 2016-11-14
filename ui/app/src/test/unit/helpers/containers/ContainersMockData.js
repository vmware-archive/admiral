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

var ContainersMockData = {};

ContainersMockData.parentHosts = [{
  "id": "test-id",
  "descriptionLink": "/resources/compute-descriptions/docker-host-compute-desc-id",
  "resourcePoolLink": "/resources/pools/default-placement-zone",
  "address": "test-id:2376",
  "powerState": "ON",
  "customProperties": {
    "Name": "TestHostName",
    "__hostAlias": ""
  },
  "documentKind": "com:vmware:photon:controller:model:resources:ComputeService:ComputeState",
  "documentSelfLink": "/resources/compute/test-docker-host-compute-id"
}];

ContainersMockData.containerIds = [
  "docker-dcp-test-dcp697",
  "docker-dcp-test-dcp698",
  "docker-dcp-test-dcp699",
  "docker-dcp-test-dcp700"
];

ContainersMockData.containers = {
  "/resources/containers/wordpress-dcp696": {
    "id": "bdfc2c6e-72fb-4728-8a77-36045fbbb6ce",
    "names": [
      "wordpress-dcp696"
    ],
    "descriptionLink": "/resources/container-descriptions/5b225c25-20e4-48bf-a523-e4dacf43941d",
    "compositeComponentLink": "/resources/composite-components/224e1027-1744-4156-ae4f-bf436cec292e",
    "address": "192.168.1.129",
    "adapterManagementReference": "http://127.0.0.1:8282/adapters/docker-service",
    "powerState": "RUNNING",
    "ports": [
      {
        "hostPort": "55419",
        "containerPort": "80"
      }
    ],
    "image": "vmware-registry:5000/wordpress:4.3.1",
    "command": [
      "/bin/bash",
      "-c",
      "export WORDPRESS_DB_HOST=$MYSQL_PORT_3306_TCP_ADDR:$MYSQL_PORT_3306_TCP_PORT ; /entrypoint.sh apache2-foreground"
    ],
    "parentLink": "/resources/compute/test-docker-host-compute-id",
    "groupResourcePlacementLink": "/resources/group-placements/default-resource-placement",
    "status": "Started",
    "created": 1445613814650,
    "customProperties": {
      "_leaseDays": "3",
      "__composition_context_id": "224e1027-1744-4156-ae4f-bf436cec292e"
    },
    "attributes": {
      "NetworkSettings": "{\"IPAddress\": \"10.23.47.89\"}",
      "Config": "{\"Cmd\": [\"/bin/sh\",\"-c\",\"exit 9\"], \"Hostname\": \"ba033ac44011\", \"Image\": \"vmware-registry:5000/wordpress:4.3.1\"}",
      "HostConfig": "{\"CpuShares\": 12, \"CpusetCpus\": 33, \"Memory\": 245, \"MemorySwap\": 123}"
    },
    "group": "docker-test",
    "documentVersion": 2,
    "documentEpoch": 0,
    "documentKind": "com:vmware:admiral:compute:container:ContainerService:ContainerState",
    "documentSelfLink": "/resources/containers/wordpress-dcp696",
    "documentUpdateTimeMicros": 1445613814652023,
    "documentUpdateAction": "PATCH",
    "documentExpirationTimeMicros": 0,
    "documentOwner": "6efe58e1-5e5e-4a62-a9d3-23dd694dbfe8",
    "documentAuthPrincipalLink": "/core/authz/system-user"
  },
  "/resources/containers/docker-dcp-test-dcp700": {
    "id": "cd1a8bf7-ab41-400b-b560-a5544daf67c7",
    "names": [
      "docker-dcp-test-dcp700"
    ],
    "descriptionLink": "/resources/container-descriptions/dcp-test:latest-id",
    "address": "192.168.1.129",
    "adapterManagementReference": "http://127.0.0.1:8282/adapters/docker-service",
    "powerState": "RUNNING",
    "ports": [
      {
        "hostIp": "127.0.0.1",
        "hostPort": "20917",
        "containerPort": "8282",
        "protocol": "tcp"
      }
    ],
    "image": "dcp-test:latest",
    "command": [
      "/etc/hosts",
      "-"
    ],
    "parentLink": "/resources/compute/test-docker-host-compute-id",
    "groupResourcePlacementLink": "/resources/group-placements/default-resource-placement",
    "status": "Started",
    "created": 1445621700908,
    "attributes": {
      "NetworkSettings": "{\"IPAddress\": \"10.23.47.89\"}",
      "Config": "{\"Cmd\": [\"/bin/sh\",\"-c\",\"exit 9\"], \"Hostname\": \"ba033ac44011\", \"Image\": \"dcp-test:latest\"}",
      "HostConfig": "{\"CpuShares\": 12, \"CpusetCpus\": 33, \"Memory\": 245, \"MemorySwap\": 123}"
    },
    "group": "docker-test",
    "documentVersion": 4,
    "documentEpoch": 0,
    "documentKind": "com:vmware:admiral:compute:container:ContainerService:ContainerState",
    "documentSelfLink": "/resources/containers/docker-dcp-test-dcp700",
    "documentUpdateTimeMicros": 1445621702913074,
    "documentUpdateAction": "PATCH",
    "documentExpirationTimeMicros": 0,
    "documentOwner": "6efe58e1-5e5e-4a62-a9d3-23dd694dbfe8"
  },
  "/resources/containers/wordpress-dcp695": {
    "id": "4012cb83-cfb0-4c83-9275-f55fe6da6ae6",
    "names": [
      "wordpress-dcp695"
    ],
    "descriptionLink": "/resources/container-descriptions/5b225c25-20e4-48bf-a523-e4dacf43941d",
    "compositeComponentLink": "/resources/composite-components/224e1027-1744-4156-ae4f-bf436cec292e",
    "address": "192.168.1.129",
    "adapterManagementReference": "http://127.0.0.1:8282/adapters/docker-service",
    "powerState": "RUNNING",
    "ports": [
      {
        "hostPort": "15728",
        "containerPort": "80"
      }
    ],
    "image": "vmware-registry:5000/wordpress:4.3.1",
    "command": [
      "/bin/bash",
      "-c",
      "export WORDPRESS_DB_HOST=$MYSQL_PORT_3306_TCP_ADDR:$MYSQL_PORT_3306_TCP_PORT ; /entrypoint.sh apache2-foreground"
    ],
    "parentLink": "/resources/compute/test-docker-host-compute-id",
    "groupResourcePlacementLink": "/resources/group-placements/default-resource-placement",
    "status": "Started",
    "created": 1445613814650,
    "customProperties": {
      "_leaseDays": "3",
      "__composition_context_id": "224e1027-1744-4156-ae4f-bf436cec292e"
    },
    "attributes": {
      "NetworkSettings": "{\"IPAddress\": \"10.23.47.89\"}",
      "Config": "{\"Cmd\": [\"/bin/sh\",\"-c\",\"exit 9\"], \"Hostname\": \"ba033ac44011\", \"Image\": \"vmware-registry:5000/wordpress:4.3.1\"}",
      "HostConfig": "{\"CpuShares\": 12, \"CpusetCpus\": 33, \"Memory\": 245, \"MemorySwap\": 123}"
    },
    "group": "docker-test",
    "documentVersion": 2,
    "documentEpoch": 0,
    "documentKind": "com:vmware:admiral:compute:container:ContainerService:ContainerState",
    "documentSelfLink": "/resources/containers/wordpress-dcp695",
    "documentUpdateTimeMicros": 1445613814652040,
    "documentUpdateAction": "PATCH",
    "documentExpirationTimeMicros": 0,
    "documentOwner": "6efe58e1-5e5e-4a62-a9d3-23dd694dbfe8",
    "documentAuthPrincipalLink": "/core/authz/system-user"
  },
  "/resources/containers/docker-dcp-test-dcp698": {
    "id": "28912231-bf5e-4397-b40f-d95dcb21e1a3",
    "names": [
      "docker-dcp-test-dcp698"
    ],
    "descriptionLink": "/resources/container-descriptions/dcp-test:latest-id",
    "address": "192.168.1.129",
    "adapterManagementReference": "http://127.0.0.1:8282/adapters/docker-service",
    "powerState": "RUNNING",
    "ports": [
      {
        "hostIp": "127.0.0.1",
        "hostPort": "57870",
        "containerPort": "8282",
        "protocol": "tcp"
      }
    ],
    "image": "vmware-registry:5000/admiral:latest",
    "command": [
      "/etc/hosts",
      "-"
    ],
    "parentLink": "/resources/compute/test-docker-host-compute-id",
    "groupResourcePlacementLink": "/resources/group-placements/default-resource-placement",
    "status": "Started",
    "created": 1445621690187,
    "attributes": {
      "NetworkSettings": "{\"IPAddress\": \"10.23.47.89\"}",
      "Config": "{\"Cmd\": [\"/bin/sh\",\"-c\",\"exit 9\"], \"Hostname\": \"ba033ac44011\", \"Image\": \"vmware-registry:5000/admiral:latest\"}",
      "HostConfig": "{\"CpuShares\": 12, \"CpusetCpus\": 33, \"Memory\": 245, \"MemorySwap\": 123}"
    },
    "group": "docker-test",
    "documentVersion": 4,
    "documentEpoch": 0,
    "documentKind": "com:vmware:admiral:compute:container:ContainerService:ContainerState",
    "documentSelfLink": "/resources/containers/docker-dcp-test-dcp698",
    "documentUpdateTimeMicros": 1445621692190041,
    "documentUpdateAction": "PATCH",
    "documentExpirationTimeMicros": 0,
    "documentOwner": "6efe58e1-5e5e-4a62-a9d3-23dd694dbfe8"
  },
  "/resources/containers/docker-dcp-test-dcp699": {
    "id": "246e11a1-9ffc-4600-b686-247ad28dd040",
    "names": [
      "docker-dcp-test-dcp699"
    ],
    "descriptionLink": "/resources/container-descriptions/dcp-test:latest-id",
    "address": "192.168.1.129",
    "adapterManagementReference": "http://127.0.0.1:8282/adapters/docker-service",
    "powerState": "RUNNING",
    "ports": [
      {
        "hostIp": "127.0.0.1",
        "hostPort": "47685",
        "containerPort": "8282",
        "protocol": "tcp"
      }
    ],
    "image": "dcp-test:latest",
    "command": [
      "/etc/hosts",
      "-"
    ],
    "parentLink": "/resources/compute/test-docker-host-compute-id",
    "groupResourcePlacementLink": "/resources/group-placements/default-resource-placement",
    "status": "Started",
    "created": 1445621695798,
    "attributes": {
      "NetworkSettings": "{\"IPAddress\": \"10.23.47.89\"}",
      "Config": "{\"Cmd\": [\"/bin/sh\",\"-c\",\"exit 9\"], \"Hostname\": \"ba033ac44011\", \"Image\": \"dcp-test:latest\"}",
      "HostConfig": "{\"CpuShares\": 12, \"CpusetCpus\": 33, \"Memory\": 245, \"MemorySwap\": 123}"
    },
    "group": "docker-test",
    "documentVersion": 4,
    "documentEpoch": 0,
    "documentKind": "com:vmware:admiral:compute:container:ContainerService:ContainerState",
    "documentSelfLink": "/resources/containers/docker-dcp-test-dcp699",
    "documentUpdateTimeMicros": 1445621697791075,
    "documentUpdateAction": "PATCH",
    "documentExpirationTimeMicros": 0,
    "documentOwner": "6efe58e1-5e5e-4a62-a9d3-23dd694dbfe8"
  },
  "/resources/containers/mysql-dcp694": {
    "id": "10a48672-8d59-438b-b4f4-7f92a4fc2e72",
    "names": [
      "mysql-dcp694"
    ],
    "descriptionLink": "/resources/container-descriptions/9fea220e-ff44-4796-9698-7523f61304a9",
    "compositeComponentLink": "/resources/composite-components/224e1027-1744-4156-ae4f-bf436cec292e",
    "address": "192.168.1.129",
    "adapterManagementReference": "http://127.0.0.1:8282/adapters/docker-service",
    "powerState": "RUNNING",
    "ports": [
      {
        "hostPort": "40477",
        "containerPort": "3306"
      }
    ],
    "image": "vmware-registry:5000/mariadb:10.0.21",
    "parentLink": "/resources/compute/test-docker-host-compute-id",
    "groupResourcePlacementLink": "/resources/group-placements/default-resource-placement",
    "status": "Started",
    "created": 1445613811531,
    "customProperties": {
      "_leaseDays": "3",
      "__composition_context_id": "224e1027-1744-4156-ae4f-bf436cec292e"
    },
    "attributes": {
      "NetworkSettings": "{\"IPAddress\": \"10.23.47.89\"}",
      "Config": "{\"Cmd\": [\"/bin/sh\",\"-c\",\"exit 9\"], \"Hostname\": \"ba033ac44011\", \"Image\": \"vmware-registry:5000/mariadb:10.0.21\"}",
      "HostConfig": "{\"CpuShares\": 12, \"CpusetCpus\": 33, \"Memory\": 245, \"MemorySwap\": 123}"
    },
    "group": "docker-test",
    "documentVersion": 2,
    "documentEpoch": 0,
    "documentKind": "com:vmware:admiral:compute:container:ContainerService:ContainerState",
    "documentSelfLink": "/resources/containers/mysql-dcp694",
    "documentUpdateTimeMicros": 1445613811534011,
    "documentUpdateAction": "PATCH",
    "documentExpirationTimeMicros": 0,
    "documentOwner": "6efe58e1-5e5e-4a62-a9d3-23dd694dbfe8"
  },
  "/resources/containers/docker-dcp-test-dcp697": {
    "id": "8c5f8aa8-37b1-4f14-9357-e21616690565",
    "names": [
      "docker-dcp-test-dcp697"
    ],
    "descriptionLink": "/resources/container-descriptions/dcp-test:latest-id2",
    "address": "192.168.1.129",
    "adapterManagementReference": "http://127.0.0.1:8282/adapters/docker-service",
    "powerState": "RUNNING",
    "ports": [
      {
        "hostIp": "127.0.0.1",
        "hostPort": "6259",
        "containerPort": "8282",
        "protocol": "tcp"
      }
    ],
    "image": "vmware-registry:5000/admiral:latest",
    "command": [
      "/etc/hosts",
      "-"
    ],
    "parentLink": "/resources/compute/test-docker-host-compute-id",
    "groupResourcePlacementLink": "/resources/group-placements/default-resource-placement",
    "status": "Started",
    "created": 1445621682408,
    "attributes": {
      "NetworkSettings": "{\"IPAddress\": \"10.23.47.89\"}",
      "Config": "{\"Cmd\": [\"/bin/sh\",\"-c\",\"exit 9\"], \"Hostname\": \"ba033ac44011\", \"Image\": \"vmware-registry:5000/admiral:latest\"}",
      "HostConfig": "{\"CpuShares\": 12, \"CpusetCpus\": 33, \"Memory\": 245, \"MemorySwap\": 123}"
    },
    "group": "docker-test",
    "documentVersion": 4,
    "documentEpoch": 0,
    "documentKind": "com:vmware:admiral:compute:container:ContainerService:ContainerState",
    "documentSelfLink": "/resources/containers/docker-dcp-test-dcp697",
    "documentUpdateTimeMicros": 1445621684415074,
    "documentUpdateAction": "PATCH",
    "documentExpirationTimeMicros": 0,
    "documentOwner": "6efe58e1-5e5e-4a62-a9d3-23dd694dbfe8"
  }
};

ContainersMockData.compositeComponentIds = [
  "224e1027-1744-4156-ae4f-bf436cec292e"
];

ContainersMockData.compositeComponentContainerIds = [
  "mysql-dcp694",
  "wordpress-dcp695",
  "wordpress-dcp696"
];

ContainersMockData.compositeComponents = {
  "/resources/composite-components/224e1027-1744-4156-ae4f-bf436cec292e": {
    "name": "wordPressWithMySql2",
    "compositeDescriptionLink": "/resources/composite-descriptions/wordPressWithMySql2",
    "componentLinks": [
      "/resources/containers/wordpress-dcp696",
      "/resources/containers/wordpress-dcp695",
      "/resources/containers/mysql-dcp694"
    ],
    "documentVersion": 0,
    "documentEpoch": 0,
    "documentKind": "com:vmware:admiral:compute:container:CompositeComponentService:CompositeComponent",
    "documentSelfLink": "/resources/composite-components/224e1027-1744-4156-ae4f-bf436cec292e",
    "documentUpdateTimeMicros": 1445613808913003,
    "documentUpdateAction": "POST",
    "documentExpirationTimeMicros": 0,
    "documentOwner": "6efe58e1-5e5e-4a62-a9d3-23dd694dbfe8"
  }
};

export default ContainersMockData;