
package io.github.jeannyil.beans;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Root Type for ResponseMessage
 * <p>
 * Response message
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "status",
    "error"
})
@Generated("jsonschema2pojo")
public class ResponseMessage {

    /**
     * Status
     * (Required)
     * 
     */
    @JsonProperty("status")
    @JsonPropertyDescription("Status")
    private ResponseMessage.Status status;
    /**
     * Root Type for StatusMessageType
     * <p>
     * Error message type  
     * 
     */
    @JsonProperty("error")
    @JsonPropertyDescription("Error message type  ")
    private ErrorMessageType error;

    /**
     * Status
     * (Required)
     * 
     */
    @JsonProperty("status")
    public ResponseMessage.Status getStatus() {
        return status;
    }

    /**
     * Status
     * (Required)
     * 
     */
    @JsonProperty("status")
    public void setStatus(ResponseMessage.Status status) {
        this.status = status;
    }

    /**
     * Root Type for StatusMessageType
     * <p>
     * Error message type  
     * 
     */
    @JsonProperty("error")
    public ErrorMessageType getError() {
        return error;
    }

    /**
     * Root Type for StatusMessageType
     * <p>
     * Error message type  
     * 
     */
    @JsonProperty("error")
    public void setError(ErrorMessageType error) {
        this.error = error;
    }

    public enum Status {

        OK("OK"),
        KO("KO");
        private final String value;
        private final static Map<String, ResponseMessage.Status> CONSTANTS = new HashMap<String, ResponseMessage.Status>();

        static {
            for (ResponseMessage.Status c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        private Status(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static ResponseMessage.Status fromValue(String value) {
            ResponseMessage.Status constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
