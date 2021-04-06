# Lab 16 - Strimzi 0.21 + 0.22 / AMQ Streams 1.7.0: Part 1

## Install cluster operator

* Create namespace/project `myproject` and set it as default.
* On OpenShift 
  * `oc new-project myproject`
  * `oc project myproject`
* On Kubernetes
  * `kubectl create ns myproject`
  * `kubectl config set-context --current --namespace=myproject`

## Upgrade improvements

* Install Strimzi 0.17 (corresponds to AMQ Streams 1.4.0)
  * `kubectl create -f strimzi-0.17.0/`
* Deploy the Kafka cluster with Kafka 2.4.0
  *  `kubectl apply -f kafka-2.4.0.yaml`
  * Notice the `.spec.kafka.version` and `log.message.format.version` in `.spec.kafka.config` with the version 2.4.0
* Once the Kafka cluster is deployed and running, we can upgrade the operator by running these commands
  * `kubectl create -f strimzi-0.22.1/`
  * `kubectl replace -f strimzi-0.22.1/`
  * Once the new version of the operator gets up and running, notice the error stating that `Version 2.4.0 is not supported`
* Edit the Kafka CR with `kubectl edit kafka my-cluster`
  * Change the `.spec.kafka.version` field to 2.7.0
  * The operator knows the `2.7.0` version and should start running the upgrade
  * You should be able to see following messages in the log:
  ```
  2021-04-05 18:40:55 INFO  KafkaAssemblyOperator:996 - Kafka is upgrading from 2.4.0 to 2.7.0
  2021-04-05 18:40:55 INFO  KafkaAssemblyOperator:926 - Kafka upgrade from 2.4.0 to 2.7.0 requires Zookeeper upgrade from 3.5.6 to 3.5.8
  ```

  * In this particular example you will see 2 rolling updates of ZooKeeper and Kafka
    * ZooKeeper needs two rolling updates because of the removal of TLS sidecars which were still used in Strimzi 0.17 / AMQ Streams 1.4
    * Kafka needs two rolling updates as well. The first one will roll Kafka to use 2.7.0, but with `inter.broker.protocol.version` set to `2.4`. Second updates the `inter.broker.protocol.version` to `2.7` since it is not fixed in the Kafka CR.
    * This might differ when you upgrade from different version or with a different Kafka CR.
* Once the initial upgrade is finished, you can go and change the `log.message.format.version` to `2.7` as well

* _On your own:_
  * _Try to do the upgrade without any versions specified (i.e. no `.spec.kafka.version`, `inter.broker.protocol.version` or `log.message.format.version`)_
  * _Try to do the upgrade with `inter.broker.protocol.version` set to `2.4` from the beginning_

## CRD upgrades

_Follows from the previous section_

* Check the CRDs installed with Strimzi 0.22 / AMQ Streams 1.7
  * Notice in the `.spec` section that they have now the `v1beta2` version. Version `v1beta1` or `v1alpha1` is marked as stored.
  * Notice that in the `.status` section they have only the `v1beta1` (or `v1alpha1`) as stored versions
  * You can use this command to check the versions from spec: `kubectl get crd kafkas.kafka.strimzi.io -o jsonpath="{.spec.versions[*].name}"`
* Try to edit the Kafka CR we have:
  * Do `kubectl edit kafka my-cluster`
    * Notice that `kubectl` uses `v1beta2` by default
    * Try to change something in the CR - for example the timeout of Kafka liveness probe - and save the changes
    * You should get an error because the resource is not valid `v1beta2` resource - it is using many deprecated fields
    * Change the `apiVersion` on the top of the file from `kafka.strimzi.io/v1beta2` to `kafka.strimzi.io/v1beta1` and save the changes again
    * This time the should pass
  * Try to also use `kubectl edit kafka.v1beta1.kafka.strimzi.io my-cluster` to use the `v1beta1` API version directly
  * Check the status section and notice the large amount of warnings about deprecated fields
