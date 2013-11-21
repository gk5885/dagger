package dagger.internal.codegen;

import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;

/**
 * This represents work to be done in an annotation processor that must run with other steps due to
 * shared state between them or a particular execution order.
 */
interface ProcessingStep {
  boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv);
}
