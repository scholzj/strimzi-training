# Lab 15 - Strimzi 0.19 + 0.20 / AMQ Streams 1.6.0

## Install cluster operator

* Create namespace/project `myproject` and set it as default.
* On Openshift 
  * `oc new-project myproject`
  * `oc project myproject`
* On Kubernetes
  * `kubectl create ns myproject`
  * `kubectl config set-context --current --namespace=myproject`

### On OCP4 and OCP 3.11 

* Install the operator using Operator Hub (as namespaces into the `myproject` namespace)
* Or install the operator from YAML files:
  * `kubectl apply -f amq-streams-1.6.0/`

### On Kubernetes

* Install the operator from [OperatorHub.io](https://operatorhub.io/operator/strimzi-kafka-operator)
* Or install the operator from YAML files:
  * `kubectl apply -f strimzi-0.20.0/`

## New Listener configuration

* Deploy Kafka with an old listener configuration
  * `kubectl apply -f listeners-old-config.yaml`
  * In case you are on Kubernetes, you can use `loadbalancer` or `nodeport` listener
  * You can check how the Kafka resource is using the old configuration

* Update the configuration to use the new listeners
  * `kubectl apply -f listeners-new-config.yaml` or edit it manually
  * There should be no rolling update!
  * Look at the updated YAML and notice the important parts
    * The `name` and `port` values are important for backwards compatibility
    * The `tls` flag is now mandatory
    * Notice it is configured as array

```yaml
      - name: plain
        port: 9092
        type: internal
        tls: false
        authentication:
          type: scram-sha-512
      - name: tls
        port: 9093
        type: internal
        tls: true
        authentication:
          type: tls
      - name: external
        port: 9094
        type: route
        tls: true
        authentication:
          type: tls
```

* _On your own:_
  * _See what happens when you change the names or port numbers for the existing `external` listener and how it affects the service names and addresses_
  * _See what happens when you change the names or port numbers for the existing `plain` or `tls` listener and how it affects the service configuration (port names)_

* Try to use some of the new features
  * Add second internal listener with TLS encryption and SCRAM-SHA-512 authentication
  * Add second listener using load balancers (if supported in your environment) or node ports
  * `kubectl apply -f listeners-more-listeners.yaml`
  * Notice the changes to the YAML

```yaml
      - name: tls2
        port: 9095
        type: internal
        tls: true
        authentication:
          type: scram-sha-512
      - name: external2
        port: 9096
        type: loadbalancer
        tls: true
        authentication:
          type: tls
```

## Dynamic logging changes

### Cluster Operator logging

* Check out the ConfigMap names `strimzi-cluster-operator` and its content
* Edit it and change the following line:
```
rootLogger.level = ${env:STRIMZI_LOG_LEVEL:-INFO}
```

* to:
```
rootLogger.level = ${env:STRIMZI_LOG_LEVEL:-DEBUG}
```

* Open the operator log and wait for the log level to change:
  * `kubectl logs deployment/strimzi-cluster-operator -f`
  * Wait for the DEBUG messages to show up
  * Notice the pod did not need to restart

_(If you installed the operator using OperatorHub, the config map and pod will be in another namespace (e.g. `openshift-operators`) and might have different name.)_

### Kafka brokers

* We can also dynamically change log levels for Kafka
  * Edit the Kafka resource
  * Set the log level for `` to ``

```yaml
    logging:
      type: inline
      loggers:
        log4j.logger.org.apache.zookeeper: "DEBUG"
```

* _On your own:_
  * _Try the same for The Entity Operators, Kafka Connect, HTTP Bridge or for Mirror Maker_

## Metrics

* On OpenShift, install the Prometheus operators from Operator Hub and deploy Grafana and Prometheus
  * `kubectl apply -f metrics/prometheus-server.yaml`
  * `kubectl apply -f metrics/grafana-operator.yaml`
  * `kubectl apply -f metrics/grafana-server.yaml`
* On Kubernetes, install the Grafana and Prometheus operators from YAMLs and deploy Grafana and Prometheus
  * `kubectl apply -f metrics/prometheus-operator.yaml`
  * `kubectl apply -f metrics/prometheus-server.yaml`
  * `kubectl apply -f metrics/grafana-operator.yaml`
  * `kubectl apply -f metrics/grafana-server.yaml`
* You can access the Prometheus and Grafana UIs using the OpenShift Routes or using port-forward

* Deploy the Kafka clients to generate some load
  * `kubectl apply -f clients.yaml`

### Resource state metrics

* Exec into the Cluster Operator pod and run the following command
  * `curl localhost:8080/metrics | grep strimzi_resource_state`
  * Check the outcome showing the state of the custom resources: 1 => Ready, 0 => Not Ready

```
sh-4.2$ curl localhost:8080/metrics | grep strimzi_resource_state
# HELP strimzi_resource_state Current state of the resource: 1 ready, 0 fail
# TYPE strimzi_resource_state gauge
strimzi_resource_state{kind="Kafka",name="my-cluster",resource_namespace="myproject",} 1.0
```

### Bridge metrics

* Deploy bridge with metrics enabled:
  * `kubectl apply -f metrics-bridge.yaml`
* Deploy the example HTTP clients
  * `kubectl apply -f metrics-bridge-clients.yaml`
* Open the Bridge dashboard in Grafana and check the metrics

### Cruise Control metrics

* Open the Cruise Control dashboard in Grafana and check the metrics
* Scale the Kafka cluster to 5 replicas
* Trigger a rebalance
  * `kubectl apply -f rebalance.yaml`
* Wait until the rebalance proposal is ready
* Annotate it to execute the rebalance
  * `kubectl annotate kafkarebalance my-rebalance strimzi.io/rebalance=approve`
* Watch in the dashboard the progress

## Scale sub-resource

* Scale the already deployed Kafka bridge to more replicas
  * `kubectl scale kafkabridge my-bridge --replicas 3`
  * Check how the number of replicas in the `my-bridge` resource changed to 3
