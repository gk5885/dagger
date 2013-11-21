package dagger.internal;

import java.lang.annotation.Annotation;

import javax.inject.Provider;

import dagger.Factory;

public final class ScopedProvider<T, S extends Annotation> implements Provider<T> {
  private final Factory<T> provider;
  private volatile T instance = null;

  private ScopedProvider(Factory<T> provider) {
    assert provider != null;
    this.provider = provider;
  }

  @Override
  public T get() {
    // double-check idiom from EJ2: Item 71
    T result = instance;
    if (result == null) {
      synchronized (this) {
        result = instance;
        if (result == null) {
          instance = result = provider.get();
          if (result == null) {
            throw new NullPointerException();
          }
        }
      }
    }
    return result;
  }

  public static <T, S extends Annotation> ScopedProvider<T, S> create(Factory<T> provider) {
    if (provider == null) {
      throw new NullPointerException();
    }
    return new ScopedProvider<T, S>(provider);
  }
}
