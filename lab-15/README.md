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

* Install the operator using Operator Hub
* Or install the operator from YAML files:
  * `kubectl apply -f amq-streams-1.6.0/`

### On Kubernetes

* Install the operator from [OperatorHub.io](https://operatorhub.io/operator/strimzi-kafka-operator)
* Or install the operator from YAML files:
  * `kubectl apply -f strimzi-0.20.0/`

## New Listener configuration

* Enter the `listeners` directory
  * `cd listeners`
* Deploy Kafka with an old listener configuration
  * `kubectl apply -f kafka-old-config.yaml`
  * In case you are on Kubernetes, you can use `loadbalancer` or `nodeport` listener
  * You can check how the Kafka resource is using the old configuration

* Update the configuration to use the new listeners
  * `kubectl apply -f kafka-new-configration.yaml` or edit it manually
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
  * `kubectl apply -f kafka-more-listeners.yaml`
  * Notice the changes to the YAML

```yaml
      - name: tls2
        port: 9095
        type: internal
        tls: true
        authentication:
          type: scram-sha-512
      - name: external
        port: 9096
        type: loadbalancer
        tls: true
        authentication:
          type: tls
```

## Dynamic logging changes




## Metrics



## Scale sub-resource




