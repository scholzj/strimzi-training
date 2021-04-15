# Lab 17 - Strimzi 0.21 + 0.22 / AMQ Streams 1.7.0: Part 2

## Install cluster operator

* Create namespaces / projects `myproject` and `myproject2` and set `myproject` as default.
* On OpenShift 
  * `oc new-project myproject`
  * `oc new-project myproject2`
  * `oc project myproject`
* On Kubernetes
  * `kubectl create ns myproject`
  * `kubectl create ns myproject2`
  * `kubectl config set-context --current --namespace=myproject`

### On OCP 4.6 or 4.7

* Install the operator using Operator Hub (as cluster-wide)
* Or install the operator from YAML files (pre-configured as cluster-wide already):
  * `kubectl create -f strimzi-0.22.1/`

### On Kubernetes

* Install the operator from [Operator Hub](https://operatorhub.io/operator/strimzi-kafka-operator) as cluster-wide
* Or install the operator from YAML files (pre-configured as cluster-wide already):
  * `kubectl create -f strimzi-0.22.1/`

## Kafka Connect Build

_This part is designed to run on OpenShift Container Platform. To run it on Kubernetes, you would need to specify your own container registry and provide the credentials._

* Deploy the Kafka cluster
  * _On Kubernetes, change the Kafka CR to use for example Load Balancers or Ingress instead of OCP Routes_
  * `kubectl apply -f kafka.yaml`
  * Wait until it is ready
* Deploy the Kafka Connect cluster
  * Check the `.spec.build` section and the connectors it adds
  * Check that the same YAML contains the `KafkaConnect` resource with the build but also the `KafkaConnector` resources using the plugins added by the build
  * `kubectl apply -f kafka-connect-build.yaml`
  * Watch as the build is started and only after it is finished Connect is deployed
* Check the Connect logs to verify that the plugins were added and connectors created
  * For example using `kubectl logs deployment/my-connect-connect -f`
  * For example using `kubectl get kctr -o yaml`

* _On your own:_
  * _Try to add more connector plugins_
  * _Reconfigure the build to push the images for example to Quay or other container registry_

## Offset Synchronization

* Deploy the second Kafka cluster in the `myproject2` namespace
  * _On Kubernetes, change the Kafka CR to use for example Load Balancers or Ingress instead of OCP Routes_
  * `kubectl apply -n myproject2 -f kafka.yaml`
  * Wait until it is ready
* Deploy Kafka Mirror Maker 2
  * `kubectl apply -n myproject2 -f kafka-mirror-maker-2.yaml`
  * Notice the enabled offset synchronization: `sync.group.offsets.enabled: "true"`
  * And the modified synchronization intervals to make the lab / demo easier
* Run consumer on the source cluster:
  * `kubectl run kafka-consumer -ti --image=quay.io/strimzi/kafka:0.22.1-kafka-2.7.0 --rm=true --restart=Never -- bin/kafka-console-consumer.sh --bootstrap-server my-cluster-kafka-bootstrap:9092 --topic timer-topic --group my-group`
  * Notice the last timestamp when you stop the consumer
* Check the offset for this consumer group on the source cluster
  * `kubectl run kafka-consumer-groups -ti --image=quay.io/strimzi/kafka:0.22.1-kafka-2.7.0 --rm=true --restart=Never -- bin/kafka-consumer-groups.sh --bootstrap-server my-cluster-kafka-bootstrap:9092 --group my-group --describe`
* Now check the offsets on the target cluster
  * `kubectl run kafka-consumer-groups -ti --image=quay.io/strimzi/kafka:0.22.1-kafka-2.7.0 --rm=true --restart=Never -- bin/kafka-consumer-groups.sh --bootstrap-server my-cluster-kafka-bootstrap.myproject2.svc:9092 --group my-group --describe`
  * See how the topic was automatically renamed in the offset
  * Compare the numbers
* Run consumer on the target cluster:
  * `kubectl run kafka-consumer -ti --image=quay.io/strimzi/kafka:0.22.1-kafka-2.7.0 --rm=true --restart=Never -- bin/kafka-console-consumer.sh --bootstrap-server my-cluster-kafka-bootstrap.myproject2.svc:9092 --topic my-source-cluster.timer-topic --group my-group`
  * Check that the consumer continues where it left on the source cluster

## Restarting annotation

* Check the connector in the [`missing-path-connector.yaml`](./missing-path-connector.yaml) file
  * The path where the connector should write the data does not exist and the connector should fail
  * Create the connector `kubectl apply -f missing-path-connector.yaml`
* Double-check that the connector really failed
  * `kubectl get kctr file-sink-connector -o yaml`
  * You should see the connector `RUNNING` but the task as `FAILED`
* Exec into the connect pod and create the directory
  * `kubectl exec deployment/my-connect-connect -ti -- mkdir -p /tmp/my-path`
* Check the status of the connector, but nothing should change since the connector doesn't restart automatically
* Annotate the connector to tell the operator to restart the failed task
  * `kubectl annotate KafkaConnector file-sink-connector strimzi.io/restart-task=0`
* Wait for the connector to be restarted and check that it is running now
  * `kubectl exec deployment/my-connect-connect -ti -- tail -f /tmp/my-path/test.sink.txt`

# Topics in Connector status

* Check the `.status.topics` section of the existing connectors
  * `kubectl get kctr camel-timer-connector -o jsonpath="{.status.topics}"`

## Directory Config Provider

* Create a Config Map with two files
  * [`log-level.properties`](./config-map/log-level.properties) is a properties containing the connector log level under the key `log.level`
  * [`log-level`](./config-map/log-level) is a flat-file containing the connector log level
  * `kubectl create configmap log-level-config --from-file=log-level.properties=config-map/log-level.properties --from-file=log-level=config-map/log-level`
* Configure Kafka Connect to mount this ConfigMap
  * Edit the Connect deployment `kubectl edit kc my-connect` and do the following changes:
    * Add `externalConfiguration` to mount the secret
    ```yaml
    spec:
      # ...
      externalConfiguration:
        volumes:
          - name: log-level
            configMap:
              name: log-level-config
    ```
    * Initialize the `FileConfigProvider` and `DirectoryConfigProvider`
    ```yaml
    spec:
      # ...
      config:
        config.providers: file,directory
        config.providers.file.class: org.apache.kafka.common.config.provider.FileConfigProvider
        config.providers.directory.class: org.apache.kafka.common.config.provider.DirectoryConfigProvider
    ```
  * Wait for Kafka Connect to roll
* Modify the `echo-sink-connector` to use the log level from the properties file using the `FileConfigProvider`
  * Check the [`connector-with-file-provider.yaml`](./connector-with-file-provider.yaml) file
  * And apply it `kubectl apply -f connector-with-file-provider.yaml`
  * Run `kubectl logs deployment/my-connect-connect -f` and check the log level of the messages changed to `WARN`
* Modify the `echo-sink-connector` to use the log level from the properties file using the `DirectoryConfigProvider`
  * Check the [`connector-with-directory-provider.yaml`](./connector-with-directory-provider.yaml) file
  * And apply it `kubectl apply -f connector-with-directory-provider.yaml`
  * Run `kubectl logs deployment/my-connect-connect -f` and check the log level of the messages changed to `ERROR`

## New Topic Operator topics

* Check the new topics used by the Topic Operator
  * `kubectl get kt`

* _On your own:_
  * _Check the configuration of these topics also with Kafka APIs (`bin/kafka-topics.sh`, Kafka Admin API or similar)_

## Rolling individual pods

* Annotate one of the Kafka or ZooKeeper pods
  * For example `kubectl annotate pod my-cluster-kafka-1 strimzi.io/manual-rolling-update=true`
  * Wait for the next reconciliation to roll the `my-cluster-kafka-1` pod

* _On your own:_
  * _Annotate multiple pods at the same time and saw how the operators rolls them one by one as with regular rolling update_

## Smaller Kafka improvements

* Check the status of the deployed Kafka cluster
  * Notice the `clusterId` in the `.status` section
  * You can also get it programmatically using `kubectl get kafka my-cluster -o jsonpath="{.status.clusterId}"`

* Try to disable the owner reference for the Kafka secrets
  * Edit the `Kafka` CR with `kubectl edit kafka my-cluster`
  * Add the following section:
  ```yaml
  spec:
    # ...
    clusterCa:
      generateSecretOwnerReference: false
    clientsCa:
      generateSecretOwnerReference: false
  ```
  * Check the CA secrets (both public and private key secrets) and verify the owner references are gone now

* Add additional labels or annotations to the Kafka cluster public key secret
  * Edit the `Kafka` CR with `kubectl edit kafka my-cluster`
  * Add the following section:
  ```yaml
  spec:
    kafka:
      template:
        clusterCaCert:
          metadata:
            labels:
              my-key: my-label
            annotations:
              my-key: my-anno
  ```
  * Check that the label and annotation are set on the `my-cluster-cluster-ca-cert` secret: `kubectl get secret my-cluster-cluster-ca-cert -o yaml`


## User Operator improvements

* Edit the Kafka CR and add to the `.spec.entityOperator.userOperator` section new field `secretPrefix`
  ```yaml
  # ...
  entityOperator:
    # ...
    userOperator:
      secretPrefix: kafka-
  ```
  * Wait for the User Operator pod to roll
* Check the `KafkaUser` CR in [`kafka-user.yaml`](./kafka-user.yaml)
  * Notice the `.spec.template` section
  * Create the user: `kubectl apply -f kafka-user.yaml`
* Check the created secret
  * Notice it is not named `my-user` but `kafka-my-user`
  * Notice the secret now has the `sasl.jaas.config` key with the JAAS configuration
  * Notice the labels and annotations from the `.spec.template` section