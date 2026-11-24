package org.junit.jupiter.api;

import java.lang.annotation.*;

/** Minimal stand-in for JUnit's @Disabled (only for the self test). */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface Disabled {
	String value() default "";
}
