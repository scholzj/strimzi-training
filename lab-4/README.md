# Strimzi Training - Lab 4

Lab 4 is using Strimzi 0.6.0. It takes you through the Role Based Access Control (RBAC).

* Checkout this repository which will be used during the lab:
  * `git clone https://github.com/scholzj/strimzi-training.git`
* Go to the `lab-4` directory
  * `cd lab-4`
* Start you OpenShift cluster
  * You should use OpenShift 3.9 or higher
  * Run `minishift start` or `oc cluster up`
* Create new project and give the developer user admin rights and set myproject as the default project
  * `oc new-project myproject2 --display-name="My Project 2"`
  * `oc adm policy add-role-to-user admin developer -n myproject2`
  * `oc project myproject`
* Login as cluster administrator
  * `oc login -u system:admin`
* Label the local node (needed for Kafka rack awareness)
  * `oc label node localhost rack=local`

## Regular installation

* Install the Cluster Operator
  * `oc apply -f regular-install/`
* Once the Cluster Operator is installed, you can check the deployed RBAC resources
  * `oc get clusterroles | grep strimzi`
  * `oc get clusterrolebindings | grep strimzi`
  * `oc get rolebindings | grep strimzi`
  * Notice that at this point all bindings are for `myproject/strimzi-cluster-operator` service account
* Now deploy the Kafka cluster
  * `oc apply -f kafka.yaml`
  * Notice the Kafka rack awareness configuration in the Kafka cluster configuration
  * Kafka Rack awareness is using the `strimzi-kafka-broker` role (that is why we are using it in this lab)
* Once the Kafka cluster is deployed, you can check the deployed RBAC resources again
  * `oc get clusterroles | grep strimzi` => The roles are static, you should not see any changes
  * `oc get clusterrolebindings | grep strimzi` => You should see new Cluster Role Binding `strimzi-my-cluster-kafka-init` which bind the `strimzi-kafka-broker` role to `myproject/my-cluster-kafka` service account which is used by the Kafka broker pods for Kafka rack awarenes.
  * Check also role bindings `oc get rolebindings | grep strimzi` => You should see two role bindings used for the Topic and User Operators
* Delete the Kafka cluster
  * `oc delete kafka my-cluster`
* _On your own:_
  * _Once the old Kafka cluster is deleted, you can try to deploy the cluster without Kafka rack awareness or into the `myproject2` namespace and watch the impact on the bindings._
  * _This should not need any additional RBAC changes because the regular installation is using Cluster Role Bindings_
* Once the Kafka cluster is deleted, delete the Cluster Operator
  * `oc delete -f regular-install`

## Alternative installation

* Check the differences between RBAC in `regular-install` and `alternative-rbac`
* Install the Cluster Operator
  * `oc apply -f alternative-rbac/`
* Once the Cluster Operator is installed, you can check the deployed RBAC resources
  * `oc get clusterroles | grep strimzi`
  * `oc get clusterrolebindings | grep strimzi`
  * `oc get rolebindings | grep strimzi`
  * Notice the differences from the regular installation
    * 5 Cluster Roles instead of 4
    * Only two cluster role bindings
    * Some role bindings
* Now deploy the Kafka cluster
  * `oc apply -f kafka.yaml`
* Once the Kafka cluster is deployed, you can check the deployed RBAC resources again
  * They should be the same as before => The installation had no impact on how the cluster is deployed
* Delete the Kafka cluster
  * `oc delete kafka my-cluster`
* _On your own:_
  * _Once the old Kafka cluster is deleted, you can try to deploy the cluster without Kafka rack awareness or into the `myproject2` namespace and watch the impact on the bindings._
  * _This should not need any additional RBAC changes because the regular installation is using Cluster Role Bindings_
* Once the Kafka cluster is deleted, delete the Cluster Operator
  * `oc delete -f alternative-rbac/`
