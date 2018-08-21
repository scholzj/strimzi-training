# Strimzi Training - Lab 1

* Checkout this repository which will be used during the lab:
  * `git clone https://github.com/scholzj/strimzi-training.git`
* Go to the `lab-1` directory
  * `cd lab-1`
* Explore the files in the examples/install/cluster-operator directory: the service account, the RBAC files, the CRD definitions and the Cluster Operator deployment
* Start you OpenShift cluster
  * You should use OpenShift 3.9 or higher
  * Run `minishift start` or `oc cluster up`
* Create new project and give the developer user admin rights and set myproject as the default project
  * `oc new-project myproject2 --display-name="My Project 2"`
  * `oc adm policy add-role-to-user admin developer -n myproject2`
  * `oc project myproject`
* Login as cluster administrator
  * `oc login -u system:admin`
* Edit the file `examples/install/cluster-operator/05-Deployment-strimzi-cluster-operator.yaml`
  * Find the configuration of the environment variable `STRIMZI_NAMESPACE`
  * Set its value to `myproject,myproject2`

```yaml
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  # ...
spec:
  replicas: 1
  template:
    # ...
    spec:
      containers:
      - name: strimzi-cluster-operator
        env:
        - name: STRIMZI_NAMESPACE
          value: myproject,myproject2
        # ...
```

* Open the following files and make sure the namespace set in them is the namespace where you want to deploy Strimzi
  * `examples/install/cluster-operator/02-ClusterRoleBinding-strimzi-cluster-operator.yaml`
  * `examples/install/cluster-operator/03-ClusterRoleBinding-strimzi-cluster-operator-kafka-broker-delegation.yaml`
  * `examples/install/cluster-operator/04-ClusterRoleBinding-strimzi-cluster-operator-topic-operator-delegation.yaml`
* Deploy the Cluster Operator
  * `oc apply -f examples/install/cluster-operator/`
* Check that it is running and that it works
  * Using the OpenShift webconsole
  * Using the command line
    * `oc get deployment strimzi-cluster-operator`
    * `oc logs $(oc get pod -l name=strimzi-cluster-operator -o=jsonpath='{.items[0].metadata.name}')`
* Open the file `examples/kafka/kafka-persistent.yaml` and get familiar with it
* Deploy the Kafka cluster in one of the namespaces which the Cluster Operator is watching
  * `oc apply -f examples/kafka/kafka-persistent.yaml -n myproject`
* Watch as the Kafka cluster is deployed
  * Using the OpenShift webconsole
  * Using the command line
    * `oc -n myproject get pods -l strimzi.io/cluster=my-cluster -w`
* Edit the Kafka cluster:
  * From the command line do `oc edit kafka my-cluster` and change the following section to enable TLS client authentication and authroization. Change it from:

```
    listeners:
      plain: {}
      tls: {}
```

to:

```
    listeners:
      tls:
        authentication:
          type: tls
    authorization:
      type: simple
```

* Watch as the Cluster Operator does a rolling update to reconfigure Kafka
  * `oc -n myproject get pods -l strimzi.io/cluster=my-cluster -w`
* Open the file `examples/topic/kafka-topic.yaml`
  * Edit the topic to set 3 partitions and 2 replicas
* Create the topic
  * `oc apply -f examples/topic/kafka-topic.yaml`
* List the topics:
  * `oc get kafkatopics`
* Check the Topic Operator logs to see how it processed the topic
  * `oc logs -c topic-operator $(oc get pod -l strimzi.io/name=my-cluster-topic-operator -o=jsonpath='{.items[0].metadata.name}')`
* Open the file `examples/user/kafka-user.yaml`
  * Review the access rights configured in the file
* Create the user
  * `oc apply -f examples/user/kafka-user.yaml`
* Check the User Operator logs to see how it processed the user
  * `oc logs -c user-operator $(oc get pod -l strimzi.io/name=my-cluster-topic-operator -o=jsonpath='{.items[0].metadata.name}')`
* Check the secret with the certificate of the newly created user
  * `oc get secret my-user -o yaml`
* List the users:
  * `oc get kafkausers`
* Check the _Hello World_ consumer and producer in `examples/hello-world/deployment.yaml`
  * Notice how it is using the secret created by the User Operator to load the TLS certificates
* Deploy the _Hello World_ producer and consumer
  * `oc apply -f examples/hello-world/deployment.yaml`
* Check the producer and consumer logs to verify that they are working
  * `oc logs $(oc get pod -l app=hello-world-producer -o=jsonpath='{.items[0].metadata.name}') -f`
  * `oc logs $(oc get pod -l app=hello-world-consumer -o=jsonpath='{.items[0].metadata.name}') -f`
* Change the deployment configuration of the producer:
  * `oc edit deployment hello-world-producer`
  * And set the environment variable `TOPIC` to some other topic
  * Wait for the pod to restart and check it gets an _authorization error_ using `oc logs $(oc get pod -l app=hello-world-producer -o=jsonpath='{.items[0].metadata.name}') -f`
  * Edit the KafkaUser resource with `oc edit kafkauser my-user` and update the access rights to allow it to use the new topic
  * Check the logs again to see how it is now authorized to produce to the new topic
* Add a new user `my-connect` for Kafka Connect
  * Edit the `examples/user/kafka-user.yaml` file:
    * Change the username to `my-connect`
    * Remove the `authorization` section
  * Create the user using `oc apply -f examples/user/kafka-user.yaml`
* Edit the Kafka cluster again:
  * From the command line do `oc edit kafka my-cluster` and change the following section to add user `CN=my-connect` as a super user. Change it from:

```
    authorization:
      type: simple
```

to:

```
    authorization:
      type: simple
      superUsers:
        - CN=my-connect
```

* Wait until the rolling update is finished
* Open the file `examples/kafka-connect/kafka-connect.yaml`
  * Notice the configuration related to the authentication
* Deploy Kafka Connect using `oc apply -f examples/kafka-connect/kafka-connect.yaml`
* Wait until Kafka Connect starts and check that it connected to the broker and created some topics using `oc get kafkatopics`