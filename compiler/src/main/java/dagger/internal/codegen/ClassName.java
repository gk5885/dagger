package dagger.internal.codegen;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.NestingKind.MEMBER;
import static javax.lang.model.element.NestingKind.TOP_LEVEL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import com.google.common.base.CaseFormat;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * Represents a fully-qualified class name for {@link NestingKind#TOP_LEVEL} and
 * {@link NestingKind#MEMBER} classes.
 */
final class ClassName {
  private final String packageName;
  /* From top to bottom.  E.g.: this field will contian ["A", "B"] for pgk.A.B.C */
  private final ImmutableList<String> enclosingSimpleNames;
  private final String simpleName;

  ClassName(String packageName, ImmutableList<String> enclosingSimpleNames, String simpleName) {
    this.packageName = packageName;
    this.enclosingSimpleNames = enclosingSimpleNames;
    this.simpleName = simpleName;
  }

  String fullyQualifiedName() {
    StringBuilder builder = new StringBuilder(packageName);
    if (builder.length() > 0) {
      builder.append('.');
    }
    for (String enclosingSimpleName : enclosingSimpleNames) {
      builder.append(enclosingSimpleName).append('.');
    }
    return builder.append(simpleName).toString();
  }

  String classFileName() {
    StringBuilder builder = new StringBuilder();
    Joiner.on('$').appendTo(builder, enclosingSimpleNames);
    if (!enclosingSimpleNames.isEmpty()) {
      builder.append('$');
    }
    return builder.append(simpleName).toString();
  }

  String simpleName() {
    return simpleName;
  }

  String packageName() {
    return packageName;
  }

  String suggestedVariableName() {
    return CharMatcher.is('$').removeFrom(
        CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, simpleName));
  }

  ClassName nameOfTopLevelClass() {
    Iterator<String> enclosingIterator = enclosingSimpleNames.iterator();
    return enclosingIterator.hasNext()
        ? new ClassName(packageName, ImmutableList.<String>of(), enclosingIterator.next())
        : this;
  }

  ClassName memberClassNamed(String memberClassName) {
    return new ClassName(packageName,
        new ImmutableList.Builder<String>().addAll(enclosingSimpleNames).add(simpleName).build(),
        memberClassName);
  }

  ClassName peerNamed(String peerClassName) {
    return new ClassName(packageName, enclosingSimpleNames, peerClassName);
  }

  private static final ImmutableSet<NestingKind> ACCEPTABLE_NESTING_KINDS =
      Sets.immutableEnumSet(TOP_LEVEL, MEMBER);

  static ClassName forTypeElement(TypeElement element) {
    checkNotNull(element);
    checkArgument(ACCEPTABLE_NESTING_KINDS.contains(element.getNestingKind()));
    String simpleName = element.getSimpleName().toString();
    List<String> reverseEnclosingNames = new ArrayList<String>();
    Element current = element.getEnclosingElement();
    while (current.getKind().equals(CLASS)) {
      checkArgument(ACCEPTABLE_NESTING_KINDS.contains(element.getNestingKind()));
      reverseEnclosingNames.add(current.getSimpleName().toString());
      current = element.getEnclosingElement();
    }
    PackageElement packageElement = Elements2.asPacakgeElement(current);
    Collections.reverse(reverseEnclosingNames);
    return new ClassName(packageElement.getQualifiedName().toString(),
        ImmutableList.copyOf(reverseEnclosingNames), simpleName);
  }

  @Override
  public String toString() {
    return fullyQualifiedName();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof ClassName) {
      ClassName that = (ClassName) obj;
      return this.packageName.equals(that.packageName)
          && this.enclosingSimpleNames.equals(that.enclosingSimpleNames)
          && this.simpleName.equals(that.simpleName);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(packageName, enclosingSimpleNames, simpleName);
  }
}
