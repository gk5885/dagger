package dagger.internal.codegen;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementKindVisitor6;

final class Factories {
  private Factories() { }

  static ClassName factoryNameForType(ClassName typeName) {
    return typeName.nameOfTopLevelClass().peerNamed(typeName.classFileName() + "$$Factory");
  }

  static ClassName factoryContainerForModule(ClassName moduleTypeName) {
    return moduleTypeName.nameOfTopLevelClass().peerNamed(
        moduleTypeName.classFileName() + "$$Factories");
  }

  static ClassName forBinding(Binding binding) {
    return binding.bindingElement().accept(
        new ElementKindVisitor6<ClassName, Void>() {
          @Override
          protected ClassName defaultAction(Element e, Void p) {
            throw new IllegalArgumentException();
          }

          /**
           * Bindings that are tied to constructors get looked up as $$Factory classes.
           */
          @Override
          public ClassName visitExecutableAsConstructor(ExecutableElement e, Void p) {
            TypeElement constructedTypeElement = Elements2.asTypeElement(e.getEnclosingElement());
            ClassName constructedName = ClassName.forTypeElement(constructedTypeElement);
            ClassName factoryName = Factories.factoryNameForType(constructedName);
            return factoryName;
          }

          /**
           * Bindings that are tied to instance methods get looked up in $$Factories containers.
           */
          @Override
          public ClassName visitExecutableAsMethod(ExecutableElement e, Void p) {
            TypeElement enclosingType = Elements2.asTypeElement(e.getEnclosingElement());
            ClassName enclosingTypeName = ClassName.forTypeElement(enclosingType);
            ClassName factoryContainerName = Factories.factoryContainerForModule(enclosingTypeName);
            ClassName factoryName = factoryContainerName.memberClassNamed(
                LOWER_CAMEL.to(UPPER_CAMEL, e.getSimpleName().toString()));
            return factoryName;
          }
        }, null);
  }
}
