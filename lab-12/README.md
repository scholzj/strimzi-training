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

## OAuth

* Go to the `oauth` directory
  * `cd oauth`
* Deploy Keycloak / RH-SSO (the script provided below works only on OpenShift)
  * `./install-keycloak.sh
  * Save the username and password provided by the installation script
  * Wait until the Keycloak deployment is finished
* Log in to the Keycloak Administration console (use the route to access it)
  * Hover with mouse over the left top corner title _Master_ and click _Add Realm_
  * And import the Realm from the attached file [`keycloak-realm.json`]()./lab-12/oauth/keycloak-realm.json)
  * Check the different clients in the _Clients_ section
* Deploy Kafka broker with OAuth authentication
  * `oc apply -f kafka.yaml`
  * And wait for it to get ready
    * `oc wait kafka/my-cluster --for=condition=Ready --timeout=300s`
* Deploy the clients
  * `oc apply -f clients.yaml`
  * Check the YAML file to see:
    * How it passes the Client Secret using a Kubernetes secret
    * How it uses the `KafkaUser` resource to manage the ACLs for the OAuth user
  * Check the source code to see how the OAuth is configured in the clients
    * All source codes are in [https://github.com/strimzi/client-examples](https://github.com/strimzi/client-examples)
    * Check the file [KafkaProducerConfig.java](https://github.com/strimzi/client-examples/blob/09b49a10dfb9cd472d0691cd1a6cf54eefe57ac3/hello-world-producer/src/main/java/KafkaProducerConfig.java#L86) to see in detail how is it configured in the producer
  * Check the logs to see that the clients work
* OAuth is also supported in all your components - as an example let's try it with HTTP Bridge
  * `oc apply -f bridge.yaml`
  * Wait for the bridge to be `Ready`
    * `oc wait kafkabridge/my-bridge --for=condition=Ready --timeout=300s`
  * You can use the script `receive-messages-with-http.sh` to see if it works
    * `./receive-messages-with-http.sh`
* _On your own: Try to deploy other components with OAuth or create your own clients for your own consumers & producers._
* Once you are finished, you can delete everything
  * `oc delete -f ./`

## Environment Variables

_Note: I do not think changing the timezone of your logs is a good idea, you should use UTC time. But it serves well to demonstrate the environment variables. :-o_

* Go to the `environment-variables` directory
  * `environment-variables`
* Deploy Kafka cluster with default configuration
  * `oc apply -f kafka.yaml`
  * And wait for it to get ready
    * `oc wait kafka/my-cluster --for=condition=Ready --timeout=300s`
* Check the logs of all pods
  * All timestamps should be in UTC time
  * e.g. `oc logs my-cluster-kafka-0 -c kafka`
* Deploy Kafka cluster with Europe / Prague timezone
  * Check the `kafka-prague-timezone.yaml` file and notices how the `TZ` environment variable is configured for every container
  * `oc apply -f kafka-prague-timezone.yaml`
  * Wait for the rolling update to be finished
* Check the logs of all pods
  * All timestamps should be in UTC time
  * e.g. `oc logs my-cluster-kafka-0 -c kafka`
* Check that the environment variable is indeed set
  * `oc exec my-cluster-kafka-0 -c kafka -- env | grep TZ`
  * You should see `TZ=Europe/Prague`
* _On your own: Try to set environment variable conflicting with some Strimzi / AMQ Streams variable and see that it is ignored. E.g. `ZOOKEEPER_NODE_COUNT` for Zookeeper container._
* Once you are finished, you can delete everything
  * `oc delete -f ./`

## Tracing

* Go to the `tracing` directory
  * `tracing`
* Install the Jaeger operator
  * On OCP4, you can use the OperatorHub
    * Go to the OCP Console, in the menu select Operators / Operator Hub, find Jaeger and install it
  * On Minishift or Minikube, you can use [OperatorHub.io](https://operatorhub.io/operator/jaeger)
  * Create a Jaeger instance int he `myproject` namespace
    * `oc apply -f jaeger.yaml`
    * Create a TLS Passthrough Route to the `my-jaeger-query` service to be able to access it
      * Alternatively you can also use port-forward for example.
* Deploy the source Kafka cluster
  * `oc apply -f source-kafka.yaml`
  * Wait for the cluster to be `Ready`
    * `oc wait kafka/source-cluster --for=condition=Ready --timeout=300s`
* Deploy the target Kafka cluster
  * `oc apply -f target-kafka.yaml`
  * Wait for the cluster to be `Ready`
    * `oc wait kafka/target-cluster --for=condition=Ready --timeout=300s`
* Deploy the Mirror Maker
  * `oc apply -f mirror-maker.yaml`
  * Check the YAML file you are deploying - notice the tracing variables and the the enabled Jaeger tracing
  * Wait for the Mirror Maker to be `Ready`
    * `oc wait kafkamirrormaker/mirror-maker --for=condition=Ready --timeout=300s`
* Deploy the clients with tracing
  * `oc apply -f clients.yaml`
  * Check the YAML for the environment variables configuring the Jaeger tracer
  * Check the source codes
    * All source codes are in [https://github.com/strimzi/client-examples](https://github.com/strimzi/client-examples)
    * Check the file [KafkaProducerExample.java](https://github.com/strimzi/client-examples/blob/09b49a10dfb9cd472d0691cd1a6cf54eefe57ac3/hello-world-producer/src/main/java/KafkaProducerExample.java#L20) to see initialization of the Jaeger Client / Tracer and configuration of the interceptor.
* Go to the Jaeger UI
  * List the traces
    * You should see traces with 4 spans: Producer -> MirrorMaker (in) -> Mirror Maker (out) -> Consumer
    * Check the latencies and the trace details
* _On your own: Try tracing with Kafka Connect or Kafka Streams API_
* Once you are finished, you can delete everything
  * `oc delete -f ./`