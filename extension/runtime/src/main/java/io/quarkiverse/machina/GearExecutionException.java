package io.quarkiverse.machina;

public class GearExecutionException extends RuntimeException {
    public GearExecutionException(Throwable cause) {
        super(cause);
    }

    public GearExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public GearExecutionException(String message) {
        super(message);
    }
}
