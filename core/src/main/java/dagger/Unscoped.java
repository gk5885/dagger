package dagger;

import java.lang.annotation.Target;

import javax.inject.Scope;

/**
 * This scope annotation indicates that scoping is to be applied. Since all bindings are assumed
 * to be unscoped, this annotation cannot be applied - it is just a token used to indicate the
 * absence of scope.
 *
 * @author Gregory Kick
 */
@Scope
@Target({ })
public @interface Unscoped { }