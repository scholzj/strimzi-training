# Strimzi Training - Lab 10

Lab 10 is using Strimzi 0.11.0. It takes you through the changes between Strimzi 0.8.0 (AMQ Streams 1.0) and 0.11.0 (AMQ Streams 1.1).

* Checkout this repository which will be used during the lab:
  * `git clone https://github.com/scholzj/strimzi-training.git`
* Go to the `lab-10` directory
  * `cd lab-10`
* Start you OpenShift cluster
  * You should use OpenShift 3.9 or higher
  * Run `minishift start` or `oc cluster up`
* Login as cluster administrator
  * `oc login -u system:admin`
* If you are not in a project `myproject`, create it and set it as active
  * `oc new-project myproject`
  * `oc project myproject`
* Install the Cluster Operator
  * `oc apply -f install/`

## Customizing deployments

* Check the cluster deployment in `templating/kafka.yaml`
  * Notice the `template` fields with their definitions of labels and annotations
  * For pods we also define the termination grace period which defines how much time do we give to the pods to terminate
  * Missing in this examples are image pull secrets and security context
* Apply the Kafka resource
  * `oc apply -f templating/kafka.yaml`
* Check the labels and annotations assigned to the different resources such as services, pods, etc.
  * `oc get services -o='custom-columns=NAME:.metadata.name,LABELS:.metadata.labels,ANNOTATIONS:.metadata.annotations'`
* On your own
  * Try to use some other customization such as Security Context or Image Pull Secrets
* Delete the Kafka cluster
  * `oc delete kafka my-cluster`

## Network policies

_This demo should be done on a cluster with enabled network policies SDN plugin. In a cluster without network policies, they will still be created, but they will be ignored by the SDN plugin._

* Check the cluster deployment in `network-policies/kafka.yaml`
  * Notice the `networkPolicyPeers` fields with the definitions of peers which are allowed to connect
* Apply the Kafka resource
  * `oc apply -f network-policies/kafka.yaml`
* Once the cluster is created, check the network policies
  * List all network policies `oc get networkpolicies`
  * Check the Zookeeper network policies in detail: `oc describe networkpolicy my-cluster-network-policy-zookeeper`
  * Check the Kafka network policies in detail: `oc describe networkpolicy my-cluster-network-policy-kafka`
* Edit the `my-cluster` resource to allow all clients from namespace with label `allow-kafka-access` set to `true` to acccess the `tls` listener:
  * Do `oc edit kafka my-cluster` and add following YAML snippet to `.spec.kafka.listeers.tls.networkPolicyPeers`:

```yaml
        - namespaceSelector:
            matchLabels:
              allow-kafka-access: "true"
```

* Check the Kafka network policies again to see that the change was applied
  * `oc describe networkpolicy my-cluster-network-policy-kafka`
* Try to run a message producer or consumer with the matching labels:
  * `oc run kafka-producer -ti --image=strimzi/kafka:0.11.0-kafka-2.1.0 --rm=true -l app=kafka-producer --restart=Never -- bin/kafka-console-producer.sh --broker-list my-cluster-kafka-bootstrap:9092 --topic my-topic`
  * `oc run kafka-consumer -ti --image=strimzi/kafka:0.11.0-kafka-2.1.0 --rm=true -l app=kafka-consumer --restart=Never -- bin/kafka-console-consumer.sh --bootstrap-server my-cluster-kafka-bootstrap:9092 --topic my-topic --from-beginning`
  * These should work fine because they are allowed
* Try to run a message producer or consumer without the matching labels:
  * `oc run kafka-producer -ti --image=strimzi/kafka:0.11.0-kafka-2.1.0 --rm=true -l app=some-producer --restart=Never -- bin/kafka-console-producer.sh --broker-list my-cluster-kafka-bootstrap:9092 --topic my-topic`
  * `oc run kafka-consumer -ti --image=strimzi/kafka:0.11.0-kafka-2.1.0 --rm=true -l app=some-consumer --restart=Never -- bin/kafka-console-consumer.sh --bootstrap-server my-cluster-kafka-bootstrap:9092 --topic my-topic --from-beginning`
  * These should not work when network policies are enabled
* Delete the Kafka cluster
  * `oc delete kafka my-cluster`

## Handling Secrets in Kafka Connect

* Deploy a Kafka cluster 
  * `oc apply -f connect-secrets/kafka.yaml`
* Create the secrets wich we will use:
  * `oc apply -f connect-secrets/env-var-secret.yaml`
  * `oc apply -f connect-secrets/properties-secret.yaml`
* Create the Kafka Connect cluster
  * Check the file `connect-secrets/kafka-connect.yaml`
  * In particular, notice the `config` section and its configuration of the file provider and the `externalConfiguration` section

```yaml
  config:
    # ...
    config.providers: file
    config.providers.file.class: org.apache.kafka.common.config.provider.FileConfigProvider
```     

```yaml
  externalConfiguration:
    env:
    - name: AWS_ACCESS_KEY_ID
      valueFrom:
        secretKeyRef:
          name: env-var-secret
          key: accessKey
    - name: AWS_SECRET_ACCESS_KEY
      valueFrom:
        secretKeyRef:
          name: env-var-secret
          key: secretAccessKey
    volumes:
    - name: my-properties-secret
      secret:
        secretName: properties-secret
```

* Crete the Kafka Connect cluster
  * `oc apply -f connect-secrets/kafka-connect.yaml`
* After Kafka Connect cluster is deplyoed, check that the secrets were correctly set
  * Exec into the pod `oc exec -ti $(oc get pod -l app=my-connect -o=jsonpath='{.items[0].metadata.name}') -- bash`
  * Check the environment variables
    * `echo $AWS_ACCESS_KEY_ID`
    * `echo $AWS_SECRET_ACCESS_KEY`
  * Check the volume
    * `ls -l external-configuration/my-properties-secret/`
    * `cat external-configuration/my-properties-secret/credentials.properties`
* Create a Kafka Connect plugin using these values
  * Exec into the pod `oc exec -ti $(oc get pod -l app=my-connect -o=jsonpath='{.items[0].metadata.name}') -- bash`
  * Create the connector:
    `curl -X POST -H "Content-Type: application/json" --data '{ "name": "sink-test", "config": { "connector.class": "FileStreamSink", "tasks.max": "3", "topics": "my-topic", "file": "/tmp/test.sink.txt", "username":"${file:/opt/kafka/external-configuration/my-properties-secret/credentials.properties:username}","password":"${file:/opt/kafka/external-configuration/my-properties-secret/credentials.properties:password" } }' http://localhost:8083/connectors`
  * Notice the `username` and `password` options set from the file
    * `"username":"${file:/opt/kafka/external-configuration/my-properties-secret/credentials.properties:username"`
    * `"password":"${file:/opt/kafka/external-configuration/my-properties-secret/credentials.properties:password"`
* Delete the Kafka and Kafka Connect clusters
  * `oc delete kafkaconnect my-connect`
  * `oc delete kafka my-cluster`








