package com.company.taskmanagementplatform.common.error;

/**
 * Raised when the request was fine but something the platform depends on was not.
 *
 * <p>Answers 503. {@link ErrorCode#SERVICE_UNAVAILABLE} has existed since the foundation phase with nothing
 * raising it; the malware scanner is the first dependency whose absence has to fail a request rather than be
 * worked around, so this is what raises it.
 *
 * <p>503 rather than 500, and the difference is not cosmetic. A 500 says the application is broken and
 * retrying is pointless; a 503 says this attempt could not be completed and the same request may well
 * succeed shortly. For an upload that could not be scanned, the second is true and the first is not.
 *
 * <p>The message must say what the caller can do and nothing about the internals. Which dependency was
 * unavailable is logged, not returned.
 */
public class ServiceUnavailableException extends ApplicationException {

    public ServiceUnavailableException(String message) {
        super(ErrorCode.SERVICE_UNAVAILABLE, message);
    }
}
