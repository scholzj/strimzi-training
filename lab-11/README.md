# Strimzi Training - Lab 11

Lab 11 is using Strimzi 0.12.0. It takes you through the changes in Strimzi 0.12.0 (AMQ Streams 1.2).
**Since 0.12.0, only Kubernetes 1.11 and newer or OpenShift 3.11 or newer are supported.**

* Checkout this repository which will be used during the lab:
  * `git clone https://github.com/scholzj/strimzi-training.git`
* Go to the `lab-11` directory
  * `cd lab-11`
* Start you OpenShift or Kubernetes cluster
  * **You should use OpenShift 3.11 or higher or Kubernetes 1.11 or higher**
  * Run `minishift start` or `oc cluster up`
* Login as cluster administrator
  * `oc login -u system:admin`
* If you are not in a project `myproject`, create it and set it as active
  * `oc new-project myproject`
  * `oc project myproject`

## Custom Resource Improvements

* Go to the `cr-improvements` directory
  * `cd cr-improvements`

### Upgrading custom resources

* Install previous version of Strimzi / AMQ Streams
  * `oc apply -f install-0.11.4/`
* Install an old Kafka cluster with version `v1alpha1`
  * `oc apply -f old-kafka.yaml`
* Update the CO to Strimzi 0.12.0 / AMQ Streams 1.2
  * `oc apply -f install-0.12.0/`
  * Wait for the rolling update to finish
* Check the custom resource
  * `oc edit kafka my-cluster`
  * Notice that the version was updated to `v1beta1`
  * Add following annotation to force the upgrade
```yaml
apiVersion: kafka.strimzi.io/v1alpha1
kind: Kafka
metadata:
  # ...
  annotations:
    upgrade: "Upgraded to kafka.strimzi.io/v1beta1"
spec:
  # ...
```

### Status sub-resource

* Once the resource is upgraded to `v1beta1` (or with all resources created after the 0.12.0 upgrade), you can check the status
  * Do `oc get kafka my-cluster -o yaml` and notice the last section of the output
  * Check the conditions
  * Check the addresses
* Modify the Kafka cluster to use external listener using OpenShift routes (e.g. using `oc edit kafka my-cluster`)
```yaml
  kafka:
    # ...
    listeners:
      # ...
      external:
        type: route
```
*  Notice how the status has been upgraded

_Optionally_

* Set the resources to number larger than what is the amount of CPU / resources you have (e.g. using `oc edit kafka my-cluster`)
```yaml
  kafka:
    # ...
    resources:
      requests:
        memory: 200Gi
        cpu: 20000m
```
* Wait for the reconciliation to fail and check that `conditions` field in the `status` sub-resource

### Handling of invalid fields

* Add following section to the Kafka custom resource (e.g. using `oc edit kafka my-cluster`)
```yaml
spec:
  kafka:
    # ...
  zookeeper:
    # ...
  entityOperator:
    # ...
  invalid:
    someKey: someValue
```
* Check the CO logs to see the warnings about the unknown fields
  * `oc logs $(oc get pod -l name=strimzi-cluster-operator -o=jsonpath='{.items[0].metadata.name}')`
* When finished, delete the cluster
  * `oc delete kafka my-cluster`

## Persistent Storage improvements

* Go to the `storage` directory
  * `cd storage`

### Adding and removing JBOD volumes

* Deploy a Kafka cluster which will be using JBOD storage
  * `oc apply -f kafka-jbod.yaml`
* Wait for the cluster to be deployed
* Add more volumes to the brokers
  * Either using `oc apply -f kafka-jbod-2-volumes.yaml`
  * Or using `oc edit` by adding following section to the `kafka.storage` section
```yaml
      - id: 1
        type: persistent-claim
        size: 10Gi
        deleteClaim: true
```
* Wait for the update to finish

_Optionally_

* Try to remove the volume again and see how it is removed

### Volume Resizing

_This step has to be executed only in environments which support storage resizing (i.e. not on Minishift or Minikube). You can try it for example on OCP4 in AWS._

* Make sure your cluster supports volume resizing
* Use `oc edit kafka my-cluster` and change the size of one of the volumes (e.g. from `10Gi` to `20Gi`)
* Watch as the PVs / PVCs get resized and wait for the 

## HTTP Bridge

* Go to the `http-bridge` directory
  * `cd http-bridge`
* Create a topic `my-topic`
  * `oc apply -f topic.yaml`
