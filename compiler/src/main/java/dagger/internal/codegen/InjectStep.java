package dagger.internal.codegen;

import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.type.TypeKind.DECLARED;

import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor6;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import com.squareup.javawriter.JavaWriter;

import dagger.Factory;

public class InjectStep implements ProcessingStep {
  private Filer filer;
  private Messager messager;
  private Types types;
  private Elements elements;
  private BindingRepository factoryRepository;
  private ProviderTyper providerTyper;
  private final KeyFactory keyFactory;
  private final BindingFactory bindingFactory;

  InjectStep(ProcessingEnvironment processingEnv, BindingRepository factoryRepository) {
    this.filer = processingEnv.getFiler();
    this.messager = processingEnv.getMessager();
    this.types = processingEnv.getTypeUtils();
    this.elements = processingEnv.getElementUtils();
    this.factoryRepository = factoryRepository;
    this.providerTyper = new ProviderTyper(types, elements);
    this.keyFactory = new KeyFactory(types, elements);
    this.bindingFactory = new BindingFactory(keyFactory);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    SetMultimap<Key, ExecutableElement> injectConstructors = HashMultimap.create();
    for (Element injectElement : roundEnv.getElementsAnnotatedWith(Inject.class)) {
      // first, deal with constructors
      injectElement.accept(
          new ElementKindVisitor6<Void, SetMultimap<Key, ExecutableElement>>() {
            @Override
            public Void visitExecutableAsConstructor(ExecutableElement e,
                SetMultimap<Key, ExecutableElement> injectConstructors) {
              TypeElement enclosingElement = Elements2.asTypeElement(e.getEnclosingElement());
              if (enclosingElement.getModifiers().contains(ABSTRACT)) {
                messager.printMessage(Kind.ERROR,
                    "@Inject is nonsense on the constructor of an abstract class");
              } else {
                injectConstructors.put(keyFactory.forInjectConstructor(e), e);
              }
              return null;
            }
          }, injectConstructors);

      // now deal with member injection
      // TODO(gak)
      injectElement.accept(new ElementKindVisitor6<Void, Void>() {
        @Override
        public Void visitVariableAsField(VariableElement e, Void p) {
          messager.printMessage(Kind.ERROR, "Field injection is not currently supported", e);
          return null;
        }

        @Override
        public Void visitExecutableAsMethod(ExecutableElement e, Void p) {
          messager.printMessage(Kind.ERROR, "Method injection is not currently supported", e);
          return null;
        }
      }, null);
    }

    for (Entry<Key, Collection<ExecutableElement>> entry
        : injectConstructors.asMap().entrySet()) {
      Collection<ExecutableElement> constructors = entry.getValue();
      if (constructors.size() > 1) {
        for (ExecutableElement constructor : constructors) {
          messager.printMessage(Kind.ERROR, "Cannot have more than one @Inject constructor",
              constructor);
        }
      } else {
        Binding binding =
            bindingFactory.forInjectConstructor(Iterables.getOnlyElement(constructors));
        try {
          writeFactory(binding);
          factoryRepository.registerBinding(binding);
        } catch (IOException e) {
          messager.printMessage(Kind.ERROR, e.getMessage());
        }
      }
    }
    return false;
  }

  private void writeFactory(Binding binding) throws IOException {
    TypeMirror type = binding.providedKey().type();
    assert type.getKind() == DECLARED;
    TypeElement constructedTypeElement = Elements2.asTypeElement(((DeclaredType) type).asElement());
    ClassName constructedTypeName = ClassName.forTypeElement(constructedTypeElement);
    ClassName factoryName = Factories.factoryNameForType(constructedTypeName);

    JavaFileObject sourceFile =
        filer.createSourceFile(factoryName.fullyQualifiedName(), constructedTypeElement);
    JavaWriter writer = new JavaWriter(sourceFile.openWriter());
    try {
      writer.emitPackage(
          Elements2.getPackage(constructedTypeElement).getQualifiedName().toString());

      writer.emitImports(Factory.class.getName());
      writer.emitImports(Provider.class.getName());

      writer.beginType(factoryName.simpleName(), "class",
          EnumSet.of(PUBLIC, FINAL), null,
          JavaWriter.type(Factory.class, constructedTypeName.fullyQualifiedName()));

      for (Key requiredKey : binding.requiredKeys()) {
        writer.emitField(Util.typeToString(requiredKey.providerType(elements, types)),
            requiredKey.suggestedIdentifier(), EnumSet.of(PRIVATE, FINAL));
      }

      ImmutableList.Builder<String> constructorParameterTokens = ImmutableList.builder();
      ImmutableList.Builder<String> providerInvocations = ImmutableList.builder();
      for (KeyRequest keyRequest : binding.keysRequests()) {
        Key key = keyRequest.key();
        constructorParameterTokens.add(Util.typeToString(key.providerType(elements, types)))
            .add(key.suggestedIdentifier());
        switch (keyRequest.type()) {
          case INSTANCE:
            providerInvocations.add(key.suggestedIdentifier() + ".get()");
            break;
          case PROVIDER:
            providerInvocations.add(key.suggestedIdentifier());
            break;
          case LAZY:
            throw new UnsupportedOperationException();
          default:
            throw new AssertionError();
        }
      }

      writer.emitEmptyLine();

      writer.beginMethod(null,
          factoryName.simpleName(),
          EnumSet.of(PUBLIC), constructorParameterTokens.build(),
          ImmutableList.<String>of());

      for (Key requiredKey : binding.requiredKeys()) {
        writer.emitStatement("assert %s != null", requiredKey.suggestedIdentifier());
        writer.emitStatement("this.%s = %s", requiredKey.suggestedIdentifier(),
            requiredKey.suggestedIdentifier());
      }

      writer.endMethod();

      writer.emitEmptyLine();

      writer.emitAnnotation(Override.class);
      writer.beginMethod(constructedTypeName.simpleName(), "get", EnumSet.of(PUBLIC));
      writer.emitStatement("return new %s(%s)", constructedTypeName.simpleName(),
          Joiner.on(", ").join(providerInvocations.build()));
      writer.endMethod();

      writer.endType();
    } finally {
      writer.close();
    }
  }
}
