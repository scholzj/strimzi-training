# Strimzi Training - Lab 9

Lab 9 demonstrates upgrading the Strimzi Cluster Operator from version 0.8.1 to version 0.11.1 and then upgrading the Kafka cluster it manages from Kafka 2.0.0 to Kafka 2.1.0.

* Checkout this repository which will be used during the lab:
  * `git clone https://github.com/scholzj/strimzi-training.git`
* Go to the `lab-9` directory
  * `cd lab-9`
  * Start your OpenShift cluster
  * You should use OpenShift 3.9 or higher
  * Run `minishift start` or `oc cluster up`
* Login as cluster administrator
  * `oc login -u system:admin`
* Install the Strimzi 0.8.1 Cluster Operator
  * `oc apply -f install/strimzi-0.8.1-co`
* Have the Strimzi 0.8.1 Cluster Operator create a Kafka 2.0.0 cluster
  * `oc apply -f kafka-2.0.0.yaml`

## Prepare to upgrade to Strimzi 0.11.1

Before we can upgrade the Cluster Operator we need to modify the `Kafka` CR slightly. This is so that:

1. when the 0.11.1 operator sees it, it will know that the running Kafka cluster is using 2.0.0; we add the `version` property to do this.
2. we don't need another rolling update when the time comes to upgrade upgrade the Kafka cluster from 2.0.0 to 2.1.0; we'll add the `log.message.format.version` Kafka config.

To do this:

* `oc edit kafka my-cluster`
  * Add a `version` property to `Kafka.spec.kafka`, setting its value to `"2.0.0"`.
  * Add a `config` property to `Kafka.spec.kafka` (since it doesn't already exist), and add the config `log.message.format.version` with the value `"2.1"`.

  In other words your `Kafka` resource should look something like this:

  ```
  kind: Kafka
    # ...
    spec:
      # ...
      kafka:
        # ...
        version: "2.0.0"
        config: 
          log.message.format.version: "2.0"
  ```

  Note the quoting around `"2.0"`, this is necessary because YAML will interpret unquoted `2.0` as a floating point number.

  * Save and exit `$EDITOR`.
* Let the Cluster Operator perform the rolling update.
  * `oc logs -f $(oc get po -o name | grep cluster-operator)` to follow the logs and/or `oc get po -w` to watch pod state transitions.

## Upgrade the Cluster Operator to Strimzi 0.11.1

We can now upgrade the Cluster Operator to Strimzi 0.11.1.

In a real installation scenario you would at this point verify that the configuration of running the `strimzi-cluster-operator` Deployment was compatible with the one about to be applied. In other words, if the running Cluster Operator is not running with the default configuration then however it has been changed needs to be reflected in the Deployment you are about to apply.

Similarly, if the RBAC (Role-Based Access Control) resources for the existing installation were modified from the 0.8.1 defaults then the RBAC resources being applied will also need to be changed.

* Now you can update the `Deployment`
  * `oc apply -f install/strimzi-0.11.1-co`

The cluster operator will perform a rolling upgrade of the Zookeeper, Kafka and Entity Operator pods. This is so that the Kafka pods are using Strimzi 0.11.1 images (though note that those images still contain Kafka 2.0.0 binaries).

* Wait for the rolling upgrade of the Kafka pods to finish. 
  * `oc logs -f $(oc get po -o name | grep cluster-operator)` to follow the logs and/or `oc get po -w` to watch pod state transitions.

We now have Strimzi 0.11.1 Cluster Operator running a Kafka 2.0.0 cluster.
You can verify this by by running

* `oc get po my-cluster-kafka-0 -o jsonpath='{.spec.containers[0].image}'`

The version of Kafka in the image is included in the image tag: `strimzi/kafka:0.11.1-kafka-2.0.0` tells you that this is a Strimzi 0.11.1 image with Kafka 2.0.0.

## Upgrade the Kafka cluster to Kafka 2.1.0

The final stage is to upgrade the Kafka cluster to 2.1.0.

* `oc edit kafka my-cluster`
  * Change the `version: 2.0.0` to `version: 2.1.0`.
  * Save and exit `$EDITOR`.
* Wait for **two** rolling restarts of the Kafka pods to finish.
  * `oc logs -f $(oc get po -o name | grep cluster-operator)` to follow the logs and/or `oc get po -w` to watch pod state transitions.

The first rolling restart ensures each pod is using the 2.1.0 binaries. 

* _Optional:_ Observe that the Kafka pods are now using a Kafka 2.1.0 image 
  * `oc get po my-cluster-kafka-0 -o jsonpath='{.spec.containers[0].image}'`
  * This should show `strimzi/kafka:0.11.1-kafka-2.1.0`.
  
The second rolling restart reconfigures the brokers to allow them to send messages to other brokers using the 2.1.0 interbroker protocol.

At this point all the _brokers_ are running 2.1.0, but the _clients_ will still be using Kafka 2.0.0. We can upgrade the clients now. Because the `log.message.format.version` broker config is set to `2.0` the 2.1.0 brokers will downconvert messages from 2.1.0 producers before they're appended to the log. This ensures that 2.0.0 clients don't see messages in the 2.1.0 (which they won't understand). This downconversion puts significant extra load on the brokers, so it's usual to try to minimise the period during which this downconversion is necessary.

In other words, once a topic(s) has a 2.1.0 producer active there's a real incentive to upgrade all the consumers of that topic so downconversion is no longer needed. Notes:

1. There is no way to configure a 2.1.0 producer to use the 2.0.0 message format. All the matters is the existence of a 2.1.0 producer.

2. It is possible to do the 2.0.0→2.1.0 transition on a topic-by-topic basis (rather than upgrading all producers for the whole cluster at once). 

3. You can upgrade the consumers to 2.1.0 first. 

4. The per-topic `message.format.version` config parameter gives fine-grained control over which message format version the broker will use when appending messages. When it's not set it defaults to the broker's `log.message.format.version` config parameter.

Once all the clients (or at least all the consumers) have been upgraded we can perform the final step.

* `oc edit kafka my-cluster`
* In the `Kafka.spec.kafka.config` change the `log.message.format.version` to `"2.1"`.

      kind: Kafka
      # ...
      spec:
        # ...
        kafka:
          # ...
          config: 
            log.message.format.version: "2.1"

The Cluster Operator will perform a final rolling restart of the Kafka cluster. From this point the cluster and clients are fully running Kafka 2.1.0.
