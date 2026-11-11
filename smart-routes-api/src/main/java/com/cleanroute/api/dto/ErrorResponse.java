package com.cleanroute.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.ZonedDateTime;

/**
 * The JSON body of every error: {status, error, message, timestamp}. Coach 503s also carry
 * {@code code} (budget, quota_exhausted, time_budget or provider_error) so a client can tell them
 * apart without reading the message; it is left out everywhere else.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private String timestamp;
    private String code;

    public ErrorResponse() {}

    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, null);
    }

    public ErrorResponse(int status, String error, String message, String code) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.timestamp = ZonedDateTime.now().toString();
        this.code = code;
    }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
