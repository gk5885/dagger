package dagger;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;


/**
 * This annotation indicates that an interface is a valid <i>component interface</i> and that
 * Dagger is to generate an implementation.
 *
 * @author Gregory Kick
 */
@Target(ElementType.TYPE)
public @interface Component {
  Class<? extends Annotation> scope() default Unscoped.class;
  Class<?>[] modules() default { };
  Class<?>[] dependencies() default { };
}
