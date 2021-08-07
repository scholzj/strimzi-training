# Lab 18 - Strimzi 0.23 + 0.24 / AMQ Streams 1.8.0:

## Install cluster operator

* Create namespaces / projects `myproject` and it as default.
* On OpenShift 
  ```
  oc new-project myproject
  ```
* On Kubernetes
  ```
  kubectl create ns myproject
  kubectl config set-context --current --namespace=myproject
  ```

### On OCP 4.6, 4.7 or 4.8

* Install the operator using Operator Hub as cluster-wide (either Strimzi 0.24.0 or AMQ Streams 1.8.0 if already available)
* Or install the operator from YAML files (pre-configured as cluster-wide already):
  ```
  kubectl create -f strimzi-0.24.0/
  ```

### On Kubernetes

* Install the Strimzi 0.24.0 operator from [Operator Hub](https://operatorhub.io/operator/strimzi-kafka-operator) as cluster-wide
* Or install the operator from YAML files (pre-configured as cluster-wide already):
  ```
  kubectl create -f strimzi-0.24.0/
  ```

## Control Plane Listener

* In Strimzi 0.24 / AMQ Streams 1.8.0, the control plane listener feature gate is disabled by default
  * But the listener is always present
* Deploy the Kafka cluster:
  ```
  kubectl apply -f kafka.yaml
  ```
  * _On Kubernetes, change the YAML to use load balancers, node ports or Ingress instead of Routes_
* Check the listener for port 9090 and notice that it is configured in the configuration but not used
  ```
  kubectl exec -ti -c kafka my-cluster-kafka-0 -- cat /tmp/strimzi.properties | grep listeners
  kubectl exec -ti -c kafka my-cluster-kafka-0 -- cat /tmp/strimzi.properties | grep control
  ```
  * Having the listener enabled by default even when not used is important for upgrades to future versions
* Enable the `ControlPlaneListener` feature gate:
  * Edit the Cluster Operator (CO) deployment and set the environment variable `FEATURE_GATES` to `+ControlPlaneListener`
  * You can do it either by editing the CO deployment (`kubectl edit deployment strimzi-cluster-operator`) if installed from the YAMLs directly and changing the environment variable:
    ```yaml
            - name: STRIMZI_FEATURE_GATES
              value: +ControlPlaneListener
    ```
  * Or by editing the `Subscription` resource if installed through the Operator Hub
    * You can edit it either from CLI
      ```
      kubectl edit subscription strimzi-kafka-operator -n openshift-operators
      ```
    * Or from the OperatorHub UI (Go to the Strimzi operator / Subscription / Edit Subscription)
    * Add following YAML to the `Subscription` resource:
      ```yaml
      spec:
        # ...
        config:
          env:
          - name: STRIMZI_FEATURE_GATES
            value: +ControlPlaneListener
      ```
* The CO will restart and afterwards it will roll the Kafka brokers
  * After the rolling update is finished, you can check that the control plane listener is now used
    ```
    kubectl exec -ti -c kafka my-cluster-kafka-0 -- cat /tmp/strimzi.properties | grep control
    ```

## Pausing reconciliation

* Use the Kafka cluster running from the previous step
* Check the `Kafka` custom state and its condition:
  
* Annotate the `Kafka` custom resource to pause its reconciliation:
  ```
  kubectl annotate kafka my-cluster strimzi.io/pause-reconciliation="true"
  ```
  * Wait until the operator confirms that the reconciliation is paused.
    This is important => if the operator is currently reconciling the resource, it will pause it only after the ongoing reconciliation is finished.
    * You can use `kubectl wait` to wait for it:
      ```
      kubectl wait kafka/my-cluster --for=condition=ReconciliationPaused --timeout=300s
      ```
    * Or check it manually - you should see something like this in the resource status:
      ```yaml
        status:
          conditions:
          - lastTransitionTime: "2021-08-06T19:39:59.196347Z"
            status: "True"
            type: ReconciliationPaused
          observedGeneration: 2
      ```
* Once the reconciliation is paused, we can manage the cluster manually
  * As an example, we can scale down the Kafka cluster to shutdown the brokers to for example backup the storage:
    ```
    kubectl scale statefulset my-cluster-kafka --replicas=0
    ```
  * You should see the Kafka pods being stopped.
    You can wait few minutes for the operator to double check that it will not revert the change
* Once you are finished with the manual maintenance, you can unpause the reconciliation:
  ```
  kubectl annotate kafka my-cluster strimzi.io/pause-reconciliation-
  ```
  * Once unpaused, the operator should restart the Kafka brokers again
  * Once the reconciliation is finished, you can also see that the status is `Ready` again:
    ```yaml
        conditions:
          - lastTransitionTime: "2021-08-06T19:46:23.119Z"
            status: "True"
            type: Ready
    ```

## Kubernetes Configuration provider

### In Kafka Connect

* Deploy Kafka Connect using the [`kafka-connect.yaml`](./kafka-connect.yaml) file
  ```
  kubectl apply -f kafka-connect.yaml
  ```
  * It deploys Connect with Apache Camel Timer connector and Echo-Sink connector
  * It also creates topic named `timer-topic` and instance of the Apache Camel Timer connector which will send message every second
  * Also notice that it initializes the [Kubernetes Configuration Provider for Apache Kafka](https://github.com/strimzi/kafka-kubernetes-config-provider)
    ```yaml
    config:
      # ...
      config.providers: secrets,configmaps
      config.providers.secrets.class: io.strimzi.kafka.KubernetesSecretConfigProvider
      config.providers.configmaps.class: io.strimzi.kafka.KubernetesConfigMapConfigProvider
    ```
* Once the Connect cluster is running, we will create additional connector configured from Secret
  * Check the [`connector.yaml`](./connector.yaml) file and notice
    * It creates a `Secret` with the connector configuration
    * It creates `Role` and `RoleBinding` to allow the `Secret` to be read
      * Notice the `ServiceAccount` used in the `RoleBinding` is the one used by Kafka Connect already
    * It creates the Echo Sink connector instance which loads the log level from the Secret:
      ```yaml
      config:
        # ...
        level: ${secrets:myproject/echo-sink-configuration:logLevel}
      ```
    * Create the connector by applying the file
      ```
      kubectl apply -f connector.yaml
      ```
    * Check how the connector gets created and starts printing the messages into the connect log:
      ```
      kubectl logs deployment/my-connect-connect -f
      ```
    * Also, notice that the Connect deployment was not restarted and other connectors were not disrupted
* _Using the Kubernetes Configuration Provider to load the log level makes it easy to demo the functionality._
  _But the main value is of course when using it to load passwords, certificates or API keys._

### In Kafka clients

* Kubernetes Configuration Provider can be also used in clients
* First, lets create a user `my-user` and topic `my-topic` the clients will use:
  ```
  kubectl apply -f kafka-topic.yaml
  kubectl apply -f kafka-user.yaml
  ```
  * Notice that the user uses TLS authentication
* In your IDE, open the [clients project](./clients/)
  * Check the `Consumer` and `Producer` classes
  * Notice how they both initialize the provider:
    ```java
    // Initialize the config provider
    props.put("config.providers", "secrets");
    props.put("config.providers.secrets.class", "io.strimzi.kafka.KubernetesSecretConfigProvider");
    ```
  * And how they use it to configure the Kafka clients using the providers and load the TLS certificates directly from Kubernetes / OpenShift using your login to the cluster.
    ```java
    props.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PEM");
    props.put(SslConfigs.SSL_KEYSTORE_CERTIFICATE_CHAIN_CONFIG, "${secrets:myproject/my-user:user.crt}");
    props.put(SslConfigs.SSL_KEYSTORE_KEY_CONFIG, "${secrets:myproject/my-user:user.key}");
    props.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PEM");
    props.put(SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG, "${secrets:myproject/my-cluster-cluster-ca-cert:ca.crt}");
    ```
  * Update the bootstrap server to match your Kafka cluster address
  * Run the `Producer` and `Consumer` to verify they work

## Load Balancer finalizers

_(Requires a Kubernetes or OpenShift cluster with support for load balancers and for the `service.kubernetes.io/load-balancer-cleanup` annotation. This will typically be the major public clouds.)_

* Edit the Kafka cluster with `kubectl edit kafka my-cluster` and change the external listener to use load balancers and set the finalizers:
  ```yaml
  - name: external
    port: 9094
    type: loadbalancer
    tls: true
    authentication:
      type: tls
    configuration:
      finalizers:
        - service.kubernetes.io/load-balancer-cleanup
  ```
  * Wait for the rolling update to of Kafka cluster to complete
* Check the load balancer type services with `kubectl get service -o yaml` and verify that the finalizers are set
* Delete the Kafka cluster with `kubectl delete kafka my-cluster`
  * What how the services will delete slowly only when the load balancer is actually deleted in the underlying infrastructure
    ```
    kubectl get service -w
    ```
