package dagger.internal.codegen;

import com.google.common.base.Optional;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

final class BindingRepository {
  private final BiMap<Key, Binding> bindingIndex;

  BindingRepository() {
    this.bindingIndex = HashBiMap.create();
  }

  Optional<Binding> getBindingForKey(Key key) {
    return Optional.fromNullable(bindingIndex.get(key));
  }

  void registerBinding(Binding binding) {
    bindingIndex.put(binding.providedKey(), binding);
  }
}
