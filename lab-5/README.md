# Strimzi Training - Lab 5

Lab 2 is using Strimzi 0.6.0. It takes you through different aspects of monitoring Strimzi.

* Checkout this repository which will be used during the lab:
  * `git clone https://github.com/scholzj/strimzi-training.git`
* Go to the `lab-5` directory
  * `cd lab-5`
* Start you OpenShift cluster
  * You should use OpenShift 3.9 or higher
  * Run `minishift start` or `oc cluster up`
* Login as cluster administrator
  * `oc login -u system:admin`
* Install the Cluster Operator
  * `oc apply -f install/`
* Install the Kafka cluster
  * `oc apply -f kafka.yaml`
* Install the Hello World application
  * `oc apply -f hello-world.yaml`

## Command line tools

* Exec into one of the Kafka pods
  * `oc exec -ti my-cluster-kafka-0 -- bash`

### Topic monitoring

* Topics can be monitored using the `kafka-topics.sh` utility
* Describe all topics
  * `bin/kafka-topics.sh --zookeeper localhost:2181 --describe`
* List all under-replicated partitions
  * `bin/kafka-topics.sh --zookeeper localhost:2181 --describe --under-replicated-partitions` (should be empty in our cluster)
* List unavailable partitions
  * `bin/kafka-topics.sh --zookeeper localhost:2181 --describe --under-replicated-partitions`  (should be empty in our cluster)

### Consumer group monitoring

* List consumer groups
  * `bin/kafka-consumer-groups.sh --bootstrap-server my-cluster-kafka-bootstrap:9092 --list`
* Show consumer group details
  * `bin/kafka-consumer-groups.sh --bootstrap-server my-cluster-kafka-bootstrap:9092 --describe --group my-hello-world-consumer`
  * Notice how all partitions are attached to single consumer
  * Try to scale the consumer instances with `oc scale deployment hello-world-consumer --replicas=3`
  * Check the consumer group details again and notice how the consumer group rebalanced with the new replicas
  * Try to scale the consumer to 0 instances with `oc scale deployment hello-world-consumer --replicas=0`
  * After the pods are terminated, check the consumer group again and notice how the lag starts incresing
* Reset the consumer offset for the topic `my-topic`
  * Reset the offset to the latest message with `bin/kafka-consumer-groups.sh --bootstrap-server my-cluster-kafka-bootstrap:9092 --group my-hello-world-consumer --reset-offsets --topic my-topic --execute --to-latest` and check that the lag disappeared
  * Or to the earliest message with `bin/kafka-consumer-groups.sh --bootstrap-server my-cluster-kafka-bootstrap:9092 --group my-hello-world-consumer --reset-offsets --topic my-topic --execute --to-earliest` and check that the offsets are now `0`.
  * Scale the consumer to at least one pod with `oc scale deployment hello-world-consumer --replicas=3`
  * Verify that the new pod started consuming messages from the offset 0
  * _On your own: Play with the other options how to reset the offsets based on time, to specific offset etc._
* Offsets can be observed with from different perspectives
  * The default perspective was based on partition
  * You can also display the offsets based on the client
  * `bin/kafka-consumer-groups.sh --bootstrap-server my-cluster-kafka-bootstrap:9092 --describe --group my-hello-world-consumer --members --verbose`

### Log dirs

* Log dirs used to store topic can be described using `kafka-log-dirs.sh`
  * Run `bin/kafka-log-dirs.sh --bootstrap-server my-cluster-kafka-bootstrap:9092 --describe --topic-list my-topic` to list where are the data of our topic located
  * Formated example of the output can be found in [kafka-log-dirs-example.json](./kafka-log-dirs-example.json)
  * This tool can be used to analyze the topic logs for offset and to see in which directory it is stored (useful when JBOD storage is used - currently not supported by Strimzi / AMQ Streams)