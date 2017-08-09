#!/bin/sh

#Set property
#$1 old admiral node
#$2 new admiral node
#$3 token

send_transformation_request() {
  echo "Sending POST to $2"
  status_code=$(curl -k --max-time 1200 -X POST -d "" "https://$1/$2" -H "Content-type: application/json" -H "x-xenon-auth-token: $3" -w %{http_code} -s --output /dev/null)
  if [[ $status_code != 200 ]]; then
    echo "Error during transformation $2 please check xenon logs for more information!"
    exit 1
  else
    echo "$2 completed"
  fi
}

URL="https://$1/config/healthcheck"
status_code=$( curl --max-time 10 -k -H "x-xenon-auth-token: $auth_token" -w %{http_code} -s --output /dev/null $URL)
if [[ $status_code != 200 ]]; then
  counter=6
  while [[ $counter -gt 0 && $status_code -ne 200 ]]; do
    echo "Retrying $counter times to get response from: $URL"
    sleep 10
    status_code=$( curl --max-time 10 -k -H "x-xenon-auth-token: $auth_token" -w %{http_code} -s --output /dev/null $URL)
    counter=$(( counter - 1 ))
  done
fi

if [[ $status_code != 200 ]]; then
  echo "Node $1 not available"
  exit 1
else
  echo "Old admiral node is ready for migration: $1"

  URL="https://$2/config/healthcheck"
  status_code=$( curl --max-time 10 -k -H "x-xenon-auth-token: $3" -w %{http_code} -s --output /dev/null $URL)
  if [[ $status_code != 200 ]]; then
    counter=6
    while [[ $counter -gt 0 && $status_code -ne 200 ]]; do
      echo "Retrying $counter times to get response from: $URL"
      sleep 10
      status_code=$( curl --max-time 10 -k -H "x-xenon-auth-token: $3" -w %{http_code} -s --output /dev/null $URL)
      counter=$(( counter - 1 ))
    done
  fi

  if [[ $status_code != 200 ]]; then
    echo "Node $2 not available"
    exit 1
  else
    echo "New admiral node is ready for migration: $2"
    echo "Starting migration of the data"
    migrationBody="{\"sourceNodeGroup\": \"https://$1/core/node-groups/default\"}"
    status_code=$(curl -k --max-time 1200 -X POST -d "$migrationBody" "https://$2/config/migration" -H "Content-type: application/json" -H "x-xenon-auth-token: $3" -w %{http_code} -s --output /dev/null)
    echo "Status code from migration: $status_code"
    if [[ $status_code != 200 ]]; then
      echo "Error during migration please check xenon logs for more information!"
      exit 1
    else
      echo "Migration completed"
    fi

    send_transformation_request $2 "upgrade-transforms/computes" $3
    send_transformation_request $2 "upgrade-transforms/pools" $3
    send_transformation_request $2 "upgrade-transforms/containers" $3
    send_transformation_request $2 "upgrade-transforms/networks" $3
    send_transformation_request $2 "upgrade-transforms/volumes" $3
    send_transformation_request $2 "upgrade-transforms/composite-components" $3
    send_transformation_request $2 "upgrade-transforms/composite-descriptions" $3
    send_transformation_request $2 "upgrade-transforms/registries" $3
    exit 0
  fi
fi