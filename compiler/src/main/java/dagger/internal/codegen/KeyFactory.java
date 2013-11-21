package dagger.internal.codegen;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.FIELD;
import static javax.lang.model.element.ElementKind.METHOD;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import dagger.Provides;
import dagger.internal.codegen.KeyRequest.Type;

final class KeyFactory {
  private final Types types;
  private final Elements elements;

  KeyFactory(Types types, Elements elements) {
    this.types = types;
    this.elements = elements;
  }

  Key forProvidesMethod(ExecutableElement e) {
    checkNotNull(e);
    checkArgument(e.getKind().equals(METHOD));
    checkArgument(e.getAnnotation(Provides.class) != null);
    return new Key(getQualifier(e.getAnnotationMirrors()), getKeyType(e.getReturnType()));
  }

  Key forInjectorMethod(ExecutableElement e) {
    checkNotNull(e);
    checkArgument(e.getKind().equals(METHOD));
    checkArgument(e.getParameters().isEmpty());
    return new Key(getQualifier(e.getAnnotationMirrors()), getKeyType(e.getReturnType()));
  }

  Key forField(VariableElement field) {
    checkNotNull(field);
    checkArgument(field.getKind().equals(FIELD));
    return new Key(getQualifier(field.getAnnotationMirrors()), getKeyType(field.asType()));
  }

  ImmutableSet<KeyRequest> forParameters(ExecutableElement e) {
    checkNotNull(e);
    List<? extends VariableElement> parameters = e.getParameters();
    ImmutableSet.Builder<KeyRequest> builder = ImmutableSet.builder();
    for (VariableElement parameter : parameters) {
      if (providerType(parameter.asType()).isPresent()) {
        builder.add(new KeyRequest(Type.PROVIDER,
            new Key(getQualifier(parameter.getAnnotationMirrors()),
                getKeyType(parameter.asType()))));
      } else {
        builder.add(new KeyRequest(Type.INSTANCE,
            new Key(getQualifier(parameter.getAnnotationMirrors()),
                getKeyType(parameter.asType()))));
      }
      // TODO(gak): lazy
    }
    ImmutableSet<KeyRequest> result = builder.build();
    checkArgument(result.size() == parameters.size());
    return result;
  }

  Key forInjectConstructor(ExecutableElement e) {
    checkArgument(e.getKind().equals(CONSTRUCTOR));
    return new Key(Optional.<AnnotationMirror>absent(), e.getEnclosingElement().asType());
  }

  private static Optional<AnnotationMirror> getQualifier(
      List<? extends AnnotationMirror> annotations) {
    for (AnnotationMirror annotation : annotations) {
      if (annotation.getAnnotationType().asElement().getAnnotation(Qualifier.class) != null) {
        return Optional.of(annotation);
      }
    }
    return Optional.absent();
  }

  Optional<DeclaredType> providerType(TypeMirror type) {
    TypeElement providerElement = elements.getTypeElement(Provider.class.getName());
    Queue<TypeMirror> queue = new ArrayDeque<TypeMirror>();
    queue.add(type);
    TypeMirror next;
    while ((next = queue.poll()) != null) {
      if (types.asElement(next).equals(providerElement)) {
        assert next.getKind() == TypeKind.DECLARED;
        return Optional.of((DeclaredType) next);
      }
      queue.addAll(types.directSupertypes(next));
    }
    return Optional.absent();
  }

  private TypeMirror getKeyType(TypeMirror type) {
    return type.accept(new SimpleTypeVisitor6<TypeMirror, Void>() {
      @Override
      protected TypeMirror defaultAction(TypeMirror e, Void p) {
        return e;
      }

      @Override
      public TypeMirror visitDeclared(DeclaredType t, Void p) {
        Optional<DeclaredType> providerType = providerType(t);
        if (providerType.isPresent()) {
          return Iterables.getOnlyElement(providerType.get().getTypeArguments());
        } else {
          return t;
        }
      }
    }, null);
  }
}
