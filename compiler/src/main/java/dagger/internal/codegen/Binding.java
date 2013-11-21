package dagger.internal.codegen;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;

/**
 * A binding is a mapping from
 *
 */
final class Binding {
  private final ExecutableElement bindingElement;
  private final Key providedKey;
  private final ImmutableSet<KeyRequest> keysRequested;
  private final Optional<AnnotationMirror> scopeAnnotation;

  Binding(
      ExecutableElement bindingElement, Key providedKey, ImmutableSet<KeyRequest> keysRequested,
      Optional<AnnotationMirror> scopeAnnotation) {
    this.bindingElement = bindingElement;
    this.providedKey = providedKey;
    this.keysRequested = keysRequested;
    this.scopeAnnotation = scopeAnnotation;
  }

  ExecutableElement bindingElement() {
    return bindingElement;
  }

  Key providedKey() {
    return providedKey;
  }

  ImmutableSet<KeyRequest> keysRequests() {
    return keysRequested;
  }

  ImmutableSet<Key> requiredKeys() {
    return FluentIterable.from(keysRequested)
        .transform(new Function<KeyRequest, Key>() {
          @Override public Key apply(KeyRequest keyRequest) {
            return keyRequest.key();
          }
        })
        .toSet();
  }

  Optional<AnnotationMirror> scopeAnnotation() {
    return scopeAnnotation;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof Binding) {
      Binding that = (Binding) obj;
      return this.bindingElement.equals(that.bindingElement)
          && this.providedKey.equals(that.providedKey)
          && this.keysRequested.equals(that.keysRequested)
          && this.scopeAnnotation.equals(scopeAnnotation);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(bindingElement, providedKey, keysRequested, scopeAnnotation);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .omitNullValues()
        .add("bindingElement", bindingElement)
        .add("providedKey", providedKey)
        .add("keysRequested", keysRequested)
        .add("scopeAnnotation", scopeAnnotation.orNull())
        .toString();
  }
}
