package com.company.taskmanagementplatform.common.error;

/** Raised when a requested record does not exist, or is not visible to the caller. */
public class ResourceNotFoundException extends ApplicationException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }

    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException(resource + " " + id + " was not found");
    }
}
