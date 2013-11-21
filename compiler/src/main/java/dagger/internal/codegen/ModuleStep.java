package dagger.internal.codegen;

import static com.google.common.base.Preconditions.checkState;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javawriter.JavaWriter;

import dagger.Factory;
import dagger.Module;
import dagger.Provides;

public class ModuleStep implements ProcessingStep {
  private Elements elements;
  private Types types;
  private Filer filer;
  private final BindingRepository factoryRepository;
  private final KeyFactory keyFactory;
  private final BindingFactory bindingFactory;

  ModuleStep(ProcessingEnvironment processingEnv, BindingRepository factoryRepository) {
    this.elements = processingEnv.getElementUtils();
    this.types = processingEnv.getTypeUtils();
    this.filer = processingEnv.getFiler();
    this.factoryRepository = factoryRepository;
    this.keyFactory = new KeyFactory(types, elements);
    this.bindingFactory = new BindingFactory(keyFactory);
  }


  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Set<? extends Element> moduleElements = roundEnv.getElementsAnnotatedWith(Module.class);
    for (Element moduleElement : moduleElements) {
      ImmutableSet.Builder<Binding> providesBindings = ImmutableSet.builder();
      for (Element methodElement : ElementFilter.methodsIn(moduleElement.getEnclosedElements())) {
        if (methodElement.getAnnotation(Provides.class) != null) {
          checkState(methodElement.getKind().equals(METHOD));
          ExecutableElement providesMethodElement = (ExecutableElement) methodElement;
          providesBindings.add(bindingFactory.forProviderMethod(providesMethodElement));
        }
      }
      TypeElement moduleTypeElement = Elements2.asTypeElement(moduleElement);
      // temporarily bail on nested classes
      if (moduleTypeElement.getNestingKind().equals(NestingKind.TOP_LEVEL)) {
        ImmutableSet<Binding> factories =
            writeFactories(moduleTypeElement, providesBindings.build());
        for (Binding binding : factories) {
          factoryRepository.registerBinding(binding);
        }
      }
    }
    return false;
  }

  ImmutableSet<Binding> writeFactories(TypeElement moduleElement,
      ImmutableSet<Binding> providesBindings) {
    ClassName moduleName = ClassName.forTypeElement(moduleElement);
    ClassName factoriesName = Factories.factoryContainerForModule(moduleName);
    ImmutableSet.Builder<Binding> bindings = ImmutableSet.builder();
    try {
      JavaFileObject sourceFile = filer.createSourceFile(
          factoriesName.fullyQualifiedName(), moduleElement);
      JavaWriter writer = new JavaWriter(sourceFile.openWriter());

      writer.emitPackage(
          Elements2.getPackage(moduleElement).getQualifiedName().toString());

      writer.emitImports(Provider.class.getName(), Factory.class.getName());

      String factoriesSimpleName = moduleElement.getSimpleName() + "$$Factories";
      writer.beginType(factoriesSimpleName, "class", EnumSet.of(PUBLIC, FINAL));

      writer.beginMethod(null, factoriesSimpleName, EnumSet.of(PRIVATE)).endMethod();

      writer.beginType(
          "AbstractFactory<T>", "class", EnumSet.of(PRIVATE, STATIC, ABSTRACT), null, "Factory<T>");
      writer.emitField(moduleName.simpleName(), "module", EnumSet.of(FINAL));

      writer.beginMethod(
          "", "AbstractFactory", EnumSet.noneOf(Modifier.class), moduleName.fullyQualifiedName(),
          "module")
              .emitStatement("assert module != null")
              .emitStatement("this.module = module")
              .endMethod();
      writer.endType();



      for (Binding binding : providesBindings) {
        writer.emitEmptyLine();
        writeFactory(writer, moduleName, binding);
        bindings.add(binding);
      }

      writer.endType();
      writer.close();
      return bindings.build();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String writeFactory(JavaWriter writer, ClassName moduleName, Binding binding)
      throws IOException {
    // TODO(gak): get rid of moduleName and use the element in the binding instead

    String factoryName = CaseFormat.LOWER_CAMEL.to(
        CaseFormat.UPPER_CAMEL, binding.bindingElement().getSimpleName().toString());
    String providedType = Util.typeToString(binding.providedKey().type());
    writer.beginType(factoryName, "class", EnumSet.of(PUBLIC, STATIC, FINAL),
        "AbstractFactory<" + providedType + ">");

    for (Key requiredKey : binding.requiredKeys()) {
      writer.emitField(Util.typeToString(requiredKey.providerType(elements, types)),
          requiredKey.suggestedIdentifier(), EnumSet.of(PRIVATE, FINAL));
    }

    ImmutableList.Builder<String> constructorParameters = new ImmutableList.Builder<String>()
        .add(moduleName.fullyQualifiedName()).add("module");
    for (Key requiredKey : binding.requiredKeys()) {
      constructorParameters.add(Util.typeToString(requiredKey.providerType(elements, types)))
          .add(requiredKey.suggestedIdentifier());
    }

    writer.beginMethod(null, factoryName, EnumSet.of(PUBLIC), constructorParameters.build(),
        ImmutableList.<String>of());
    writer.emitStatement("super(module)");
    for (Key requiredKey : binding.requiredKeys()) {
      writer.emitStatement("assert %s != null", requiredKey.suggestedIdentifier());
      writer.emitStatement("this.%s = %s", requiredKey.suggestedIdentifier(),
          requiredKey.suggestedIdentifier());
    }
    writer.endMethod();

    ImmutableList.Builder<String> providerInvocations = ImmutableList.builder();
    for (Key requiredKey : binding.requiredKeys()) {
      providerInvocations.add(requiredKey.suggestedIdentifier() + ".get()");
    }

    writer.emitAnnotation(Override.class);
    writer.beginMethod(providedType, "get", EnumSet.of(PUBLIC));

    writer.emitStatement("return module.%s(%s)", binding.bindingElement().getSimpleName(),
        Joiner.on(", ").join(providerInvocations.build()));

    writer.endMethod();

    writer.endType();

    return factoryName;
  }
}
