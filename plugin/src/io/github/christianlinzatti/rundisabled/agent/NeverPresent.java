package io.github.christianlinzatti.rundisabled.agent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that is never used on any class. Quarkus' {@code FacadeClassLoader} is told to look
 * for this annotation instead of {@code @Disabled}, so it treats every {@code @Disabled} test class
 * like a normal (Quarkus) test class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NeverPresent {
}
