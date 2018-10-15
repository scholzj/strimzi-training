# Strimzi Training - Lab 7

Lab 7 is using Strimzi 0.8.0. It takes you through configuration aspect of Kafka Mirror Maker deployment

* Checkout this repository which will be used during the lab:
  * `git clone https://github.com/stanlyDoge/strimzi-training.git`
* Go to the `lab-7` directory
  * `cd lab-7`
* Start you OpenShift cluster
  * You should use OpenShift 3.9 or higher
  * Run `minishift start` or `oc cluster up`
* Login as cluster administrator
  * `oc login -u system:admin`
* Install the Cluster Operator
  * `oc apply -f install/`

## Mirror Maker

* Deploy the source Kafka cluster
  * `oc apply -f mirror-maker/kafka-source.yaml`
* Deploy the source Kafka cluster
  * `oc apply -f mirror-maker/kafka-target.yaml`
* Wait for the deployments to be ready and check the pods
  * `oc get pods`
* Open `mirror-maker/kafka-mirror-maker.yaml` and have a look at all the configuration
* Deploy the Kafka Mirror Maker cluster and wait until it is deployed
  * `oc apply -f mirror-maker/kafka-mirror-maker.yaml`
* Wait for the deployment to be ready and check the pods
  * `oc get pods`
* Go to the OpenShift webconsole
* Go the the one of the `my-source-cluster-kafka` pods
* Create a topic
  * `./bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic test`
* Wait until the topic is discovered by Mirror Maker
* Create a producer and send a few messages
  * `./bin/kafka-console-producer.sh --broker-list localhost:9092 --topic test`
* Go the the one of the `my-target-cluster-kafka` pods
* Create a consumer and consume messages
  * `./bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic test --from-beginning`
* Observe mirrored messages
* Delete the deployments
  * `oc delete kafkamirrormaker my-mirror-maker`
  * `oc delete kafka my-source-cluster`
  * `oc delete kafka my-target-cluster`


## Configuration
* Open `miror-maker/kafka-mirror-maker.yaml`.
* Make yourself sure 
  * `bootstrap-servers` are set correctly
  * whitelist regular expression matches your topic name
* You can and add `config` section to the both consumer and producer.
  * Optional properties are listed at http://kafka.apache.org/08/documentation/#configuration


##  Explanation
* Source/Target

It may be confusing we are setting bootstrap servers for source cluster as consumer and target cluster as producer.
See figure below for explanation.
```
           _______________________
          |                       |
          |   Kafka Mirror Maker  |
          |                       |
          |_consumer_____producer_|
          /                        \
   ______/_________       __________\_____
  |_source cluster_|     |_target cluster_|
```
Kafka Mirror Maker consumes messages from `source` cluster and produces them into the `target` cluster.

* Discovering topics

By default Kafka Mirror Maker mirrors only messages which were sent after Kafka Mirror Maker dicovers appropriate topic.

```
                            Kafka MM deployed
 time   __________________________|__________________\
                                      |              / 
                                topic discovered
                                messages are mirrored from this moment
```
