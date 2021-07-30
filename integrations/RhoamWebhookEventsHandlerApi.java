// camel-k: language=java 
// camel-k: dependency=mvn:javax.ws.rs:javax.ws.rs-api:2.1.1.redhat-00002
// camel-k: dependency=github:jeannyil-camel-k-playground:camel-k-rhoam-webhook-handler-api:main-SNAPSHOT
// camel-k: trait=prometheus.enabled=true trait=3scale.enabled=true trait=tracing.auto=true 
// camel-k: resource=../resources/openapi.json

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.TypeConversionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;

import io.github.jeannyil.beans.ErrorMessageType;
import io.github.jeannyil.beans.ResponseMessage;

// Exposes the RHOAM Webhook Events Handler API
public class RhoamWebhookEventsHandlerApi extends RouteBuilder {

  private static String logName = RhoamWebhookEventsHandlerApi.class.getName();

  public static final String DIRECT_GENERATE_ERROR_MESSAGE_ENDPOINT = "direct:generateErrorResponse";
	public static final String DIRECT_SEND_TO_AMQP_QUEUE_ENDPOINT = "direct:sendToAMQPQueue";
	public static final String DIRECT_PING_WEBHOOK_ENDPOINT = "direct:pingWebhook";
  public static final String KNATIVE_CE_RHOAM_EVENT_ENDPOINT = "knative:event/rhoam.event";
  
