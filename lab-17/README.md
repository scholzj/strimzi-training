# Lab 17 - Strimzi 0.21 + 0.22 / AMQ Streams 1.7.0: Part 2

## Install cluster operator

* Create namespace/project `myproject` and set it as default.
* On OpenShift 
  * `oc new-project myproject`
  * `oc project myproject`
* On Kubernetes
  * `kubectl create ns myproject`
  * `kubectl config set-context --current --namespace=myproject`

### On OCP 4.6 or 4.7

* Install the operator using Operator Hub (as name-spaced into the `myproject` namespace)
* Or install the operator from YAML files:
  * `kubectl create -f strimzi-0.22.1/`

### On Kubernetes

* Install the operator from [Operator Hub](https://operatorhub.io/operator/strimzi-kafka-operator)
* Or install the operator from YAML files:
  * `kubectl create -f strimzi-0.22.1/`

## Offset Synchronization

* TODO

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

## Restarting annotation

* Check the connector in the [`missing-path-connector.yaml`](./missing-path-connector.yaml) file
  * The path where the connector should write the data does not exist and the connector should fail
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