* Deploy the HTTP Bridge and wait for it to be ready
  * `oc apply -f http-bridge.yaml`
* Expose the bridge using OpenShift Route to allow it being used from our local computers
  * `oc apply -f http-route.yaml`
  * Get the address of the route `export BRIDGE="$(oc get routes my-bridge -o jsonpath='{.status.ingress[0].host}')"`

### Sending messages

* Send messages using a simple `POST` call
  * Notice the `content-type` header which is important!
```sh
curl -X POST $BRIDGE/topics/my-topic \
  -H 'content-type: application/vnd.kafka.json.v2+json' \
  -d '{"records":[{"key":"message-key","value":"message-value"}]}'
```

### Receiving messages

* First, we need to create a consumer
```sh
curl -X POST $BRIDGE/consumers/my-group \
  -H 'content-type: application/vnd.kafka.v2+json' \
  -d '{
    "name": "consumer1",
    "format": "json",
    "auto.offset.reset": "earliest",
    "enable.auto.commit": "false",
    "fetch.min.bytes": "512",
    "consumer.request.timeout.ms": "30000"
  }'
```

* Then we need to subscribe the consumer to the topics it should receive from
```sh
curl -X POST $BRIDGE/consumers/my-group/instances/consumer1/subscription \
  -H 'content-type: application/vnd.kafka.v2+json' \
  -d '{"topics": ["my-topic"]}'
```

* And then we can consume the messages (can be called repeatedly - e.g. in a loop)
```sh
curl -X GET $BRIDGE/consumers/my-group/instances/consumer1/records \
  -H 'accept: application/vnd.kafka.json.v2+json'
```

* At the end we should close the consumer
```sh
curl -X DELETE $BRIDGE/consumers/my-group/instances/consumer1
```

## Debezium

* Go to the `debezium` directory
  * `cd debezium`
* Deploy the Address Book application
  * `oc apply -f address-book.yaml`
  * This YAML deploys a MySQL database, and a simple application using it as a address book
  * Check it in OpenShift, make sure it works and you are able to add / edit / remove addresses
* Deploy Kafka Connect
  * `oc apply -f kafka-connect.yaml`
* Get the address of the Kafka Connect REST interface
  * `export CONNECT="$(oc get routes my-connect-cluster -o jsonpath='{.status.ingress[0].host}')"`

### Create the connector

* The connector can be created using the `debezium-connector.json` file
  * POST it to the Kafka Connect REST interface
```sh
curl -X POST $CONNECT/connectors \
  -H "Content-Type: application/json" \
  --data "@debezium-connector.json"
```
* Check that status of the connector
```sh
curl $CONNECT/connectors/debezium-connector/status | jq
```

### Debezium messages

* We will use the HTTP Bridge deployed in previous section to get the Debezium messages
* Create a consumer
```sh
curl -X POST $BRIDGE/consumers/debezium-group \
  -H 'content-type: application/vnd.kafka.v2+json' \
  -d '{
    "name": "debezium-consumer",
    "format": "json",
    "auto.offset.reset": "earliest",
    "enable.auto.commit": "false",
    "fetch.min.bytes": "512",
    "consumer.request.timeout.ms": "30000"
  }'
```
* Subscribe to the Kafka topic where we get the messages for our MySQL Address book table
```sh
curl -X POST $BRIDGE/consumers/debezium-group/instances/debezium-consumer/subscription \
  -H 'content-type: application/vnd.kafka.v2+json' \
  -d '{"topics": ["dbserver1.inventory.customers"]}'
```
* Read the messages created by the initial state of the database (you might need to run it multiple time before you get all the messages)
```sh
curl -X GET $BRIDGE/consumers/debezium-group/instances/debezium-consumer/records \
  -H 'accept: application/vnd.kafka.json.v2+json' | jq
```
* Go to the address book UI and make some changes
* Observe the new Debezium messages by calling the `GET` again
```sh
curl -X GET $BRIDGE/consumers/debezium-group/instances/debezium-consumer/records \
  -H 'accept: application/vnd.kafka.json.v2+json' | jq
```
* You can also consume the messages using a regular Kafka client:
```sh
oc run kafka-consumer -ti --image=strimzi/kafka:0.12.0-kafka-2.2.1 --rm=true --restart=Never -- bin/kafka-console-consumer.sh --bootstrap-server my-cluster-kafka-bootstrap:9092 --topic dbserver1.inventory.customers --from-beginning --property print.key=true --property key.separator=" - "
```