* Have a look at the API Conversion tool located in [`api-conversion`](./api-conversion)
* Use the API Conversion tool to convert the [`kafka-2.4.0.yaml`](./kafka-2.4.0.yaml) file
  * Run `api-conversion/bin/api-conversion.sh convert-file -f kafka-2.4.0.yaml`
  * The converted resource will be printed to the standard output
  * Notice how it converted all the deprecated / removed fields
  * Notice the new Config Maps with the metrics configuration
* Use the API Conversion tool to convert the resource directly inside Kubernetes
  * Run `api-conversion/bin/api-conversion.sh convert-resources`
  * Check that no rolling update is happening (we converted the resource, but nothing really changed in the deployment itself)
  * Edit the resource with `kubectl edit kafka my-cluster` and check that:
    * The resource is now converted
    * The warnings about deprecations are gone
    * Since we do not use any old deprecated fields, we can now use the `v1beta2` API to edit it
  * Check the automatically created Config Maps with metrics configuration
* After you convert all resources, change the CRDs to use the `vbeta2` API as the stored version
  * Run `api-conversion/bin/api-conversion.sh crd-upgrade`
  * Check the `.status` section of the CRDs to see that only `v1beta2` is now used as stored version
  * You can use the command `kubectl get crd kafkas.kafka.strimzi.io -o jsonpath="{.status.storedVersions}"`

* _On your own:_
  * _Try to convert the custom resource files with the output being written into another file_
  * _Try to convert the custom resource files with the output being written into the same file_
  * _Try the different options when converting the resources directly in Kubernetes API server_
  * _Try to convert the custom resource manually_

## New Topic Operator topics

* Check the new topics used by the Topic Operator
  * `kubectl get kt`

* _On your own:_
  * _Check the configuration of these topics also with Kafka APIs (`bin/kafka-topics.sh`, Kafka Admin API or similar)_

## Rolling individual pods

* Annotate one of the Kafka or ZooKeeper pods
  * For example `kubectl annotate pod my-cluster-kafka-1 strimzi.io/manual-rolling-update=true`
  * Wait for the next reconciliation to roll the `my-cluster-kafka-1` pod

* _On your own:_
  * _Annotate multiple pods at the same time and saw how the operators rolls them one by one as with regular rolling update_

## Smaller Kafka improvements

* Check the status of the deployed Kafka cluster
  * Notice the `clusterIs` in the `.status` section
  * You can also get it programmatically using `kubectl get kafka my-cluster -o jsonpath="{.status.clusterId}"`

* Try to disable the owner reference for the Kafka secrets
  * Edit the `Kafka` CR with `kubectl edit kafka my-cluster`
  * Add the following section:
  ```yaml
  spec:
    # ...
    clusterCa:
      generateSecretOwnerReference: false
    clientsCa:
      generateSecretOwnerReference: false
  ```
  * Check the CA secrets (both public and private key secrets) and verify the owner references are gone now

* Add additional labels or annotations to the Kafka cluster public key secret
  * Edit the `Kafka` CR with `kubectl edit kafka my-cluster`
  * Add the following section:
  ```yaml
  spec:
    kafka:
      template:
        clusterCaCert:
          metadata:
            labels:
              my-key: my-label
            annotations:
              my-key: my-anno
  ```
  * Check that the label and annotation are set on the `my-cluster-cluster-ca-cert` secret: `kubectl get secret my-cluster-cluster-ca-cert -o yaml`


## User Operator improvements

* Edit the Kafka CR and add to the `.spec.entityOperator.userOperator` section new field `secretPrefix`
  ```yaml
  # ...
  entityOperator:
    # ...
    userOperator:
      secretPrefix: kafka-
  ```
  * Wait for the User Operator pod to roll
* Check the `KafkaUser` CR in [`kafka-user.yaml`](./kafka-user.yaml)
  * Notice the `.spec.template` section
  * Create the user: `kubectl apply -f kafka-user.yaml`
* Check the created secret
  * Notice it is not named `my-user` but `kafka-my-user`
  * Notice the secret now has the `sasl.jaas.config` key with the JAAS configuration
  * Notice the labels and annotations from the `.spec.template` section
