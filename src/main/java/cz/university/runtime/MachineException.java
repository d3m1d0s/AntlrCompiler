package cz.university.runtime;

/**
 * An error raised by the running program, as opposed to a defect in the
 * machine itself. The driver reports these as plain runtime errors.
 */
public class MachineException extends RuntimeException {
    public MachineException(String message) {
        super(message);
    }

    public MachineException(String message, Throwable cause) {
        super(message, cause);
    }
}
