// camel-k: language=java
// camel-k: name=rhoam-webhook-events-handler-api
// camel-k: dependency=camel:camel-quarkus-amqp 
// camel-k: dependency=camel:camel-quarkus-direct
// camel-k: open-api=../resources/api/openapi.json
// camel-k: resource=file:../resources/api/openapi.json
// camel-k: resource=file:../resources/map/generate-api-KO-response.adm
// camel-k: resource=file:../resources/map/generate-api-OK-response.adm
// camel-k: trait=prometheus.enabled=true trait=3scale.enabled=true trait=tracing.enabled=true
// camel-k: config=secret:amqpbroker-connection-secret
// camel-k: property=api.resources.path=file:/etc/camel/resources

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.TypeConversionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

// Exposes the RHOAM Webhook Events Handler API
public class RhoamWebhookEventsHandlerApi extends RouteBuilder {

  private static String logName = RhoamWebhookEventsHandlerApi.class.getName();

  public static final String DIRECT_GENERATE_ERROR_MESSAGE_ENDPOINT = "direct:generateErrorResponse";
	public static final String DIRECT_SEND_TO_AMQP_QUEUE_ENDPOINT = "direct:sendToAMQPQueue";
	public static final String DIRECT_PING_WEBHOOK_ENDPOINT = "direct:pingWebhook";
  public static final String AMQP_QUEUE_RHOAM_EVENT_ENDPOINT = "amqp:queue:RHOAM.WEBHOOK.EVENTS.QUEUE";
  
  @Override
  public void configure() throws Exception {

    /**
		 * Catch unexpected exceptions
		 */
		onException(java.lang.Exception.class)
      .handled(true)
      .maximumRedeliveries(0)
      .log(LoggingLevel.ERROR, logName, ">>> ${routeId} - Caught exception: ${exception.stacktrace}").id("log-unexpected")
      .to(DIRECT_GENERATE_ERROR_MESSAGE_ENDPOINT)
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - OUT: headers:[${headers}] - body:[${body}]").id("log-unexpected-response")
    ;

    /**
		 * Catch TypeConversionException exceptions
		 */
		onException(TypeConversionException.class)
      .handled(true)
      .maximumRedeliveries(0)
      .log(LoggingLevel.ERROR, logName, ">>> ${routeId} - Caught TypeConversionException: ${exception.stacktrace}").id("log-400")
      .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(Response.Status.BAD_REQUEST.getStatusCode())) // 400 Http Code
      .setHeader(Exchange.HTTP_RESPONSE_TEXT, constant(Response.Status.BAD_REQUEST.getReasonPhrase())) // 400 Http Code Text
      .to(DIRECT_GENERATE_ERROR_MESSAGE_ENDPOINT)
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - OUT: headers:[${headers}] - body:[${body}]")
    ;

    /**
		 * REST endpoint for the Service OpenAPI document 
		 */
		rest().id("openapi-document-restapi")
      .produces(MediaType.APPLICATION_JSON)
      // Returns the OpenAPI document for this service
      .get("/openapi.json")
        .id("get-openapi-spec-route")
        .route()
          .log(LoggingLevel.INFO, logName, ">>> ${routeId} - IN: headers:[${headers}] - body:[${body}]")
          .setHeader(Exchange.CONTENT_TYPE, constant("application/vnd.oai.openapi+json"))
          .setBody()
            .constant("resource:{{api.resources.path}}/openapi.json")
          .log(LoggingLevel.INFO, logName, ">>> ${routeId} - OUT: headers:[${headers}] - body:[${body}]")
        .end()
    ;
  
    
    // Route that handles the webhook ping
    from(DIRECT_PING_WEBHOOK_ENDPOINT)
			.routeId("ping-webhook-route")
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - Received a ping event: in.headers[${headers}] - in.body[${body}]")
			// Generate the error response message using atlasmap
      .to("atlasmap:{{api.resources.path}}/generate-api-OK-response.adm")
      .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_JSON))
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - pingWebhook response: headers:[${headers}] - body:[${body}]")
		;

    // Route that sends RHOAM Admin/Developer Portal webhook event to an AMQP broker 
    from(DIRECT_SEND_TO_AMQP_QUEUE_ENDPOINT)
      .routeId("send-to-amqp-queue-route")
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - RHOAM Admin/Developer Portal received event: in.headers[${headers}] - in.body[${body}]")
      .removeHeaders("*", "breadcrumbId")
      // .setHeader("RHOAM_EVENT_TYPE").xpath("//event/type", String.class)
      // .setHeader("RHOAM_EVENT_ACTION").xpath("//event/action", String.class)
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - Sending to RHOAM.WEBHOOK.EVENTS.QUEUE AMQP address...")
      .to(ExchangePattern.InOnly, AMQP_QUEUE_RHOAM_EVENT_ENDPOINT)
      // Generate the error response message using atlasmap
      .to("atlasmap:{{api.resources.path}}/generate-api-OK-response.adm")
      .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_JSON))
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - sendToAMQPQueue response: headers:[${headers}] - body:[${body}]")
    ;

    /**
		 * Route that returns the error response message in JSON format
		 * The following properties are expected to be set on the incoming Camel Exchange Message if customization is needed:
		 * <br>- CamelHttpResponseCode ({@link org.apache.camel.Exchange#HTTP_RESPONSE_CODE})
		 * <br>- CamelHttpResponseText ({@link org.apache.camel.Exchange#HTTP_RESPONSE_TEXT})
		 */
		from(DIRECT_GENERATE_ERROR_MESSAGE_ENDPOINT)
      .routeId("generate-error-response-route")
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - IN: headers:[${headers}] - body:[${body}]")
      .filter(simple("${in.header.CamelHttpResponseCode} == null")) // Defaults to 500 HTTP Code
        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()))
        .setHeader(Exchange.HTTP_RESPONSE_TEXT, constant(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase()))
      .end() // end filter
      .setHeader("errorMessage", simple("${exception}"))
      // Generate the error response message using atlasmap
      .to("atlasmap:{{api.resources.path}}/generate-api-KO-response.adm")
      .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_JSON))
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - OUT: headers:[${headers}] - body:[${body}]")
    ;

  }

}