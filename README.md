# Camel-K-RHOAM-Webhook-Handler-Api project

This project leverages **Red Hat Integration - Camel K 1.4**.

The implemented Integration exposes the following RESTful service endpoints:
- `/webhook/amqpbridge` : 
    - Webhook ping endpoint through the `GET` HTTP method.
    - Sends RHOAM Admin/Developer Portal webhook XML event to an AMQP address (`RHOAM.WEBHOOK.EVENTS.QUEUE`) through the `POST` HTTP method.
- `/openapi.json`: returns the OpenAPI 3.0 specification for the service.
- `/q/metrics` : the _Camel Quarkus MicroProfile_ metrics

## Deploying the integration on OpenShift

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
        ```zsh
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
        ```zsh
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
        ```zsh
        watch oc get sub,csv,installPlan
        ```
        
4. Create an `allInOne` Jaeger instance.
    1. **IF NOT ALREADY INSTALLED**:
        1. Install, via OLM, the `Red Hat OpenShift distributed tracing platform` (Jaeger) operator with an `AllNamespaces` scope. :warning: Needs `cluster-admin` privileges
            ```zsh
            oc create --save-config -f - <<EOF
            apiVersion: operators.coreos.com/v1alpha1
            kind: Subscription
            metadata:
                name: jaeger-product
                namespace: openshift-operators
            spec:
                channel: stable
                installPlanApproval: Automatic
                name: jaeger-product
                source: redhat-operators
                sourceNamespace: openshift-marketplace
            EOF
            ```
        2. Verify the successful installation of the `Red Hat OpenShift distributed tracing platform` operator
            ```zsh
            watch oc get sub,csv
            ```
    2. Create the `allInOne` Jaeger instance.
        ```zsh
        oc create --save-config -f - <<EOF
        apiVersion: jaegertracing.io/v1
        kind: Jaeger
        metadata:
        name: jaeger-all-in-one-inmemory
        spec:
        allInOne:
            options:
            log-level: info
        strategy: allInOne
        EOF
        ```