  @Override
  public void configure() throws Exception {

    /**
		 * Catch unexpected exceptions
		 */
		onException()
      .handled(true)
      .maximumRedeliveries(0)
      .log(LoggingLevel.ERROR, logName, ">>> ${routeId} - Caught exception: ${exception.stacktrace}").id("log-unexpected")
      .to(DIRECT_GENERATE_ERROR_MESSAGE_ENDPOINT).id("generate-500-errorresponse")
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
      .setProperty(Exchange.HTTP_RESPONSE_TEXT, constant(Response.Status.BAD_REQUEST.getReasonPhrase())) // 400 Http Code Text
      .to(DIRECT_GENERATE_ERROR_MESSAGE_ENDPOINT).id("generate-400-errorresponse")
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - OUT: headers:[${headers}] - body:[${body}]").id("log400-response")
    ;

    /**
		 * REST configuration with Camel Quarkus Platform HTTP component
		 */
		restConfiguration()
      .component("platform-http")
      .enableCORS(true)
      .bindingMode(RestBindingMode.off) // RESTful responses will be explicitly marshaled for logging purposes
      .dataFormatProperty("prettyPrint", "true")
      .scheme("http")
      .host("0.0.0.0")
      .port("8080")
      .contextPath("/")
      .clientRequestValidation(true)
    ;

    /**
		 * REST endpoint for the Service OpenAPI document 
		 */
		rest().id("openapi-document-restapi")
      .produces(MediaType.APPLICATION_JSON)
      
      // Gets the OpenAPI document for this service
      .get("/openapi.json")
        .id("get-openapi-spec-route")
        .description("Gets the OpenAPI document for this service in JSON format")
        .route()
          .log(LoggingLevel.INFO, logName, ">>> ${routeId} - IN: headers:[${headers}] - body:[${body}]").id("log-openapi-doc-request")
          .setHeader(Exchange.CONTENT_TYPE, constant("application/vnd.oai.openapi+json")).id("set-content-type")
          .setBody()
            .constant("resource:classpath:resources/openapi.json")
            .id("setBody-for-openapi-document")
          .log(LoggingLevel.INFO, logName, ">>> ${routeId} - OUT: headers:[${headers}] - body:[${body}]").id("log-openapi-doc-response")
        .end()
    ;
  
    /**
     * REST endpoint for the RHOAM Webhook Events Handler API
     */
    rest().id("rhoam-webhook-events-handler-api")
        
      // Handles RHOAM webhook ping
      .get("/webhook/amqpbridge")
        .id("webhook-amqpbridge-ping-route")
        .description("Handles RHOAM webhook ping")
        .produces(MediaType.APPLICATION_JSON)
        .responseMessage()
          .code(Response.Status.OK.getStatusCode())
          .message(Response.Status.OK.getReasonPhrase())
          .responseModel(ResponseMessage.class)
        .endResponseMessage()
        // Call the WebhookPingRoute
        .to(DIRECT_PING_WEBHOOK_ENDPOINT)
      
      // Handles the RHOAM Admin/Developer Portal webhook event and sends it to an AMQP queue
      .post("/webhook/amqpbridge")
        .id("webhook-amqpbridge-handler-route")
        .consumes(MediaType.WILDCARD)
        .produces(MediaType.APPLICATION_JSON)
        .description("Sends RHOAM Admin/Developer Portal webhook event to an AMQP queue")
        .param()
          .name("body")
          .type(RestParamType.body)
          .description("RHOAM Admin/Developer Portal XML event")
          .dataType("string")
          .required(true)
        .endParam()
        .responseMessage()
          .code(Response.Status.OK.getStatusCode())
          .message(Response.Status.OK.getReasonPhrase())
          .responseModel(ResponseMessage.class)
        .endResponseMessage()
        .responseMessage()
          .code(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())
          .message(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())
          .responseModel(ResponseMessage.class)
        .endResponseMessage()
        // call the SendToAMQPQueueRoute
        .to(DIRECT_SEND_TO_AMQP_QUEUE_ENDPOINT)

    ;

    // Route that handles the webhook ping
    from(DIRECT_PING_WEBHOOK_ENDPOINT)
			.routeId("ping-webhook-route")
			.setBody()
				.method("responseMessageHelper", "generateOKResponseMessage()")
				.id("set-pingOK-reponseMessage")
      .end()
      .marshal().json(JsonLibrary.Jackson, true).id("marshal-pingOK-responseMessage-to-json")
		;

    // Route that sends RHOAM Admin/Developer Portal webhook event to the Knative broker as a Cloud Event
    // A kamelet will pickup the cloud event and send the event message to an external AMQP broker 
    from(DIRECT_SEND_TO_AMQP_QUEUE_ENDPOINT)
      .routeId("send-to-knative-broker-route")
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - RHOAM Admin/Developer Portal received event: in.headers[${headers}] - in.body[${body}]")
      .removeHeaders("*", "breadcrumbId")
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - Sending it as a Cloud Event to the knative broker...")
      .to(ExchangePattern.InOnly, KNATIVE_CE_RHOAM_EVENT_ENDPOINT)
			.setBody()
				.method("responseMessageHelper", "generateOKResponseMessage()")
				.id("set-OK-reponseMessage")
      .end()
      .marshal().json(JsonLibrary.Jackson, true).id("marshal-OK-responseMessage-to-json")
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
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - IN: headers:[${headers}] - body:[${body}]").id("log-errormessage-request")
      .filter(simple("${in.header.CamelHttpResponseCode} == null")) // Defaults to 500 HTTP Code
        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())).id("set-500-http-code")
        .setHeader(Exchange.HTTP_RESPONSE_TEXT, constant(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())).id("set-500-http-reason")
      .end() // end filter
      .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_JSON)).id("set-json-content-type")
      .setBody()
        .method("responseMessageHelper", 
            "generateKOResponseMessage(${headers.CamelHttpResponseCode}, ${headers.CamelHttpResponseText}, ${exception})")
        .id("set-errorresponse-object")
      .end()
      .marshal().json(JsonLibrary.Jackson, true).id("marshal-errorresponse-to-json")
      .convertBodyTo(String.class).id("convert-errorresponse-to-string")
      .log(LoggingLevel.INFO, logName, ">>> ${routeId} - OUT: headers:[${headers}] - body:[${body}]").id("log-errorresponse")
    ;

  }

  /**
   * 
   * Response Message helper bean 
   *
   */
  @BindToRegistry("responseMessageHelper")
  public static class ResponseMessageHelper {
    
    /**
     * Generates an OK ResponseMessage
     * @return OK ResponseMessage
     */
    public ResponseMessage generateOKResponseMessage() {
      ResponseMessage responseMessage = new ResponseMessage();
      responseMessage.setStatus(ResponseMessage.Status.OK);
      return responseMessage;
    }
    
    /**
     * Generates a KO ResponseMessage
     * @param erroCode
     * @param errorDescription
     * @param errorMessage
     * @return KO ResponseMessage
     */
    public ResponseMessage generateKOResponseMessage(String errorCode, String errorDescription, String errorMessage) {
      ResponseMessage responseMessage = new ResponseMessage();
      ErrorMessageType errorMessageType = new ErrorMessageType();
      errorMessageType.setCode(errorCode);
      errorMessageType.setDescription(errorDescription);
      errorMessageType.setMessage(errorMessage);

      responseMessage.setStatus(ResponseMessage.Status.KO);
      responseMessage.setError(errorMessageType);
      
      return responseMessage;
    }

  }

}
