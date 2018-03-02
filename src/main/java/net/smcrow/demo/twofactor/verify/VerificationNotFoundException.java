package net.smcrow.demo.twofactor.verify;

public class VerificationNotFoundException extends Throwable {
    public VerificationNotFoundException() {
        this("Failed to find verification.");
    }

    public VerificationNotFoundException(String message) {
        super(message);
    }
}
