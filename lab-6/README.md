# Strimzi Training - Lab 6

Lab 6 is using Strimzi 0.6.0. It takes you through different aspects of monitoring Strimzi.

* Checkout this repository which will be used during the lab:
  * `git clone https://github.com/scholzj/strimzi-training.git`
* Go to the `lab-6` directory
  * `cd lab-6`
* Start you OpenShift cluster
  * You should use OpenShift 3.9 or higher
  * Run `minishift start` or `oc cluster up`
* Login as cluster administrator
  * `oc login -u system:admin`
* Install the Cluster Operator
  * `oc apply -f install/`
* Install the Kafka cluster
  * `oc apply -f kafka.yaml`

## Cluster Operator Logging

* Edit the Cluster Operator deployment
  * `oc edit deployment strimzi-cluster-operator`
  * And change the log level:

```yaml
        env:
        - name: STRIMZI_LOG_LEVEL
          value: DEBUG
```

* Check the new information which it contains with the DEBUG log level

## Logging in other components

* Deploy Kafka Connect `oc apply -f connect.yaml`
  * Watch Kafka Connect log to see it fail because of missing ACL rights
* Change log levels for Kafka brokers
* Edit the Kafka resource using `oc edit kafka my-cluster`
  * Change the Kafka log levels. For example

```yaml
apiVersion: {KafkaApiVersion}
kind: Kafka
spec:
  kafka:
    # ...
    logging:
      type: inline
      loggers:
        log4j.logger.kafka.authorizer.logger: INFO
    # ...
```

* Wait for the rolling update of Kafka brokers to finish
* Check Kafka logs to see authorization errors
  * Try to find out which access rights are missing and add them
* _On your own: Try to configure log levels also in other componenets_

## Configure logging using config map

* Use the file `log4j.properties` to create a new config map
  * `oc create configmap kafka-broker-logging --from-file log4j.properties`
* Edit the Kafka resource using `oc edit kafka my-cluster`
  * Change the Kafka log levels. For example

```yaml
apiVersion: {KafkaApiVersion}
kind: Kafka
spec:
  kafka:
    # ...
    logging:
      type: external
      name: kafka-broker-logging
    # ...
```

* Wait for the rolling update of Kafka brokers to finish
* Check that the logging configuration has been updated
* _On your own: Try to configure log levels using config map also in other componenets_