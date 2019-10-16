# Strimzi Training - Lab 12

Lab 12 is using Strimzi 0.14.0. It takes you through the changes in Strimzi 0.14.0 (AMQ Streams 1.3.0).

* Checkout this repository which will be used during the lab:
  * `git clone https://github.com/scholzj/strimzi-training.git`
* Go to the `lab-12` directory
  * `cd lab-12`
* Start you OpenShift or Kubernetes cluster
  * **You should use OpenShift 3.11 or higher or Kubernetes 1.11 or higher**
  * Run `minishift start`, `oc cluster up`, `openshift-installer create cluster` or `minikube start`
* Login as cluster administrator
* If you are not in a project `myproject`, create it and set it as active
  * `oc new-project myproject`
  * `oc project myproject`

## Install Strimzi 0.14.0

Strimzi 0.14.0 is used as a basis for AMQ Streams 1.3.0 and all features are identical.

* Install Strimzi 0.14.0
  * `oc apply -f strimzi-0.14.0/`

## Custom Resource status sub-resources

* Go to the `cr-status` directory
  * `cd cr-status`
* Deploy the source Kafka cluster and check the Status
  * `oc apply -f source-kafka.yaml`
  * Wait for the cluster to be `Ready`
    * `oc wait kafka/source-cluster --for=condition=Ready --timeout=300s`
  * After it is ready, check the status
    * `oc get kafka source-cluster -o yaml`
    * Check the status section: conditions, listeners, and observed generation
* Deploy the HTTP Bridge and check the Status
  * `oc apply -f http-proxy.yaml`
  * Wait for the bridge to be `Ready`
    * `oc wait kafkabridge/http-proxy --for=condition=Ready --timeout=300s`
  * After it is ready, check the status
    * `oc get kafkabridge http-proxy -o yaml`
    * Check the status section: conditions and URL
    * _Note: Notice the bug in the URL in the status :-(_
* Deploy the Mirror Maker and check the Status
  * `oc apply -f mirror-maker.yaml`
  * We are missing the target Kafka cluster, so the deployment fill fail at first.
  * Wait for the Mirror Maker to be `NotReady` (it should take around 5 minutes with he default settings)
    * `oc wait kafkamirrormaker/mirror-maker --for=condition=NotReady --timeout=300s`
  * After it is ready, check the status
    * `oc get kafkamirrormaker mirror-maker -o yaml`
    * Notice the `NotReady` condition and the reason for it
* Deploy the target Kafka cluster and check the Status
  * `oc apply -f target-kafka.yaml`
  * Wait for the cluster to be `Ready`
    * `oc wait kafka/target-cluster --for=condition=Ready --timeout=300s`
  * After it is ready, check the status
    * `oc get kafka target-cluster -o yaml`
    * Check the status section: conditions, addresses, and observerGeneration
* Deploy Kafka Connect and check the Status
  * `oc apply -f connect.yaml`
  * Wait for the Connect cluster to be `Ready`
    * `oc wait kafkaconnect/connect --for=condition=Ready --timeout=300s`
  * After it is ready, check the status
    * `oc get kafkaconnect connect -o yaml`
    * Check the status section: conditions, URL for the REST API and the observed generation
* Deploy the producer and consumer
  * `oc apply -f clients.yaml`
  * Check the status of the `KAfkaUser` and `KafkaTopic` resources:
    * `oc get kafkatopic my-topic -o yaml`
    * `oc get kafkauser hello-world-producer -o yaml`
    * `oc get kafkauser hello-world-consumer -o yaml`
* Now with the target Kafka cluster running, check the Kafka Mirror Maker again
  * `oc get kafkamirrormaker mirror-maker -o yaml`
  * It should transition to `Ready` state and the status should change (the exponential back-off might mean that it takes it longer to catch-up - you can speed it up by deleting the pod manually)
* _On your own: Try to trigger different errors by modifying the different custom resources and see how the status can change._
* Once you are finished, you can delete everything
  * `oc delete -f ./`

## Kafka Exporter

* Go to the `kafka-exporter` directory
  * `cd kafka-exporter`
* Deploy Prometheus, Grafana and Grafana dashboards
  * `oc apply -f prometheus-grafana/`
* Deploy a Kafka cluster with the Kafka Exporter enabled
  * `oc apply -f kafka.yaml`
  * And wait for it to get ready
    * `oc wait kafka/my-cluster --for=condition=Ready --timeout=300s`
* Deploy the clients
  * `oc apply -f clients.yaml`
* Go to Grafana
  * On OpenShift it should be exposed using a route
  * Login (username: `admin`, password: `123456`)
  * Check the Kafka and Kafka Exporter dashboards
  * You should see minimal consumer lag for the `hello-world-consumer` (`hello-world-streams` is based on Streams API and will show some lag)
  * Scale down the consumer to 0 replicas (stop it)
    * `oc scale deployment hello-world-consumer --replicas=0`
  * Check in the Kafka Exporter dashboard how the consumer lag starts increasing
  * Scale the consumer back to 1 (or more) replicas
    * `oc scale deployment hello-world-consumer --replicas=1`
  * Check in the Kafka Exporter dashboard how the consumer lag decreases again
* _On your own: Check the other dashboards, check the offsets etc._
* Once you are finished, you can delete everything
  * `oc delete -f ./`
  * `oc delete -f ./prometheus-grafana`

