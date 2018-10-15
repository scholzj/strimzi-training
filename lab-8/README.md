# Strimzi Training - Lab 7

Lab 6 is using Strimzi 0.8.0. It takes you through the changes between Strimzi 0.6.0 and 0.8.0.

* Checkout this repository which will be used during the lab:
  * `git clone https://github.com/scholzj/strimzi-training.git`
* Go to the `lab-7` directory
  * `cd lab-7`
* Start you OpenShift cluster
  * You should use OpenShift 3.9 or higher
  * Run `minishift start` or `oc cluster up`
* Login as cluster administrator
  * `oc login -u system:admin`
* Install the Cluster Operator
  * `oc apply -f install/`
* Download Apache Kafka:

```
curl -LO https://www-us.apache.org/dist/kafka/2.0.0/kafka_2.11-2.0.0.tgz
tar -xzf kafka_2.11-2.0.0.tgz
mv kafka_2.11-2.0.0 kafka
```

## Exposing Kafka to the outside of OpenShift

### OpenShift Routes

* Review the file `exposing/route.yaml` and notice the `external` listerner set to type `route`
* Apply the file `oc apply -f exposing/route.yaml`
* Check the routes which have been created `oc get routes`
* Check also the additional services which have been created to route traffic to individual brokers `oc get service`
* Extract the public key of the CA which signed the broker certificates and create a JKS truststore
  * `oc extract secret/my-cluster-cluster-ca-cert --keys=ca.crt --to=- > ca.crt`
  * `keytool -import -file ca.crt -keystore truststore -storepass 123456` (type _yes_ to trust the certificate)
* Run the clients locally on your PC
  * Review `exposing/client-tls.properties` with TLS configuration
  * Run the producer 
    * Find the address of your bootstrap service address `oc get routes my-cluster-kafka-bootstrap -o=jsonpath='{.status.ingress[0].host}{"\n"}'`
    * Routes are always listening on port 443 (HTTPS)
    * Use the address and the port from above in the `kafka-console-producer.sh` command
    * `kafka/bin/kafka-console-producer.sh --broker-list my-cluster-kafka-bootstrap-myproject.192.168.64.2.nip.io:443 --producer.config exposing/client-tls.properties --topic my-topic`
    * Send some messages and press Ctrl+D or Ctrl-C to exit
  * Run the consumer 
    * Use the address and the port from above in the `kafka-console-consumer.sh` command
    * `kafka/bin/kafka-console-consumer.sh --bootstrap-server my-cluster-kafka-bootstrap-myproject.192.168.64.2.nip.io:443 --consumer.config exposing/client-tls.properties  --group my-group --topic my-topic --from-beginning`
    * Check that you received all the messages you sent and press Ctrl-C to exit

### Load Balancers

_Note: This will work only on OpenShift installations where the LoadBalancers are supported_

* Review the file `exposing/loadbalancer.yaml` and notice the `external` listerner set to type `loadbalancer`
* Apply the file `oc apply -f exposing/loadbalancer.yaml`
* Check the services which have been created and their type `oc get services`
* The CA public key didn't changed, so you can sue the `truststore` file from previous section
* Run the clients locally on your PC
  * Run the producer 
    * Find the address of your bootstrap service address `oc get service my-cluster-kafka-external-bootstrap -o=jsonpath='{.status.loadBalancer.ingress[0].hostname}{"\n"}'`
    * If no hostname is found, they to check for an IP address instead `oc get service my-cluster-kafka-external-bootstrap -o=jsonpath='{.status.loadBalancer.ingress[0].ip}{"\n"}'`
    * Loadbalancers are always listening on port 9094
    * Use the address and the port from above in the `kafka-console-producer.sh` command
    * `kafka/bin/kafka-console-producer.sh --broker-list 172.29.72.135:9094 --producer.config exposing/client-tls.properties --topic my-topic`
    * Send some messages and press Ctrl+D or Ctrl-C to exit
  * Run the consumer 
    * Use the address and the port from above in the `kafka-console-consumer.sh` command
    * `kafka/bin/kafka-console-consumer.sh --bootstrap-server 172.29.72.135:9094 --consumer.config exposing/client-tls.properties  --group my-group --topic my-topic --from-beginning`
    * Check that you received all the messages you sent and press Ctrl-C to exit

### Node Ports

_Note: This will work only on OpenShift installations where the nodes are accessible from the outside_

