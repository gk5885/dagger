package dagger.internal.codegen;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.SimpleElementVisitor6;

final class Elements2 {
  private static final ElementVisitor<TypeElement, Void> TYPE_ELEMENT_VISITOR =
      new SimpleElementVisitor6<TypeElement, Void>() {
        protected TypeElement defaultAction(Element e, Void p) {
          throw new IllegalArgumentException();
        }

        public TypeElement visitType(TypeElement e, Void p) {
          return e;
        }
      };

  static TypeElement asTypeElement(Element e) {
    return e.accept(TYPE_ELEMENT_VISITOR, null);
  }

  private static final ElementVisitor<PackageElement, Void> PACKAGE_ELEMENT_VISITOR =
      new SimpleElementVisitor6<PackageElement, Void>() {
    protected PackageElement defaultAction(Element e, Void p) {
      throw new IllegalArgumentException();
    }

    public PackageElement visitPackage(PackageElement e, Void p) {
      return e;
    }
  };

  static PackageElement asPacakgeElement(Element e) {
    return e.accept(PACKAGE_ELEMENT_VISITOR, null);
  }

  static PackageElement getPackage(Element type) {
    while (type.getKind() != ElementKind.PACKAGE) {
      type = type.getEnclosingElement();
    }
    return (PackageElement) type;
  }

  private Elements2() { }
}
