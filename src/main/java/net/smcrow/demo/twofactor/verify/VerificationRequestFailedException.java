package net.smcrow.demo.twofactor.verify;

public class VerificationRequestFailedException extends Throwable {
    public VerificationRequestFailedException() {
        this("Failed to verify request.");
    }

    public VerificationRequestFailedException(String message) {
        super(message);
    }

    public VerificationRequestFailedException(Throwable cause) {
        super(cause);
    }
}