* Review the file `exposing/nodeport.yaml` and notice the `external` listerner set to type `nodeport`
* Apply the file `oc apply -f exposing/nodeport.yaml`
* Check the services which have been created and their type `oc get services`
* The CA public key didn't changed, so you can sue the `truststore` file from previous section
* Run the clients locally on your PC
  * Node Ports currently do not support hostname verification
  * Review `exposing/client-tls-no-hostname-verification.properties` with TLS configuration and notice the disabled hostname verification (option `ssl.endpoint.identification.algorithm`)
  * Run the producer 
    * Find the address of your node `oc get node localhost -o=jsonpath='{range .status.addresses[*]}{.type}{"\t"}{.address}{"\n"}'`
      * For the bootstrap service you can sue whatever node of your cluster
      * The addresses are used in the following order: ExternalDNS, ExternalIP, InternalDNS, InternalIP, Hostname
    * Find the port where Kafka is listening `oc get service my-cluster-kafka-external-bootstrap -o=jsonpath='{.spec.ports[0].nodePort}{"\n"}'`
    * Use the address and the port from above in the `kafka-console-producer.sh` command
    * `kafka/bin/kafka-console-producer.sh --broker-list 192.168.64.2:31264 --producer.config exposing/client-tls-no-hostname-verification.properties --topic my-topic`
    * Send some messages and press Ctrl+D or Ctrl-C to exit
  * Run the consumer 
    * Use the address and the port from above in the `kafka-console-consumer.sh` command
    * `kafka/bin/kafka-console-consumer.sh --bootstrap-server 192.168.64.2:31264 --consumer.config exposing/client-tls-no-hostname-verification.properties  --group my-group --topic my-topic --from-beginning`
    * Check that you received all the messages you sent and press Ctrl-C to exit

* Node ports and load balancers can be also used without TLS
* Edit the Kafka cluster `oc edit kafka my-cluster` and change the following section (add `tls: false`):

```yaml
      external:
        type: nodeport
        tls: false
```

* Wait until the rolling update is finished
* Run the clients locally on your PC without TLS
  * Run the producer 
    * The address and port should be the same as in previous example
    * Use the address and the port from above in the `kafka-console-producer.sh` command
    * `kafka/bin/kafka-console-producer.sh --broker-list 192.168.64.2:31264 --topic my-topic`
    * Send some messages and press Ctrl+D or Ctrl-C to exit
  * Run the consumer 
    * Use the address and the port from above in the `kafka-console-consumer.sh` command
    * `kafka/bin/kafka-console-consumer.sh --bootstrap-server 192.168.64.2:31264 --group my-group --topic my-topic --from-beginning`
    * Check that you received all the messages you sent and press Ctrl-C to exit

## SASL SCRAM-SHA-512 Authentication

* Review the file `scram-sha-512/scram-sha-512.yaml` and notice the `authentication` enabled for all listerners and set to type `scram-sha-512`
* Apply the file `scram-sha-512/scram-sha-512.yaml`
* Create a user with SCRAM-SHA-512 authentication
  * Review the file `scram-sha-512/user.yaml`
  * Apply the file `oc apply -f scram-sha-512/user.yaml`
  * Check the secret created by the User Operator for the password:
    * `oc extract secret/my-user --keys=password --to=-`
* Run the clients locally on your PC
  * Review `scram-sha-512/client.properties` with the SASL configuration
    * Edit the file and replace the password with the password generated by the User Operator
  * The addresses should be the same as in previous example
  * Run the producer  
    * Use the address and the port from above in the `kafka-console-producer.sh` command
    * `kafka/bin/kafka-console-producer.sh --broker-list 192.168.64.2:31264 --producer.config scram-sha-512/client.properties --topic my-topic`
    * Send some messages and press Ctrl+D or Ctrl-C to exit
  * Run the consumer 
    * Use the address and the port from above in the `kafka-console-consumer.sh` command
    * `kafka/bin/kafka-console-consumer.sh --bootstrap-server 192.168.64.2:31264 --consumer.config scram-sha-512/client.properties  --group my-group --topic my-topic --from-beginning`
    * Check that you received all the messages you sent and press Ctrl-C to exit

## Manual Actions

* Trigger a rolling update of the Kafka cluster manually
  * `oc annotate statefulset my-cluster-kafka operator.strimzi.io/manual-rolling-update=true`
  * Watch the rolling update `oc get pods -w`
* To trigger pod deletion and PVC deletion, annotate the pod
  * `oc annotate pod my-cluster-kafka-1 operator.strimzi.io/delete-pod-and-pvc=true`
  * Watch the pod and pvc be deleted using `oc get pods -w` and `oc get pvc -w`