5. Create the `amqpbroker-connection-secret` containing the _QUARKUS QPID JMS_ [configuration options](https://github.com/amqphub/quarkus-qpid-jms#configuration) from the [`amqpbroker.properties`](config/amqpbroker.properties) file. These options are leveraged by the _Camel Quarkus AMQP_ extension to connect to an AMQP broker. 

    :warning: _Replace values with your AMQP broker environment in the [`amqpbroker.properties`](config/amqpbroker.properties) file_.
    ```zsh
    oc create secret generic amqpbroker-connection-secret \
    --from-file=./config/amqpbroker.properties
    ``` 

6. Run the integration:
    ```zsh
    kamel run ./integrations/RhoamWebhookEventsHandlerApi.java
    ```
    You should see an output similar to the following:
    ```zsh
    Modeline options have been loaded from source files
    Full command: kamel run ./integrations/RhoamWebhookEventsHandlerApi.java --name=rhoam-webhook-events-handler-api --dependency=camel:camel-quarkus-amqp --dependency=camel:camel-quarkus-direct --open-api=resources/api/openapi.json --resource=file:resources/api/openapi.json --resource=file:resources/map/generate-api-KO-response.adm --resource=file:resources/map/generate-api-OK-response.adm --trait=prometheus.enabled=true --trait=3scale.enabled=true --trait=tracing.enabled=true --config=secret:amqpbroker-connection-secret --property=api.resources.path=file:/etc/camel/resources
    integration "rhoam-webhook-events-handler-api" created
    ```

## Testing the integration

### Pre-requisites

- [**`curl`**](https://curl.se/) or [**`HTTPie`**](https://httpie.io/) command line tools. 
- [**`HTTPie`**](https://httpie.io/) has been used in the tests.

### Testing instructions:

1. Get the OpenShift route hostname
    ```zsh
    URL="http://$(oc get route camel-quarkus-rhoam-webhook-handler-api -o jsonpath='{.spec.host}')"
    ```
2. Test the `/webhook/amqpbridge` endpoint

    - `GET /webhook/amqpbridge` :

        ```zsh
        http -v $URL/webhook/amqpbridge
        ```
        ```zsh
        [...]
        HTTP/1.1 200 OK
        [...]
        Content-Type: application/json
        [...]
        server: envoy
        [...]
        x-forwarded-port: 80
        x-forwarded-proto: http
        x-request-id: b99d5741-7781-45a5-ad8b-d74b9fe3eb78

        {
            "status": "OK"
        }
        ```

    - `POST /webhook/amqpbridge` :

        - `OK` response:

            ```zsh
            echo '<?xml version="1.0" encoding="UTF-8"?>
            <event>
            <action>updated</action>
            <type>account</type>
            <object>
                <account>
                <id>6</id>
                <created_at>2021-05-14T20:22:53Z</created_at>
                <updated_at>2021-05-14T20:22:53Z</updated_at>
                <state>approved</state>
                <org_name>TestAccount</org_name>
                <extra_fields/>
                <monthly_billing_enabled>true</monthly_billing_enabled>
                <monthly_charging_enabled>true</monthly_charging_enabled>
                <credit_card_stored>false</credit_card_stored>
                <plans>
                    <plan default="true">
                    <id>6</id>
                    <name>Default</name>
                    <type>account_plan</type>
                    <state>hidden</state>
                    <approval_required>false</approval_required>
                    <setup_fee>0.0</setup_fee>
                    <cost_per_month>0.0</cost_per_month>
                    <trial_period_days/>
                    <cancellation_period>0</cancellation_period>
                    </plan>
                </plans>
                <users>
                    <user>
                    <id>9</id>
                    <created_at>2021-05-14T20:22:53Z</created_at>
                    <updated_at>2021-05-14T20:22:53Z</updated_at>
                    <account_id>6</account_id>
                    <state>pending</state>
                    <role>admin</role>
                    <username>admin</username>
                    <email>admin@acme.org</email>
                    <extra_fields/>
                    </user>
                </users>
                </account>
            </object>
            </event>' | http -v POST $URL/webhook/amqpbridge content-type:'application/xml'
            ```
            ```zsh
            [...]
            HTTP/1.1 200 OK
            Access-Control-Allow-Headers: Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers
            Access-Control-Allow-Methods: GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS, CONNECT, PATCH
            Access-Control-Allow-Origin: *
            Access-Control-Max-Age: 3600
            Content-Type: application/json
            RHOAM_EVENT_ACTION: updated
            RHOAM_EVENT_TYPE: account
            Set-Cookie: 0d5acfcb0ca2b6f2520831b8d4bd4031=f3580c9af577adb49be04813506f5ec6; path=/; HttpOnly
            breadcrumbId: 43EB8F0221CD24E-0000000000000002
            transfer-encoding: chunked

            {
                "status": "OK"
            }
            ```

3. Test the `/openapi.json` endpoint
    ```zsh
    http -v $URL/openapi.json
    ```
    ```zsh
    [...]
    HTTP/1.1 200 OK
    Accept: */*
    [...]
    Content-Type: application/vnd.oai.openapi+json
    [...]
    breadcrumbId: DFB5B53061B9578-0000000000000002
    transfer-encoding: chunked

    {
        "components": {
            "schemas": {
                "ErrorMessageType": {
                    "description": "Error message type  ",
    [...]
        "info": {
        "contact": {
            "name": "Jean Nyilimbibi"
        },
        "description": "API that handles RHOAM Admin/Developer Portals webhook events",
        "license": {
            "name": "MIT License",
            "url": "https://opensource.org/licenses/MIT"
        },
        "title": "RHOAM Webhook Events Handler API",
        "version": "1.0.0"
    },
    "openapi": "3.0.2",
    [...]
    },
        "servers": [
            {
                "description": "API Backend URL",
                "url": "http://rhoam-webhook-events-handler-api.apps.jeannyil.sandbox1789.opentlc.com"
            }
        ]
    }
    ```
4. Test the `/q/metrics` endpoint
    ```zsh
    http -v $URL/q/metrics
    ```
    ```zsh
    [...]
    HTTP/1.1 200 OK
    Set-Cookie: afac851ba5d373e8e02a0326002ffd7c=bd72161e796b12be5de494520b6174c6; path=/; HttpOnly
    cache-control: private
    content-length: 28449
    [...]
    # HELP application_camel_context_exchanges_total The total number of exchanges for a route or Camel Context
    # TYPE application_camel_context_exchanges_total counter
    application_camel_context_exchanges_total{camelContext="camel-1"} 6.0
    [...]
    # HELP application_camel_route_count The count of routes.
    # TYPE application_camel_route_count gauge
    application_camel_route_count{camelContext="camel-1"} 6.0
    # HELP application_camel_route_exchanges_completed_total The total number of completed exchanges for a route or Camel Context
    # TYPE application_camel_route_exchanges_completed_total counter
    application_camel_route_exchanges_completed_total{camelContext="camel-1",routeId="generate-error-response-route"} 0.0
    application_camel_route_exchanges_completed_total{camelContext="camel-1",routeId="get-openapi-spec-route"} 1.0
    application_camel_route_exchanges_completed_total{camelContext="camel-1",routeId="ping-webhook-route"} 1.0
    application_camel_route_exchanges_completed_total{camelContext="camel-1",routeId="pingWebhook"} 1.0
    application_camel_route_exchanges_completed_total{camelContext="camel-1",routeId="send-to-amqp-queue-route"} 4.0
    application_camel_route_exchanges_completed_total{camelContext="camel-1",routeId="sendToAMQPQueue"} 4.0
    [...]
    # HELP application_camel_route_exchanges_total The total number of exchanges for a route or Camel Context
    # TYPE application_camel_route_exchanges_total counter
    application_camel_route_exchanges_total{camelContext="camel-1",routeId="generate-error-response-route"} 0.0
    application_camel_route_exchanges_total{camelContext="camel-1",routeId="get-openapi-spec-route"} 1.0
    application_camel_route_exchanges_total{camelContext="camel-1",routeId="ping-webhook-route"} 1.0
    application_camel_route_exchanges_total{camelContext="camel-1",routeId="pingWebhook"} 1.0
    application_camel_route_exchanges_total{camelContext="camel-1",routeId="send-to-amqp-queue-route"} 4.0
    application_camel_route_exchanges_total{camelContext="camel-1",routeId="sendToAMQPQueue"} 4.0
    [...]
    ```