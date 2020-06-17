# Lab 14 - Strimzi 0.18.0 / AMQ Streams 1.5.0

## Install cluster operator

* Create namespace/project `myproject` and set it as default.
* On Openshift 
  * `oc new-project myproject`
  * `oc project myproject`
* On Kubernetes
  * `kubectl create ns myproject`
  * `kubectl config set-context --current --namespace=myproject`

### On OCP4

* Install AMQ Streams 1.5 / Strimzi 0.18.0 from the Operator Hub to watch all namespaces

### On OCP 3.11 or on Kubernetes

* Install the operator:
  * `kubectl apply -f strimzi-0.18.0`

## Improved TLS Configuration

* Check the configuration of Kafka in the [`kafka-tls-settings.yaml`](./kafka-tls-settings.yaml)
  * Notice the settings in `.spec.kafka.config` which configures TLSv1.2 as the only allowed TLS/SSL protocol:
```yaml
      ssl.protocol: TLSv1.2
      ssl.enabled.protocols: TLSv1.2
```
* Deploy the Kafka cluster
  * `kubectl apply -f kafka-tls-settings.yaml`
  * Wait for it to be ready

* Check the configuration of Kafka Connect in the [`kafka-connect-tls-settings.yaml`](./kafka-connect-tls-settings.yaml)
  * Notice the settings in `.spec.config` which configures TLSv1.1 as the only allowed TLS/SSL protocol:
```yaml
      ssl.protocol: TLSv1.1
      ssl.enabled.protocols: TLSv1.1
```
* Deploy the Kafka Connect cluster
  * `kubectl apply -f kafka-connect-tls-settings.yaml`
* Because we configured Kafka brokers to use TLSv1.2 and Connect to use TLSv1.1, they should never connect and the Connect pod should be looping
* Edit the Connect CR (`kubectl edit kc my-connect-cluster`) and change the options to TLSv1.2
  * Watch as the operator applies the changes and gets the Connect deployment running

## Operator Metrics

* Install Prometheus and Grafana
  * `kubectl apply metrics/prometheus-operator.yaml`
  * `kubectl apply metrics/prometheus-server.yaml`
  * `kubectl apply metrics/grafana-operator.yaml`
  * `kubectl apply metrics/grafana-server.yaml`
* Open the Grafana using the OpenShift Route or using port-forward
  * Check the dashboard with operator metrics
  * Look at the different metrics
* Deploy the example clients with topics and users
  * `kubectl apply -f client-applications.yaml`
* Check how the new users and topics show up in the dashboard
  
## Cluster Balancing / Cruise Control

* Scale the Kafka broker to 3 nodes
  * Edit the Kafka CR with `kubectl edit kafka my-cluster` and set `.spec.kafka.replicas` to 3
  * Wait until the new nodes are started and ready
* Deploy Cruise Control
  * Edit the Kafka CR with `kubectl edit kafka my-cluster` and add `.spec.cruiseControl` section set to `{}`. E.g.
```yaml
apiVersion: kafka.strimzi.io/v1beta1
kind: Kafka
metadata:
  name: my-cluster
spec:
  kafka:
    # ...
  zookeeper:
    # ...
  entityOperator:
    # ...
  cruiseControl: {}
```

* Check the existing topics and notice how they are all on node 0, since they were created when we had only 1 broker
  * `kubectl exec -ti my-cluster-kafka-0 -c kafka -- bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe`
* Let's get a rebalance proposal from Cruise Control. Check the `KafkaRebalance` resource in [rebalance.yaml](./rebalance.yaml) before applying it and look at the goals.
  * `kubectl apply -f rebalance.yaml`
* Wait until the proposal status is `ProposalReady`
* Check the status of the `KafkaProposal` resource
  * Notice the information about the replicas to be moved etc.
* Approve the rebalance `kubectl annotate kafkarebalance my-rebalance strimzi.io/rebalance=approve`
* Wait until the proposal status is `Ready` which means that the rebalancing is finished
* Check the new topic distribution after the rebalance
  * `kubectl exec -ti my-cluster-kafka-0 -c kafka -- bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe`