* Change the Cluster operator deployment
  * Instead of watching just the `myproject` namespace, change it to watching `myproject` and `myproject2` namespace:

```yaml
        - name: STRIMZI_NAMESPACE
          value: "myproject,myproject2"
```

* Check the Cluster Operator log files
  * |The Cluster Operator doesn't work, because of missing rights for `myproject2`
* Deploy the Role Bindings also into the `myproject2` namespace
  *  `oc apply -f alternative-rbac/020-RoleBinding-strimzi-cluster-operator.yaml -n myproject2`
  *  `oc apply -f alternative-rbac/031-RoleBinding-strimzi-cluster-operator-entity-operator-delegation.yaml -n myproject2`
  *  `oc apply -f alternative-rbac/032-RoleBinding-strimzi-cluster-operator-topic-operator-delegation.yaml -n myproject2`
  * Deploy them and watch the Cluster Operator recover
* Delete the Cluster operator
  * `oc delete -f alternative-rbac/`

## 2-step installation

* Cluster Role, Cluster Role Bindings and Custom Resources require cluster-admin level priviledges to install
* Deployment and Service account can be done by project admin
* AMQ Streams installation can be done in two steps / by two people
* First, login as cluster admin and install the roles, bindings and CRDs
  * `oc login -u system:admin`
  * `oc apply -f regular-install/02-ClusterRoleBinding-strimzi-cluster-operator.yaml`
  * `oc apply -f regular-install/02-ClusterRole-strimzi-cluster-operator-role.yaml`
  * `oc apply -f regular-install/03-ClusterRoleBinding-strimzi-cluster-operator-kafka-broker-delegation.yaml`
  * `oc apply -f regular-install/03-ClusterRole-strimzi-kafka-broker.yaml`
  * `oc apply -f regular-install/04-ClusterRoleBinding-strimzi-cluster-operator-entity-operator-delegation.yaml`
  * `oc apply -f regular-install/04-ClusterRoleBinding-strimzi-cluster-operator-topic-operator-delegation.yaml`
  * `oc apply -f regular-install/04-ClusterRole-strimzi-entity-operator.yaml`
  * `oc apply -f regular-install/04-ClusterRole-strimzi-topic-operator.yaml`
  * `oc apply -f regular-install/04-Crd-kafkaconnects2i.yaml`
  * `oc apply -f regular-install/04-Crd-kafkaconnect.yaml`
  * `oc apply -f regular-install/04-Crd-kafkatopic.yaml`
  * `oc apply -f regular-install/04-Crd-kafkauser.yaml`
  * `oc apply -f regular-install/04-Crd-kafka.yaml`
* Second, login as developer and install the Service Account and deploy Cluster Operator
  * `oc login -u developer`
  * `oc apply -f regular-install/01-ServiceAccount-strimzi-cluster-operator.yaml`
  * `oc apply -f regular-install/05-Deployment-strimzi-cluster-operator.yaml`

## Assigning rights to manage AMQ Streams clusters

* Still logged as `developer` from previous chapter, try to deploy Kafka cluster:
  * `oc apply -f kafka.yaml` => You should get an error
* In the `admin` folder, you can find Cluster Role and Role Binding for setting up AMQ Streams _admin_
  * Log in as cluster admin again using `oc login -u system:admin`
  * Deploy the role and binding using `oc apply -f admin/`
* Now you can login back as `developer` and deploy Kafka cluster
  * `oc login -u developer`
  * `oc apply -f kafka.yaml` => It should now work without any problems
* Delete the Kafka cluster
  * `oc delete kafka my-cluster`
* Once the Kafka cluster is deleted, delete the Cluster Operator
  * Login as cluster admin `oc login -u system:admin`
  * `oc delete -f regular-install`

## Troubleshooting

* The installation files in the `troubleshooting` dir have been modified
  * All Cluster Role Bindings have been changed to Role Bindings to give AMQ Streams less rights
* Deploy Cluster Operator using these modified files
  * `oc apply -f troubleshooting/`
