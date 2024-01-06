package dev.joago.exceptions;

public class IncompatibleAnnotationException extends Throwable {

    public IncompatibleAnnotationException(String annotation1, String annotation2, String optionalMessage) {
        super("Annotation %s is incompatible with %s. Please remove one of these annotations. %s".formatted(annotation1, annotation2, optionalMessage));
    }

    public IncompatibleAnnotationException(String annotation1, String annotation2) {
        super("Annotation %s is incompatible with %s. Please remove one of these annotations".formatted(annotation1, annotation2));
    }
}
