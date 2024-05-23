package com.elhg.retryexample;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;

public interface BackendAdapter {

    @Retryable(retryFor = {RemoteServiceNotAvailableException.class},
            maxAttempts = 10,
            backoff = @Backoff(delay = 1000))
    public String getBackendResponse(String param1, String param2);

    @Recover
    public String getBackendResponseFallback(RemoteServiceNotAvailableException e, String param1, String param2);
}
