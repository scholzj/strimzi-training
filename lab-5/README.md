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
  * `bin/kafka-topics.sh --zookeeper localhost:2181 --describe --unavailable-partitions`  (should be empty in our cluster)

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

## Kafka Connect

* Deploy Kafka Connect
  * `oc apply -f connect.yaml`
  * The deployment also created an OpenShift Route for the Kafka Connect REST API
* Deploy the FileSink connetor from your local command line:
  * `curl -X POST -H "Content-Type: application/json" --data '{ "name": "sink-test", "config": { "connector.class": "FileStreamSink", "tasks.max": "1", "topics": "my-topic", "file": "/tmp/test.sink.txt" } }' http://my-connect-cluster-myproject.127.0.0.1.nip.io/connectors`
* Check that the connector has been deployed and that it works:
  * `curl http://my-connect-cluster-myproject.127.0.0.1.nip.io/connectors/sink-test/status`
  * `curl http://my-connect-cluster-myproject.127.0.0.1.nip.io/connectors/sink-test/config`
  * `curl http://my-connect-cluster-myproject.127.0.0.1.nip.io/connectors/sink-test/tasks/0/status`
* Try to deploy another connector which will fail:
  * `curl -X POST -H "Content-Type: application/json" --data '{ "name": "sink-failing", "config": { "connector.class": "FileStreamSink", "tasks.max": "1", "topics": "my-topic", "file": "/root/this.will.not.work.txt" } }' http://my-connect-cluster-myproject.127.0.0.1.nip.io/connectors`
* Check the state of the failing connector:
  * `curl http://my-connect-cluster-myproject.127.0.0.1.nip.io/connectors/sink-failing/status`
  * `curl http://my-connect-cluster-myproject.127.0.0.1.nip.io/connectors/sink-failing/tasks/0/status`

## Prometheus metrics

* Check the `kafka.yaml` and `connect.yaml` files
  * Check the metrics configuration in the `metrics` fields
* Deploy Prometheus and Grafana installation
  * `oc apply -f prometheus/`
* Open Grafana on address[http://grafana-myproject.127.0.0.1.nip.io](http://grafana-myproject.127.0.0.1.nip.io)
  * Login with username `admin` and password `admin`
  * Click the _Add data source_ button
  * Add new data source with following options:
    * Name: `Prometheus`
    * Type: `Prometheus`
    * URL: `http://prometheus:9090`
    * Press the _Add_ button and make sure is says _Success: Data source is working_
    * In the same window switch to Dahsboard and import the _Prometheus Stats_ dashboard
  * Click the icon in the top left corner and select _Dashboards_ and _Home_ and afterwards in the menu next to the icon select the _Prometheus Stats_ dashboard.
    * Verify that you see the _Target Scrapes_ and _Scrape Duration_ charts
    * These show metrics of how Prometheus scrapes the Kafka metrics
  * Click the icon in the top left corner and select _Dashboards_ and _Import_
    * In the import window, select the `dashboard.json` file from this directory and `Prometheus` as the data source and import it
    * Select the _Kafka Dashboard_ and have a look at the metrics
  * Play with the Kafka components and watch how it reflects ni the metrics. For example:
    * Scale the consumer down to 0 using `oc scale deployment hello-world-consumer --replicas=0`
    * Scale the producer to 0 using `oc scale deployment hello-world-producer --replicas=0`
    * Change the number of messages the producer is sending (environment variable `DELAY_MS` in the producer deployment)
  * Try to add some new chart to the Dashboard
    * Click on the title of the _Bytes Out Per Second_ chart and select duplicate
    * Click th title of the newly added graph and select edit
    * In the _General_ tab change the title to _Bytes In Per Second: my-topic_
    * In the _Metrics_ change the query to `sum(kafka_server_brokertopicmetrics_bytesinpersec_topic_my_topic)`
    * _On you own: Try to do this for other topics and find out which topic generates most trafic_