package dagger;

import javax.inject.Provider;

/**
 * An unscoped provider.
 *
 * @author Gregory Kick
 */
public interface Factory<T> extends Provider<T> { }
