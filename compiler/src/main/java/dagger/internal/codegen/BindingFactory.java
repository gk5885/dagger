package dagger.internal.codegen;

import javax.inject.Scope;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

final class BindingFactory {
  private final KeyFactory keyFactory;

  BindingFactory(KeyFactory keyFactory) {
    this.keyFactory = keyFactory;
  }

  Binding forInjectConstructor(ExecutableElement constructor) {
    return new Binding(constructor, keyFactory.forInjectConstructor(constructor),
        keyFactory.forParameters(constructor), Optional.<AnnotationMirror>absent());
  }

  Binding forProviderMethod(ExecutableElement method) {
    return new Binding(method, keyFactory.forProvidesMethod(method),
        keyFactory.forParameters(method), findScopeAnnotation(method));
  }

  Binding forInjectorMethod(ExecutableElement method) {
    return new Binding(method, keyFactory.forInjectorMethod(method), ImmutableSet.<KeyRequest>of(),
        Optional.<AnnotationMirror>absent());
  }

  private static Optional<AnnotationMirror> findScopeAnnotation(Element element) {
    ImmutableList<? extends AnnotationMirror> scopeAnnotations =
        FluentIterable.from(element.getAnnotationMirrors())
            .filter(new Predicate<AnnotationMirror>() {
              @Override public boolean apply(AnnotationMirror annotationMirror) {
                Element annotationTypeElement = annotationMirror.getAnnotationType().asElement();
                return annotationTypeElement.getAnnotation(Scope.class) != null;
              }
            })
            .toList();
    switch (scopeAnnotations.size()) {
      case 0:
        return Optional.<AnnotationMirror>absent();
      case 1:
        return Optional.<AnnotationMirror>of(scopeAnnotations.get(0));
      default:
        throw new IllegalStateException();
    }
  }
}
