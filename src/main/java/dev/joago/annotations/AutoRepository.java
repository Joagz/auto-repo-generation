package dev.joago.annotations;

import dev.joago.enums.PrimaryKeyTypes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface AutoRepository {
    String databaseName();
    PrimaryKeyTypes primaryKeyType() default PrimaryKeyTypes.INTEGER;
}
