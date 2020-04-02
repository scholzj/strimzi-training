# Lab 13 - Strimzi / AMQ Streams 1.4.0

## Install cluster operator

### On OCP4

* Install AMQ Streams 1.4 / Strimzi from the Operator Hub to watch all namespaces

### On OCP 3.11

* Decide into which namespace will you install the operator
* On Linux, use:

```
sed -i 's/namespace: .*/namespace: my-namespace/' 00-install/*RoleBinding*.yaml
```

* On MacOS, use:

```
sed -i '' 's/namespace: .*/namespace: my-namespace/' 00-install/*RoleBinding*.yaml
```

* Install the operator:
  * `oc apply -f 00-install`

## Create namespaces

* Create two namespaces `demo-europe` and `demo-na`
  * `oc new-project demo-na`
  * `oc new-project demo-europe`

## Deploy Kafka clusters

* We need to deploy the Kafka clusters in their respective namespaces
  * `oc apply -f 01-kafka-europe.yaml`
  * `oc apply -f 02-kafka-na.yaml`

## Deploy applications using the clusters

* Once the clusters are running, we can deploy some applications using them:
  * `oc apply -f 03-application-europe.yaml`
  * `oc apply -f 04-application-na.yaml`
* Notice that both applications are producing to the topic `my-topic`
* Check that the applications work and consume only the local messages

## Deploy the Mirror Makers

* Now we can deploy the Mirror Makers 2
  * `oc apply -f 05-mirror-maker-2-europe.yaml`
  * `oc apply -f 06-mirror-maker-2-na.yaml`
* MM2s should sync the topics
  * The Europe consumer should now be consuming from a copy of the NA topic and vice versa
  * Notice the topic names on both sides

## Read the data from Kafka Connect

* The mirrored data can be consumed also from Kafka Connect
* Deploy Kafka Connect
  * `oc apply -f 07-kafka-connect.yaml`
* Notice the annotation enabling the connector operator

## Deploy the Connector

* Check the list of connectors in the KafkaConnect status
* Deploy the connector
  * For test purposes we use only FileSink connector
  * `oc apply -f 08-file-sink-connector.yaml`
* Notice the status of the connector
* Check the file with the received messages
  * `oc exec -n demo-europe $(oc get pods -n demo-europe -o name | grep europe-connect) -ti -- tail -f /tmp/messages.sink.txt`

## Custom certificates

* Generate new self-signed certificate:
  * `keytool -genkeypair -storetype PKCS12 -keystore server.p12 -dname "CN=MyCustomCert, O=StrimziTraining" -storepass 123456 -keyalg RSA -alias server -ext SAN=dns:$(oc get routes -n demo-europe europe-kafka-bootstrap -o jsonpath='{.status.ingress[0].host}'),dns:$(oc get routes -n demo-europe europe-kafka-0 -o jsonpath='{.status.ingress[0].host}')`
  * Replace the DNS name or the IP address with the DNS names or IP address of your server
* Extract the public and private keys into PEM format
  * `openssl pkcs12 -in server.p12 -out server.crt -clcerts -nokeys -passin pass:123456`
  * `openssl pkcs12 -in server.p12 -out server.key -nocerts -nodes -passin pass:123456`
* Create a secret with the public and private keys

```
oc create secret generic custom-cert \
  --from-file=tls.crt=server.crt \
  --from-file=tls.key=server.key \
  --namespace demo-europe
```

* Create trust store with the server certificate
  * `keytool -import -storetype PKCS12 -alias server -keystore truststore.p12 -file server.crt -storepass 123456`
* Edit the Europe Kafka cluster from the Mirror Maker demo and configure external listener with the custom certificate
  * Use `oc edit kafka europe`
  * Add following YAML snippet to `spec.kafka.listeners`

```yaml
      external:
        type: route
        configuration:
          brokerCertChainAndKey:
            secretName: custom-cert
            certificate: tls.crt
            key: tls.key
        authentication:
          type: tls
```

* Wait after the rolling update is finished
* Check the custom certificate using OpenSSL
  * `openssl s_client -connect $(oc get routes -n demo-europe europe-kafka-bootstrap -o jsonpath='{.status.ingress[0].host}'):443 -showcerts -servername $(oc get routes -n demo-europe europe-kafka-bootstrap -o jsonpath='{.status.ingress[0].host}')`
  * Check in the log that the server certificate is the custom one

## PKCS12 certificates

* Create a user with TLS authentication using the User Operator
  * `oc apply -f 09-user.yaml`
* Check the secret and notice the PKCS12 file and the corresponding password
* Extract the PKCS12 file and the password
  * `oc extract -n demo-europe secret/my-user --keys=user.p12 --to=- > user.p12`
  * `oc extract -n demo-europe secret/my-user --keys=user.password --to=- > user.pwd`
* Run Kafka console consumer to consume messages

```
./kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server=$(oc get routes -n demo-europe europe-kafka-bootstrap -o=jsonpath='{.status.ingress[0].host}{"\n"}'):443 \
  --whitelist '.*my-topic' --from-beginning \
  --consumer-property security.protocol=SSL \
  --consumer-property ssl.truststore.password=123456 \
  --consumer-property ssl.truststore.location=truststore.p12 \
  --consumer-property ssl.keystore.password=$(<user.pwd) \
  --consumer-property ssl.keystore.location=./user.p12
```

## User Quotas

_Note: the quotas currently do not work with TLS users due to a bug in Strimzi / AMQ Streams. So this demo will not work properly._

* Use the performance test producer to send some messages without any quotas

```
./kafka/bin/kafka-producer-perf-test.sh \
  --topic my-topic \
  --throughput 10000000 --num-records 1000000 \
  --producer-props bootstrap.servers=$(oc get routes -n demo-europe europe-kafka-bootstrap -o=jsonpath='{.status.ingress[0].host}{"\n"}'):443 security.protocol=SSL ssl.truststore.type=PKCS12 ssl.truststore.password=123456 ssl.truststore.location=truststore.p12 ssl.keystore.type=PKCS12 ssl.keystore.password=$(<user.pwd) ssl.keystore.location=user.p12 \
  --record-size 1000
```

* Edit the `my-user` user and set the quotas
  * `oc edit -n demo-europe kafkauser my-user`
  * Se the quotas:

```yaml
  quotas:
    producerByteRate: 100000
```

* Run the producer again and notice how the speed changed:

```
./kafka/bin/kafka-producer-perf-test.sh \
  --topic my-topic \
  --throughput 10000000 --num-records 100000 \
  --producer-props bootstrap.servers=$(oc get routes -n demo-europe europe-kafka-bootstrap -o=jsonpath='{.status.ingress[0].host}{"\n"}'):443 security.protocol=SSL ssl.truststore.type=PKCS12 ssl.truststore.password=123456 ssl.truststore.location=truststore.p12 ssl.keystore.type=PKCS12 ssl.keystore.password=$(<user.pwd) ssl.keystore.location=user.p12 \
  --record-size 1000
```
        