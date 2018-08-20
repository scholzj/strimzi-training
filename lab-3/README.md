# Strimzi Training - Lab 3

Lab 2 is using Strimzi 0.6.0. It takes you through the topic and user management.

* Checkout this repository which will be used during the lab:
  * `git clone https://github.com/scholzj/strimzi-training.git`
* Go to the `lab-3` directory
  * `cd lab-3`
* Start you OpenShift cluster
  * You should use OpenShift 3.9 or higher
  * Run `minishift start` or `oc cluster up`
* Login as cluster administrator
  * `oc login -u system:admin`
* Install the Cluster Operator
  * `oc apply -f install/`

## Plain connections

* Deploy a plain Kafka cluster without any security feature and wait until the cluster is deployed
  * `oc apply -f plain/kafka.yaml`
* Check the `plain/hello-world.yaml` YAML file with the deployment
  * Notice what does the YAML file contain:
    * Topic
    * Consumer
    * Producer
  * Deploy it using `oc apply -f plain/hello-world.yaml`
* Check the producer and consumer logs from the Openshift webconsole or from the command line to verify that they are working:
  * `oc logs $(oc get pod -l app=hello-world-producer -o=jsonpath='{.items[0].metadata.name}') -f`
  * `oc logs $(oc get pod -l app=hello-world-consumer -o=jsonpath='{.items[0].metadata.name}') -f`

## Encrypted connections

* Edit the cluster deployment and change the listeners configuration and wait until the rolling update is finished
  * Disable the `plain` interface and enable `tls` interface:

```yaml
    listeners:
      tls: {}
```

* Have a look at the `my-cluster-cluster-ca-cert` secret which contains the public key which can be used to verify the Kafka broker identity
  * `oc get secret my-cluster-cluster-ca-cert -o yaml`
* Once the rolling update is finished, the old consumer and producer should have stopped working
* Update the Hello World consumer and producer to use TLS
  * In both consumer and producer:
    * Change the port number to `9093`
    * Use the Secret created you checked above
      * Mount the certificate from the Secret to an environment variable

```yaml
          - name: CA_CRT
            valueFrom:
              secretKeyRef:
                name: my-cluster-cluster-ca-cert
                key: ca.crt
          - name: BOOTSTRAP_SERVERS
            value: my-cluster-kafka-bootstrap:9093
```

* Apply the changes and check that the updated producers and consumers started working again
  * `oc logs $(oc get pod -l app=hello-world-producer -o=jsonpath='{.items[0].metadata.name}') -f`
  * `oc logs $(oc get pod -l app=hello-world-consumer -o=jsonpath='{.items[0].metadata.name}') -f`

## Authenticated connections

* Edit the cluster deployment and Enable authentication on the TLS listener:

```yaml
    listeners:
      tls:
        authentication:
          type: tls
```

* Create the new users for consumer and producer
  * `oc apply -f authentication/my-consumer.yaml`
  * `oc apply -f authentication/my-producer.yaml`
* Have a look at the `my-producer` and `my-consumer` secrets which contains the new broker certificates
  * `oc get secret my-producer -o yaml`
  * `oc get secret my-consumer -o yaml`
* Once the rolling update is finished, the old consumer and producer should have stopped working
* Update the Hello World consumer and producer to use TLS Client Authentication
  * Notice in `authentication/hello-world.yaml` that all resources can be combined into a single file:
    * Topic
    * Both users
    * Consumer
    * Producer
  * In both consumer and producer:
    * Use the Secret created you checked above
      * Mount the certificate from the Secret to an environment variable

```yaml
          - name: USER_CRT
            valueFrom:
              secretKeyRef:
                name: my-consumer
                key: user.crt
          - name: USER_KEY
            valueFrom:
              secretKeyRef:
                name: my-consumer
                key: user.key
```

and

```yaml
          - name: USER_CRT
            valueFrom:
              secretKeyRef:
                name: my-producer
                key: user.crt
          - name: USER_KEY
            valueFrom:
              secretKeyRef:
                name: my-producer
                key: user.key
```

* Apply the changes and check that the updated producers and consumers started working again
  * `oc logs $(oc get pod -l app=hello-world-producer -o=jsonpath='{.items[0].metadata.name}') -f`
  * `oc logs $(oc get pod -l app=hello-world-consumer -o=jsonpath='{.items[0].metadata.name}') -f`

## Authorized connections

* Edit the cluster deployment and Enable authentication on the TLS listener:

```yaml
    authorization:
      type: simple
```

* Once the rolling update is finished, the old consumer and producer should have stopped working
* Edit the users and ad them the ACL rights needed for producing and consuming messages:

```yaml
  authorization:
    type: simple
    acls:
      - resource:
          type: topic
          name: my-topic
        operation: Read
      - resource:
          type: topic
          name: my-topic
        operation: Describe
      - resource:
          type: group
          name: my-hello-world-consumer
        operation: Read
```

and

```yaml
  authorization:
    type: simple
    acls:
      - resource:
          type: topic
          name: my-topic
        operation: Write
      - resource:
          type: topic
          name: my-topic
        operation: Describe
```

* Apply the changes and check that the updated producers and consumers started working again
  * `oc logs $(oc get pod -l app=hello-world-producer -o=jsonpath='{.items[0].metadata.name}') -f`
  * `oc logs $(oc get pod -l app=hello-world-consumer -o=jsonpath='{.items[0].metadata.name}') -f`
* _On your own: Play with the ACL rules to see in more detail how they work_