package dagger.internal.codegen;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;

import java.util.Iterator;

import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

import com.google.common.base.Equivalence;
import com.google.common.base.Objects;
import com.google.common.base.Optional;

public class Key {
  private final Optional<AnnotationMirror> qualifier;
  private final TypeMirror type;
  private final Equivalence.Wrapper<TypeMirror> typeWrapper;

  Key(Optional<AnnotationMirror> qualifier, TypeMirror type) {
    assert qualifier != null;
    this.qualifier = qualifier;
    assert type != null;
    this.type = type;
    this.typeWrapper = Mirrors.equivalence().wrap(type);
  }

  Optional<AnnotationMirror> qualifier() {
    return qualifier;
  }

  TypeMirror providerType(Elements elements, Types types) {
    return types.getDeclaredType(elements.getTypeElement(Provider.class.getName()), type);
  }

  TypeMirror type() {
    return type;
  }

  String suggestedIdentifier() {
    StringBuilder builder = new StringBuilder();
    if (qualifier.isPresent()) {
      builder.append(qualifier.get().getAnnotationType().asElement().getSimpleName());
      // TODO(gak): this is insufficient
    }
    type.accept(new SimpleTypeVisitor6<Void, StringBuilder>() {
      @Override
      public Void visitDeclared(DeclaredType t, StringBuilder builder) {
        builder.append(t.asElement().getSimpleName());
        Iterator<? extends TypeMirror> argumentIterator = t.getTypeArguments().iterator();
        if (argumentIterator.hasNext()) {
          builder.append("Of");
          TypeMirror first = argumentIterator.next();
          first.accept(this, builder);
          while (argumentIterator.hasNext()) {
            builder.append("And");
            argumentIterator.next().accept(this, builder);
          }
        }
        return null;
      }
    }, builder);
    return UPPER_CAMEL.to(LOWER_CAMEL, builder.toString());
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof Key) {
      Key that = (Key) obj;
      return this.typeWrapper.equals(that.typeWrapper)
          && this.qualifier.equals(that.qualifier);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(typeWrapper, qualifier);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .omitNullValues()
        .add("qualifier", qualifier.orNull())
        .add("type", type)
        .toString();
  }
}
