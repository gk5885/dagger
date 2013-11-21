package dagger.internal.codegen;

import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import com.google.common.base.Optional;

import dagger.Factory;
import dagger.internal.ScopedProvider;


final class ProviderTyper {
  private final Types types;
  private final Elements elements;

  ProviderTyper(Types types, Elements elements) {
    this.types = types;
    this.elements = elements;
  }

  TypeMirror providerType(Key key) {
    return types.getDeclaredType(elements.getTypeElement(Provider.class.getName()),
        key.type());
  }

  TypeMirror scopedProviderType(Binding binding) {
    TypeMirror providedType = binding.providedKey().type();
    Optional<AnnotationMirror> scopeAnnotation = binding.scopeAnnotation();
    return scopeAnnotation.isPresent()
        ? types.getDeclaredType(elements.getTypeElement(ScopedProvider.class.getName()),
            providedType, scopeAnnotation.get().getAnnotationType())
        : types.getDeclaredType(elements.getTypeElement(Factory.class.getName()), providedType);
  }
}
