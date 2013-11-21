package dagger.internal.codegen;

import com.google.common.base.Objects;

final class KeyRequest {
  enum Type {
    INSTANCE,
    PROVIDER,
    LAZY,
  }

  private final Type type;
  private final Key key;

  KeyRequest(Type type, Key key) {
    this.type = type;
    this.key = key;
  }

  Key key() {
    return key;
  }

  Type type() {
    return type;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof KeyRequest) {
      KeyRequest that = (KeyRequest) obj;
      return this.type.equals(that.type)
          && this.key.equals(that.key);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(type, key);
  }
}
