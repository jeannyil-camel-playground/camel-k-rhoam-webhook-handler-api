# Camel-K-RHOAM-Webhook-Handler-Api project

This project leverages **Red Hat Integration - Camel K 1.4**.

## NOTE
- [`Jitpack`](https://jitpack.io/) is used to package the [_`message-models`_](./message-models) project into a shared JAR that will be used by the [_`RhoamWebhookEventsHandlerApi`_](integrations/RhoamWebhookEventsHandlerApi.java) integration. 
- This configuration is handy but experimental and it may change in future versions. **In a production scenario, the model JAR should be deployed into your own Maven registry and be referenced in the platform configuration**.

:construction: *_INSTRUCTIONS_TO_COMPLETE_*

1. Login to the OpenShift cluster
    ```zsh
    oc login ...
    ```
2. Create an OpenShift project or use your existing OpenShift project. For instance, to create `camel-quarkus`
    ```zsh
    oc new-project camel-k-integrations --display-name="Red Hat Integration - Camel K integrations"
    ```
3. Install, via OLM, the `Red Hat Integration Camel-K` operator in the `camel-k-integrations` namespace

    1. Create the `camel-k-integrations` operator group
        ```
        oc create --save-config -f - <<EOF
        apiVersion: operators.coreos.com/v1
        kind: OperatorGroup
        metadata:
            name: camel-k-integrations-operatorgroup
            namespace: camel-k-integrations
        spec:
            targetNamespaces:
            - camel-k-integrations
        EOF
        ```
    2. Create the `Red Hat Integration - Camel-K` subscription 
        ```
        oc create --save-config -f - <<EOF
        apiVersion: operators.coreos.com/v1alpha1
        kind: Subscription
        metadata:
            name: red-hat-camel-k
            namespace: camel-k-integrations
        spec:
            channel: 1.4.x
            installPlanApproval: Automatic
            name: red-hat-camel-k
            source: redhat-operators
            sourceNamespace: openshift-marketplace
        EOF
        ```
    3. Verify the successful installation of the `Red Hat Integration - Camel-K` operator
        ```
        watch oc get sub,csv,installPlan
        ```
4. Create the `amqpbroker-connection-secret` containing the _QUARKUS QPID JMS_ [configuration options](https://github.com/amqphub/quarkus-qpid-jms#configuration) from the [`amqpbroker.properties`](config/amqpbroker.properties) file. These options are leveraged by the _Camel Quarkus AMQP_ extension to connect to an AMQP broker. 

    :warning: _Replace values with your AMQP broker environment in the [`amqpbroker.properties`](config/amqpbroker.properties) file_.
    ```zsh
    oc create secret generic amqpbroker-connection-secret \
    --from-file=./config/amqpbroker.properties
    ``` 
5. Build the `message-models` jar
    ```
    mvn clean package -f ./message-models/pom.xml
    ```
6. Run the integration:
    ```
    kamel run -t jvm.classpath=message-models/target/message-models-1.0.0.jar ./integrations/RhoamWebhookEventsHandlerApi.java
    ```