* Check that the Cluster operator is running
* Try to deploy Kafka cluster
  * `oc apply -f kafka.yaml`
  * Watch how the deployment proceeds and watch the Cluster Operator log
  * After deploying Zookeeper, the deployment will fail:

```plain
2018-08-29 19:28:53 ERROR AbstractAssemblyOperator:244 - Reconciliation #0(watch) Kafka(myproject/my-cluster): createOrUpdate failed
io.fabric8.kubernetes.client.KubernetesClientException: Got unexpected GET status code 403: Forbidden
	at io.strimzi.operator.common.operator.resource.WorkaroundRbacOperator.execute(WorkaroundRbacOperator.java:118) ~[cluster-operator-0.6.0.jar:?]
	at io.strimzi.operator.common.operator.resource.WorkaroundRbacOperator.lambda$doReconcile$0(WorkaroundRbacOperator.java:58) ~[cluster-operator-0.6.0.jar:?]
	at io.vertx.core.impl.ContextImpl.lambda$executeBlocking$1(ContextImpl.java:273) ~[cluster-operator-0.6.0.jar:?]
	at io.vertx.core.impl.TaskQueue.run(TaskQueue.java:76) ~[cluster-operator-0.6.0.jar:?]
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149) ~[?:1.8.0_181]
	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624) ~[?:1.8.0_181]
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30) [cluster-operator-0.6.0.jar:?]
	at java.lang.Thread.run(Thread.java:748) [?:1.8.0_181]
```

* To find out more about the problem, edit the Cluster Operator deployment and change the log level to `DEBUG`
  * `oc edit deployment strimzi-cluster-operator` and change the log level

```yaml
        - name: STRIMZI_LOG_LEVEL
          value: DEBUG
```

* Check the Cluster operator logs for more details. You should see more details about the error:

```plain
018-08-29 19:32:04 DEBUG ClusterRoleBindingOperator:107 - Making GET request Request{method=GET, url=https://kubernetes.default.svc/apis/rbac.authorization.k8s.io/v1beta1/clusterrolebindings/strimzi-my-cluster-kafka-init , tag=null}
2018-08-29 19:32:04 DEBUG ClusterRoleBindingOperator:110 - Got GET response Response{protocol=http/1.1, code=403, message=Forbidden, url=https://kubernetes.default.svc/apis/rbac.authorization.k8s.io/v1beta1/clusterrolebindings/strimzi-my-cluster-kafka-init }
2018-08-29 19:32:04 DEBUG AbstractAssemblyOperator:239 - Reconciliation #0(watch) Kafka(myproject/my-cluster): Lock lock::myproject::Kafka::my-cluster released
2018-08-29 19:32:04 ERROR AbstractAssemblyOperator:244 - Reconciliation #0(watch) Kafka(myproject/my-cluster): createOrUpdate failed
io.fabric8.kubernetes.client.KubernetesClientException: Got unexpected GET status code 403: Forbidden
	at io.strimzi.operator.common.operator.resource.WorkaroundRbacOperator.execute(WorkaroundRbacOperator.java:118) ~[cluster-operator-0.6.0.jar:?]
	at io.strimzi.operator.common.operator.resource.WorkaroundRbacOperator.lambda$doReconcile$0(WorkaroundRbacOperator.java:58) ~[cluster-operator-0.6.0.jar:?]
	at io.vertx.core.impl.ContextImpl.lambda$executeBlocking$1(ContextImpl.java:273) ~[cluster-operator-0.6.0.jar:?]
	at io.vertx.core.impl.TaskQueue.run(TaskQueue.java:76) ~[cluster-operator-0.6.0.jar:?]
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149) ~[?:1.8.0_181]
	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624) ~[?:1.8.0_181]
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30) [cluster-operator-0.6.0.jar:?]
	at java.lang.Thread.run(Thread.java:748) [?:1.8.0_181]
```