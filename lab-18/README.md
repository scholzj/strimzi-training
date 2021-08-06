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
  kubectl apply -f kafka.yaml`
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
    ```
            - name: STRIMZI_FEATURE_GATES
              value: +ControlPlaneListener
    ```
  * Or by editing the `Subscription` resource if installed through the Operator Hub
    ```
    TODO
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
      ```
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
    ```
        conditions:
          - lastTransitionTime: "2021-08-06T19:46:23.119Z"
            status: "True"
            type: Ready
    ```

## Kubernetes Configuration provider




## Load Balancer finalizers


