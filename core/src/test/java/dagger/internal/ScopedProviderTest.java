package dagger.internal;

import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import org.junit.Test;

import dagger.Factory;

public class ScopedProviderTest {
  @Test public void create_nullPointerException() {
    try {
      ScopedProvider.create(null);
      fail();
    } catch (NullPointerException expected) { }
  }

  @Test public void get_nullPointerException() {
    ScopedProvider<Object, ?> scopedProvider = ScopedProvider.create(new Factory<Object>() {
      @Override public Object get() {
        return null;
      }
    });
    try {
      scopedProvider.get();
      fail();
    } catch (NullPointerException expected) { }
  }

  @Test public void get() {
    ScopedProvider<Integer, ?> scopedProvider = ScopedProvider.create(new Factory<Integer>() {
      int i = 0;

      @Override public Integer get() {
        return i++;
      }
    });
    ASSERT.that(scopedProvider.get()).is(0);
    ASSERT.that(scopedProvider.get()).is(0);
    ASSERT.that(scopedProvider.get()).is(0);
  }
}
